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
    // Penalidade de repeticao (06/09/2026, pedido do usuario): sem isso o Qwen3 4B as vezes
    // repete a mesma frase/bordao varias vezes dentro do MESMO boletim (ex.: "a gente tem que"
    // abrindo 4 das 6 falas) - problema diferente do eco do exemplo fixo do prompt (ja corrigido
    // reescrevendo o exemplo), esse e o modelo se auto-repetindo durante a propria geracao.
    // penalty_last_n=64 cobre o "raio" tipico onde essas frases se repetem; penalty_repeat=1.15
    // e moderado de proposito - alto demais penalizaria os tokens ESTRUTURAIS do JSON que
    // realmente precisam se repetir a cada fala ("speaker", "text", chaves, aspas), quebrando o
    // parseGeneratedLines(). Ordem (top-k/top-p antes de penalties) segue a recomendacao do
    // proprio header do llama.cpp - custa menos achar repeticao num vocabulario ja reduzido.
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.15f, 0.0f, 0.0f));
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

// Cache de prefixo (06/09/2026, pedido do usuario: "quanto tempo demora pra ele so ler a
// materia" - medido em campo em 232s de prefill por boletim). O prompt do redator local tem
// duas partes: um bloco FIXO (system instructions + exemplo few-shot do ADR-002, sempre o
// mesmo texto) e um bloco VARIAVEL (a materia de cada boletim). generateNative acima reprocessa
// o bloco fixo inteiro do zero em TODO boletim, mesmo sem mudar nada nele. As duas funcoes
// abaixo usam llama_state_save_file/llama_state_load_file (cache de KV do llama.cpp) pra
// decodificar o bloco fixo uma unica vez e reaproveitar em cada boletim - so o bloco variavel
// (bem menor) precisa de prefill de verdade a partir dai.
//
// Cuidado de seguranca: generateNative permanece INTOCADA acima, como rede de seguranca - se
// esse cache falhar por qualquer motivo, generateCachedNative cai sozinha pro mesmo caminho
// frio (decodifica o prompt inteiro) que generateNative sempre fez.
namespace {
constexpr size_t PREFIX_CACHE_TOKEN_CAPACITY = 2048;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_pailer_localtune_data_LocalLlamaTextGenerator_ensurePrefixCacheNative(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring cache_path,
    jstring prefix_prompt,
    jint threads
) {
    std::call_once(backend_once, init_backend_once);

    const std::string model_path_str = jstring_to_utf8(env, model_path);
    const std::string cache_path_str = jstring_to_utf8(env, cache_path);
    const std::string prefix_str = jstring_to_utf8(env, prefix_prompt);
    if (model_path_str.empty() || cache_path_str.empty() || prefix_str.empty()) return JNI_FALSE;

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    llama_model * model = llama_model_load_from_file(model_path_str.c_str(), model_params);
    if (model == nullptr) return JNI_FALSE;

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int n_prefix = -llama_tokenize(vocab, prefix_str.c_str(), static_cast<int32_t>(prefix_str.size()), nullptr, 0, true, true);
    if (n_prefix <= 0) {
        llama_model_free(model);
        return JNI_FALSE;
    }
    std::vector<llama_token> prefix_tokens(static_cast<size_t>(n_prefix));
    if (llama_tokenize(vocab, prefix_str.c_str(), static_cast<int32_t>(prefix_str.size()), prefix_tokens.data(), n_prefix, true, true) < 0) {
        llama_model_free(model);
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::min(n_prefix + 32, 2048));
    ctx_params.n_batch = static_cast<uint32_t>(std::min(n_prefix, 2048));
    ctx_params.n_ubatch = 128;
    ctx_params.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        return JNI_FALSE;
    }
    const int32_t safe_threads = std::max(1, std::min(static_cast<int32_t>(threads), 8));
    llama_set_n_threads(ctx, safe_threads, safe_threads);

    // Confere se ja existe cache valido pra esse EXATO bloco fixo (mesmos tokens, na mesma
    // ordem) antes de gastar tempo reconstruindo - so muda quando o texto de instrucao muda
    // (atualizacao do app), o que e raro.
    std::vector<llama_token> cached_tokens(PREFIX_CACHE_TOKEN_CAPACITY);
    size_t n_cached = 0;
    const bool loaded = llama_state_load_file(ctx, cache_path_str.c_str(), cached_tokens.data(), cached_tokens.size(), &n_cached);
    const bool already_valid = loaded && n_cached == static_cast<size_t>(n_prefix)
        && std::equal(cached_tokens.begin(), cached_tokens.begin() + static_cast<long>(n_cached), prefix_tokens.begin());
    if (already_valid) {
        __android_log_print(ANDROID_LOG_INFO, "PailerLlama", "perf: cache de prefixo ja valido (%d tokens)", n_prefix);
        llama_free(ctx);
        llama_model_free(model);
        return JNI_TRUE;
    }

    // Cache ausente ou desatualizado - limpa o que o load acima possa ter deixado no contexto
    // (tokens de uma versao antiga do prefixo) e reconstroi do zero.
    llama_memory_clear(llama_get_memory(ctx), true);
    const auto t_build_start = std::chrono::steady_clock::now();
    llama_batch batch = llama_batch_get_one(prefix_tokens.data(), n_prefix);
    const bool decode_ok = llama_decode(ctx, batch) == 0;
    const auto t_build_end = std::chrono::steady_clock::now();
    const long build_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_build_end - t_build_start).count();

    bool saved = false;
    if (decode_ok) {
        saved = llama_state_save_file(ctx, cache_path_str.c_str(), prefix_tokens.data(), static_cast<size_t>(n_prefix)) != 0;
    }
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama",
        "perf: cache de prefixo reconstruido (%d tokens) em %ldms, decode_ok=%d salvou=%d",
        n_prefix, build_ms, decode_ok, saved);

    llama_free(ctx);
    llama_model_free(model);
    return saved ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pailer_localtune_data_LocalLlamaTextGenerator_generateCachedNative(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring cache_path,
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
    const std::string cache_path_str = jstring_to_utf8(env, cache_path);
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
    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()), prompt_tokens.data(), n_prompt, true, true) < 0) {
        llama_model_free(model);
        return error_result(env, "Não consegui preparar o pedido.");
    }

    const int32_t predict = std::max(16, std::min(static_cast<int32_t>(max_tokens), 420));
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::min(n_prompt + predict + 32, 2048));
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

    const int32_t safe_threads = std::max(1, std::min(static_cast<int32_t>(threads), 8));
    llama_set_n_threads(ctx, safe_threads, safe_threads);
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama", "perf: threads pedidos=%d usados=%d", threads, safe_threads);

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    // Penalidade de repeticao (06/09/2026, pedido do usuario): sem isso o Qwen3 4B as vezes
    // repete a mesma frase/bordao varias vezes dentro do MESMO boletim (ex.: "a gente tem que"
    // abrindo 4 das 6 falas) - problema diferente do eco do exemplo fixo do prompt (ja corrigido
    // reescrevendo o exemplo), esse e o modelo se auto-repetindo durante a propria geracao.
    // penalty_last_n=64 cobre o "raio" tipico onde essas frases se repetem; penalty_repeat=1.15
    // e moderado de proposito - alto demais penalizaria os tokens ESTRUTURAIS do JSON que
    // realmente precisam se repetir a cada fala ("speaker", "text", chaves, aspas), quebrando o
    // parseGeneratedLines(). Ordem (top-k/top-p antes de penalties) segue a recomendacao do
    // proprio header do llama.cpp - custa menos achar repeticao num vocabulario ja reduzido.
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.15f, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::max(0.1f, std::min(temperature, 1.2f))));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    if (expired()) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        llama_model_free(model);
        return error_result(env, "Redator local passou do tempo antes de escrever.");
    }

    // Tenta reaproveitar o cache de prefixo (ver ensurePrefixCacheNative acima): so conta como
    // "cache hit" se os tokens salvos forem um prefixo EXATO do prompt atual - qualquer
    // divergencia (prefixo mudou, cache de outro pacote/modelo) descarta o cache inteiro e cai
    // pro prefill normal, sem arriscar decodificar em cima de um estado errado.
    std::vector<llama_token> cached_tokens(PREFIX_CACHE_TOKEN_CAPACITY);
    size_t n_cached = 0;
    const bool cache_present = !cache_path_str.empty()
        && llama_state_load_file(ctx, cache_path_str.c_str(), cached_tokens.data(), cached_tokens.size(), &n_cached);
    const bool cache_hit = cache_present && n_cached > 0 && n_cached < static_cast<size_t>(n_prompt)
        && std::equal(cached_tokens.begin(), cached_tokens.begin() + static_cast<long>(n_cached), prompt_tokens.begin());

    const auto t_prefill_start = std::chrono::steady_clock::now();
    llama_batch batch;
    if (cache_hit) {
        batch = llama_batch_get_one(prompt_tokens.data() + n_cached, n_prompt - static_cast<int>(n_cached));
    } else {
        if (cache_present) {
            // Cache existia mas nao bateu com o prompt atual - limpa antes de decodificar tudo
            // de novo, senao o llama_decode continuaria em cima do estado errado.
            llama_memory_clear(llama_get_memory(ctx), true);
        }
        batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    }
    const int decode_result = llama_decode(ctx, batch);
    if (decode_result != 0) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        llama_model_free(model);
        return error_result(env, "O modelo falhou ao ler o pedido.");
    }
    const auto t_prefill_end = std::chrono::steady_clock::now();
    const long prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_prefill_end - t_prefill_start).count();
    const int32_t prefill_new_tokens = cache_hit ? (n_prompt - static_cast<int32_t>(n_cached)) : n_prompt;
    const double prefill_tok_s = prefill_ms > 0 ? (1000.0 * prefill_new_tokens / prefill_ms) : 0.0;
    __android_log_print(ANDROID_LOG_INFO, "PailerLlama",
        "perf: prefill em %ldms (%.1f tok/s, cache_hit=%d, reaproveitados=%zu de %d)",
        prefill_ms, prefill_tok_s, cache_hit, n_cached, n_prompt);

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
