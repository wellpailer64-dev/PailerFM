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
    jint timeout_ms
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

    llama_model * model = llama_model_load_from_file(model_path_str.c_str(), model_params);
    if (model == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, "PailerLlama", "failed to load model: %s", model_path_str.c_str());
        return error_result(env, "Não consegui carregar o modelo local.");
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

    const int32_t predict = std::max(16, std::min(static_cast<int32_t>(max_tokens), 260));
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

    const int32_t safe_threads = std::max(1, std::min(static_cast<int32_t>(threads), 4));
    llama_set_n_threads(ctx, safe_threads, safe_threads);

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

    if (llama_decode(ctx, batch) != 0) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        llama_model_free(model);
        return error_result(env, "O modelo falhou ao ler o pedido.");
    }

    std::string output;
    llama_token token = LLAMA_TOKEN_NULL;
    for (int32_t i = 0; i < predict; ++i) {
        if (expired()) {
            __android_log_print(ANDROID_LOG_WARN, "PailerLlama", "generation timed out after %d tokens", i);
            output.clear();
            break;
        }
        token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char piece[256];
        const int n_piece = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n_piece > 0) output.append(piece, static_cast<size_t>(n_piece));
        if (output.find("}]") != std::string::npos || output.find("}\n]") != std::string::npos) break;

        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) break;
    }

    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);

    if (output.empty()) return error_result(env, "Redator local demorou demais.");
    return env->NewStringUTF(output.c_str());
}
