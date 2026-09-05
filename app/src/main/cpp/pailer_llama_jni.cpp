#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {
std::once_flag backend_once;

void forward_ggml_log(ggml_log_level level, const char * text, void *) {
    const int android_level = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
        : level == GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN
        : level == GGML_LOG_LEVEL_DEBUG ? ANDROID_LOG_DEBUG
        : ANDROID_LOG_INFO;
    __android_log_print(android_level, "PailerLlama", "%s", text);
}

void init_backend_once() {
    llama_log_set(forward_ggml_log, nullptr);
    llama_backend_init();
}

struct DeadlineAbort {
    std::chrono::steady_clock::time_point deadline;
};

bool abort_when_expired(void * data) {
    auto * abort_data = static_cast<DeadlineAbort *>(data);
    return abort_data != nullptr && std::chrono::steady_clock::now() >= abort_data->deadline;
}

std::string jstring_to_utf8(JNIEnv * env, jstring value) {
    if (value == nullptr) return "";
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring error_result(JNIEnv * env, const std::string & message) {
    return env->NewStringUTF(("ERROR:" + message).c_str());
}
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pailer_localtune_data_LocalLlamaTextGenerator_generateNative(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jint threads,
    jint timeout_ms,
    jobject progress_listener
) {
    std::call_once(backend_once, init_backend_once);
    const auto started_at = std::chrono::steady_clock::now();
    const auto deadline = started_at + std::chrono::milliseconds(std::max(5000, static_cast<int>(timeout_ms)));

    DeadlineAbort abort_data { deadline };
    auto expired = [&abort_data]() {
        return abort_when_expired(&abort_data);
    };

    const std::string model_path_str = jstring_to_utf8(env, model_path);
    const std::string prompt_str = jstring_to_utf8(env, prompt);
    if (model_path_str.empty()) return error_result(env, "Modelo não informado.");
    if (prompt_str.empty()) return error_result(env, "Prompt vazio.");

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;

    const auto t_load_start = std::chrono::steady_clock::now();
    llama_model * model = llama_model_load_from_file(model_path_str.c_str(), model_params);
    if (model == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, "PailerLlama", "failed to load model: %s", model_path_str.c_str());
        return error_result(env, "Não consegui carregar o modelo local.");
    }
    const auto t_load_end = std::chrono::steady_clock::now();
    const long load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_load_end - t_load_start).count();
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama", "perf: modelo carregado em %ldms", load_ms);

    // Callback pra UI acompanhar fase (lendo/escrevendo) e tokens gerados em tempo real, com
    // segundos decorridos - pedido do usuario (03/09/2026): "precisa emitir em quantos segundos
    // cada etapa foi realizada" pra acompanhar carregando/lendo/escrevendo separado. Metodos
    // Kotlin: onPhase(phase: String, elapsedMs: Long), onProgress(current: Int, max: Int, elapsedMs: Long).
    jclass listener_class = progress_listener != nullptr ? env->GetObjectClass(progress_listener) : nullptr;
    jmethodID on_phase_mid = listener_class != nullptr
        ? env->GetMethodID(listener_class, "onPhase", "(Ljava/lang/String;J)V")
        : nullptr;
    jmethodID on_progress_mid = listener_class != nullptr
        ? env->GetMethodID(listener_class, "onProgress", "(IIJ)V")
        : nullptr;
    if (on_phase_mid != nullptr) {
        jstring phase = env->NewStringUTF("lendo");
        env->CallVoidMethod(progress_listener, on_phase_mid, phase, static_cast<jlong>(load_ms));
        env->DeleteLocalRef(phase);
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int n_prompt = -llama_tokenize(vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        llama_model_free(model);
        return error_result(env, "Não consegui tokenizar o pedido.");
    }
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama", "perf: prompt tokenizado, n_prompt=%d", n_prompt);

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()), prompt_tokens.data(), n_prompt, true, true) < 0) {
        llama_model_free(model);
        return error_result(env, "Não consegui preparar o pedido.");
    }

    // Teto acompanha o range liberado do lado Kotlin (RadioWriterPackageRepository.
    // maxTokens.coerceIn(32,420)) - era fixo em 260, abaixo do 420 que o manifest do Qwen3 4B
    // pede, e podia truncar falas antes do JSON fechar (achado 03/09/2026, chegou a 215/260 numa
    // rodada real de teste).
    const int32_t predict = std::max(16, std::min(static_cast<int32_t>(max_tokens), 420));
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::min(n_prompt + predict + 32, 2048));
    // n_batch precisa caber o prompt inteiro - decode() manda todos os n_prompt tokens de uma vez
    // via llama_batch_get_one(). Um cap fixo abaixo de n_prompt (era 512) dispara
    // GGML_ASSERT(n_tokens_all <= cparams.n_batch) dentro do llama_decode e mata o processo
    // inteiro com SIGABRT - visto em campo em 02/09/2026 quando o prompt cresceu (ver ADR-002).
    ctx_params.n_batch = static_cast<uint32_t>(std::min(n_prompt, 2048));
    ctx_params.n_ubatch = 128;
    ctx_params.no_perf = true;
    ctx_params.abort_callback = abort_when_expired;
    ctx_params.abort_callback_data = &abort_data;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        return error_result(env, "Não consegui criar o contexto do modelo.");
    }

    // Teto acompanha o range liberado do lado Kotlin (RadioWriterPackageRepository.coerceIn(1,8)) -
    // era min(...,4) e silenciosamente ignorava threads=5/6 do manifest, então "aumentamos pra 5
    // threads" nunca tinha efeito real no decode (achado 03/09/2026 diagnosticando lentidão do Qwen3 4B).
    // Subido pra 8 (03/09/2026, 2a vez): nucleos totais do Dimensity 1200 (`adb shell nproc`),
    // reteste pedido pelo usuario com o maximo fisico do aparelho.
    const int32_t safe_threads = std::max(1, std::min(static_cast<int32_t>(threads), 8));
    llama_set_n_threads(ctx, safe_threads, safe_threads);
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama", "perf: threads pedidos=%d usados=%d", threads, safe_threads);

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::max(0.1f, std::min(temperature, 1.2f))));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    if (expired()) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        llama_model_free(model);
        return error_result(env, "Redator local passou do tempo antes de escrever.");
    }

    const auto t_prefill_start = std::chrono::steady_clock::now();
    if (llama_decode(ctx, batch) != 0) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        llama_model_free(model);
        return error_result(env, "O modelo falhou ao ler o pedido.");
    }
    const auto t_prefill_end = std::chrono::steady_clock::now();
    const long prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_prefill_end - t_prefill_start).count();
    const double prefill_tok_s = prefill_ms > 0 ? (1000.0 * n_prompt / prefill_ms) : 0.0;
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama", "perf: prefill em %ldms (%.1f tok/s)", prefill_ms, prefill_tok_s);

    if (on_phase_mid != nullptr) {
        jstring phase = env->NewStringUTF("escrevendo");
        env->CallVoidMethod(progress_listener, on_phase_mid, phase, static_cast<jlong>(prefill_ms));
        env->DeleteLocalRef(phase);
    }

    std::string output;
    llama_token token = LLAMA_TOKEN_NULL;
    const auto t_decode_start = std::chrono::steady_clock::now();
    auto t_last_log = t_decode_start;
    int32_t generated = 0;
    const char * stop_reason = "predict_cap";
    for (int32_t i = 0; i < predict; ++i) {
        if (expired()) {
            __android_log_print(ANDROID_LOG_WARN, "PailerLlama", "generation timed out after %d tokens", i);
            output.clear();
            stop_reason = "timeout";
            break;
        }
        token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) { stop_reason = "eog"; break; }

        char piece[256];
        const int n_piece = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n_piece > 0) output.append(piece, static_cast<size_t>(n_piece));
        ++generated;
        const auto now = std::chrono::steady_clock::now();
        const long generated_elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - t_decode_start).count();
        if (on_progress_mid != nullptr) {
            env->CallVoidMethod(progress_listener, on_progress_mid, generated, predict, static_cast<jlong>(generated_elapsed_ms));
        }
        if (output.find("}]") != std::string::npos || output.find("}\n]") != std::string::npos) { stop_reason = "json_closed"; break; }

        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - t_last_log).count() >= 3000) {
            const long elapsed_ms = generated_elapsed_ms;
            const double tok_s = elapsed_ms > 0 ? (1000.0 * generated / elapsed_ms) : 0.0;
            __android_log_print(ANDROID_LOG_INFO, "PailerLlama",
                "perf: gerando %d/%d tokens, %ldms decorridos, %.1f tok/s", generated, predict, elapsed_ms, tok_s);
            // Texto parcial (ultimos ~120 caracteres) - visibilidade em tempo real de QUANDO o
            // texto comeca a se formar, pedido do usuario (03/09/2026) monitorando lentidao do
            // Qwen3 4B. So a cauda pra nao inundar o logcat a cada 3s com o output inteiro.
            const size_t tail_start = output.size() > 120 ? output.size() - 120 : 0;
            __android_log_print(ANDROID_LOG_INFO, "PailerLlama",
                "perf: texto parcial: ...%s", output.c_str() + tail_start);
            t_last_log = now;
        }

        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) { stop_reason = "decode_error"; break; }
    }
    const auto t_decode_end = std::chrono::steady_clock::now();
    const long decode_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_decode_end - t_decode_start).count();
    const double decode_tok_s = decode_ms > 0 ? (1000.0 * generated / decode_ms) : 0.0;
    const long total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_decode_end - t_load_start).count();
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama",
        "perf: fim motivo=%s tokens=%d decode=%ldms (%.1f tok/s) total=%ldms", stop_reason, generated, decode_ms, decode_tok_s, total_ms);

    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);

    if (output.empty()) return error_result(env, "Redator local demorou demais.");
    return env->NewStringUTF(output.c_str());
}
