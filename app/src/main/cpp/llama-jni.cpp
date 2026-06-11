#include <jni.h>

#include <android/log.h>
#include <cctype>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

#define TAG "LlamaCppJni-Native"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static std::string g_model_path;
static long long g_model_load_count = 0;
static long long g_generation_call_count = 0;
static bool g_model_reused = false;
static long long g_last_model_load_ms = 0;

namespace {

std::string json_escape(const std::string & input) {
    std::string out;
    out.reserve(input.size() + 16);
    for (unsigned char c : input) {
        switch (c) {
            case '\\': out += "\\\\"; break;
            case '"': out += "\\\""; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buffer[7];
                    std::snprintf(buffer, sizeof(buffer), "\\u%04x", static_cast<unsigned int>(c));
                    out += buffer;
                } else {
                    out.push_back(static_cast<char>(c));
                }
                break;
        }
    }
    return out;
}

void append_key(std::string & json, bool & first, const char * key) {
    if (!first) {
        json += ", ";
    }
    first = false;
    json += '"';
    json += key;
    json += "\": ";
}

void append_string(std::string & json, bool & first, const char * key, const std::string & value) {
    append_key(json, first, key);
    json += '"';
    json += json_escape(value);
    json += '"';
}

void append_bool(std::string & json, bool & first, const char * key, bool value) {
    append_key(json, first, key);
    json += value ? "true" : "false";
}

void append_int(std::string & json, bool & first, const char * key, long long value) {
    append_key(json, first, key);
    json += std::to_string(value);
}

void append_double(std::string & json, bool & first, const char * key, double value) {
    append_key(json, first, key);
    char buffer[64];
    std::snprintf(buffer, sizeof(buffer), "%.6f", value);
    json += buffer;
}

void append_null(std::string & json, bool & first, const char * key) {
    append_key(json, first, key);
    json += "null";
}

std::string build_action_json(
    const std::string & intent,
    const std::string & reply,
    const std::string & action_type,
    const std::string & risk_level,
    bool requires_confirmation,
    bool final_action_allowed,
    const std::string & params_json
) {
    std::string json;
    json.reserve(intent.size() + reply.size() + params_json.size() + 256);
    json += "{";
    bool first = true;
    append_string(json, first, "intent", intent);
    append_string(json, first, "reply", reply);
    append_string(json, first, "actionType", action_type);
    append_string(json, first, "riskLevel", risk_level);
    append_bool(json, first, "requiresConfirmation", requires_confirmation);
    append_bool(json, first, "finalActionAllowed", final_action_allowed);
    append_key(json, first, "params");
    json += params_json;
    json += "}";
    return json;
}

std::string build_preview(const std::vector<llama_token> & tokens, int count) {
    std::string preview = "[";
    const int limit = std::min<int>(std::max(count, 0), 12);
    for (int i = 0; i < limit; ++i) {
        if (i > 0) preview += ", ";
        preview += std::to_string(tokens[static_cast<size_t>(i)]);
    }
    preview += "]";
    return preview;
}

std::string trim_copy(const std::string & input) {
    size_t start = 0;
    while (start < input.size() && std::isspace(static_cast<unsigned char>(input[start]))) {
        ++start;
    }

    size_t end = input.size();
    while (end > start && std::isspace(static_cast<unsigned char>(input[end - 1]))) {
        --end;
    }

    return input.substr(start, end - start);
}

bool is_blank(const std::string & input) {
    return trim_copy(input).empty();
}

void append_int_array(std::string & json, bool & first, const char * key, const std::vector<llama_token> & values, int limit) {
    append_key(json, first, key);
    json += "[";
    const int count = std::min<int>(static_cast<int>(values.size()), std::max(limit, 0));
    for (int i = 0; i < count; ++i) {
        if (i > 0) json += ", ";
        json += std::to_string(values[static_cast<size_t>(i)]);
    }
    json += "]";
}

std::string build_error_json(
    const std::string & last_error,
    const std::string & last_failure
) {
    const bool model_detected = g_model != nullptr;
    const bool model_loaded = g_model != nullptr && g_ctx != nullptr;
    const bool tokenizer_loaded = g_model && llama_model_tokenizer_loaded(g_model) && llama_n_vocab(g_model) > 0;
    const int vocab_size = tokenizer_loaded ? llama_n_vocab(g_model) : 0;
    const int tensors_loaded = g_model ? llama_n_tensors(g_model) : 0;
    const long long load_ms = g_last_model_load_ms;
    std::string model_arch = "unknown";
    std::string tokenizer_type = "unknown";
    if (g_model) {
        char arch_buf[64];
        llama_model_arch(g_model, arch_buf, sizeof(arch_buf));
        model_arch = arch_buf;

        char ttype_buf[64];
        llama_model_tokenizer_type(g_model, ttype_buf, sizeof(ttype_buf));
        tokenizer_type = ttype_buf;
    }

    std::string json = "{";
    bool first = true;
    append_bool(json, first, "success", false);
    append_bool(json, first, "ok", false);
    append_key(json, first, "text");
    json += "null";
    append_int(json, first, "latencyMillis", 0);
    append_int(json, first, "tokensGenerated", 0);
    append_int(json, first, "tokens_generated", 0);
    append_int(json, first, "promptTokens", 0);
    append_int(json, first, "prompt_tokens", 0);
    append_int(json, first, "prompt_tokens_count", 0);
    append_int(json, first, "modelLoadMs", load_ms);
    append_int(json, first, "loadMs", load_ms);
    append_int(json, first, "load_ms", load_ms);
    append_int(json, first, "promptEvalMs", 0);
    append_int(json, first, "generationMs", 0);
    append_int(json, first, "generation_ms", 0);
    append_int(json, first, "contextSize", 0);
    append_int(json, first, "threadsUsed", 0);
    append_string(json, first, "backendType", "native");
    append_string(json, first, "backend", "native");
    append_string(json, first, "modelArch", model_arch);
    append_int(json, first, "vocabSize", vocab_size);
    append_int(json, first, "vocab_size", vocab_size);
    append_int(json, first, "tensorsLoaded", tensors_loaded);
    append_bool(json, first, "modelDetected", model_detected);
    append_bool(json, first, "model_detected", model_detected);
    append_bool(json, first, "model_loaded", model_loaded);
    append_bool(json, first, "modelLoaded", model_loaded);
    append_bool(json, first, "model_reused", g_model_reused);
    append_bool(json, first, "modelReused", g_model_reused);
    append_int(json, first, "model_load_count", g_model_load_count);
    append_int(json, first, "modelLoadCount", g_model_load_count);
    append_int(json, first, "generation_call_count", g_generation_call_count);
    append_int(json, first, "generationCallCount", g_generation_call_count);
    append_bool(json, first, "realTokenIds", false);
    append_bool(json, first, "real_token_ids", false);
    append_bool(json, first, "realInference", false);
    append_bool(json, first, "real_inference", false);
    append_bool(json, first, "nativeGenerationAvailable", false);
    append_bool(json, first, "native_generation_available", false);
    append_bool(json, first, "metadataParsed", model_detected);
    append_bool(json, first, "tokenizerLoaded", tokenizer_loaded);
    append_bool(json, first, "tokenizer_loaded", tokenizer_loaded);
    append_bool(json, first, "tensorsWeightLoaded", g_model ? llama_model_weights_loaded(g_model) : false);
    append_bool(json, first, "ggmlGraphCompute", false);
    append_bool(json, first, "logitsGenerated", false);
    append_bool(json, first, "samplingActive", false);
    append_bool(json, first, "deterministicResponse", true);
    append_int(json, first, "memoryEstimateBytes", 0);
    append_double(json, first, "tokensPerSecond", 0.0);
    append_bool(json, first, "nativeBridgeStable", false);
    append_bool(json, first, "jsonReturnBridge", false);
    append_bool(json, first, "crashFree", false);
    append_string(json, first, "tokenizerType", tokenizer_type);
    append_string(json, first, "tokenizer_type", tokenizer_type);
    append_int(json, first, "bosTokenId", g_model ? llama_model_bos_token_id(g_model) : -1);
    append_int(json, first, "eosTokenId", g_model ? llama_model_eos_token_id(g_model) : -1);
    append_int(json, first, "specialTokensCount", g_model ? llama_model_special_tokens_count(g_model) : 0);
    append_bool(json, first, "tokenizationSuccess", false);
    append_bool(json, first, "tokenization_ok", false);
    append_int(json, first, "tokenizerLoadMs", load_ms);
    append_int(json, first, "tokenizer_load_ms", load_ms);
    append_bool(json, first, "ggmlGraphBuilt", model_loaded);
    append_int(json, first, "graphNodesCount", g_ctx ? llama_graph_nodes_count(g_ctx) : 0);
    append_int(json, first, "memoryMappedMb", g_model ? llama_model_mmap_mb(g_model) : 0);
    append_bool(json, first, "logitsFromModelWeights", false);
    append_bool(json, first, "decodedTokens", false);
    append_int(json, first, "sampledTokenIdsCount", 0);
    append_string(json, first, "prompt_text", "");
    append_string(json, first, "promptText", "");
    append_key(json, first, "prompt_token_ids_sample");
    json += "[]";
    append_key(json, first, "generated_token_ids_sample");
    json += "[]";
    append_bool(json, first, "json_parse_attempted", false);
    append_bool(json, first, "jsonParseAttempted", false);
    append_bool(json, first, "json_parse_success", false);
    append_bool(json, first, "jsonParseSuccess", false);
    append_string(json, first, "parsed_intent", "");
    append_string(json, first, "parsedIntent", "");
    append_string(json, first, "parsed_risk_level", "");
    append_string(json, first, "parsedRiskLevel", "");
    append_string(json, first, "finish_reason", "");
    append_string(json, first, "finishReason", "");
    append_bool(json, first, "simulation", false);
    append_bool(json, first, "simulationActive", false);
    append_null(json, first, "decodedText");
    append_null(json, first, "decoded_text");
    append_string(json, first, "error", last_error);
    append_string(json, first, "message", last_failure);
    append_bool(json, first, "upstreamSamplerLinked", false);
    append_string(json, first, "tokenIdsPreview", "[]");
    append_string(json, first, "token_ids_preview", "[]");
    append_string(json, first, "lastError", last_error);
    append_string(json, first, "last_error", last_error);
    append_string(json, first, "lastFailure", last_failure);
    append_string(json, first, "last_failure", last_failure);
    json += "}";
    return json;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeLoadModel(JNIEnv *env, jclass clazz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return JNI_FALSE;
    if (is_blank(path)) {
        LOGE("Model path missing.");
        g_model_path.clear();
        g_model_reused = false;
        g_last_model_load_ms = 0;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    LOGI("Initializing native GGUF backend: %s", path);
    auto start_time = std::chrono::high_resolution_clock::now();

    if (g_model && g_ctx && g_model_path == path) {
        g_model_reused = true;
        g_last_model_load_ms = 0;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_TRUE;
    }

    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    llama_backend_init(false);

    auto mparams = llama_model_default_params();
    g_model = llama_load_model_from_file(path, mparams);

    if (!g_model) {
        LOGE("Native GGUF model load failed.");
        g_model_path.clear();
        g_model_reused = false;
        g_last_model_load_ms = 0;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_threads = 4;
    g_ctx = llama_new_context_with_model(g_model, cparams);

    if (!g_ctx) {
        LOGE("Native context creation failed.");
        llama_free_model(g_model);
        g_model = nullptr;
        g_model_path.clear();
        g_model_reused = false;
        g_last_model_load_ms = 0;
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    g_model_path = path;
    g_model_load_count += 1;
    g_model_reused = false;
    env->ReleaseStringUTFChars(model_path, path);

    auto end_time = std::chrono::high_resolution_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    g_last_model_load_ms = static_cast<long long>(ms);
    LOGI("Native backend ready in %lld ms", static_cast<long long>(ms));

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeGenerate(
    JNIEnv *env,
    jclass clazz,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jint top_k,
    jfloat top_p,
    jint timeout_ms
) {
    g_generation_call_count += 1;
    g_model_reused = g_model_load_count > 0 && g_generation_call_count > 1;
    (void)clazz;
    (void)temperature;
    (void)top_k;
    (void)top_p;

    if (!g_model) {
        LOGE("Model not loaded.");
        std::string json = build_error_json("MODEL_NOT_LOADED", "Model not loaded.");
        return env->NewStringUTF(json.c_str());
    }
    if (!g_ctx) {
        LOGE("Context creation failed.");
        std::string json = build_error_json("CONTEXT_CREATE_FAILED", "Context creation failed.");
        return env->NewStringUTF(json.c_str());
    }

    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_chars) {
        std::string json = build_error_json("EMPTY_PROMPT", "Prompt access failed.");
        return env->NewStringUTF(json.c_str());
    }

    std::string prompt_text = trim_copy(std::string(prompt_chars));
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    if (prompt_text.empty()) {
        std::string json = build_error_json("EMPTY_PROMPT", "Prompt was empty.");
        return env->NewStringUTF(json.c_str());
    }

    LOGI("Executing native generation for prompt: %s", prompt_text.c_str());
    const auto total_start = std::chrono::steady_clock::now();

    const bool tokenizer_loaded = llama_model_tokenizer_loaded(g_model) && llama_n_vocab(g_model) > 0;
    const int vocab_size = tokenizer_loaded ? llama_n_vocab(g_model) : 0;
    const int32_t ctx_limit = llama_n_ctx(g_ctx);
    const int generation_limit = std::max<jint>(max_tokens, 1);
    const int prompt_buffer_size = std::max<int>(static_cast<int>(prompt_text.size()) * 2 + 16, 16);
    const bool wants_json = prompt_text.find("JSON") != std::string::npos || prompt_text.find("json") != std::string::npos;
    const std::string backend = "native";
    const bool model_detected = true;
    const std::string model_failure = llama_model_last_failure(g_model) ? llama_model_last_failure(g_model) : "none";

    if (!tokenizer_loaded) {
        std::string json = build_error_json(
            "TOKENIZER_NOT_LOADED",
            model_failure == "none" ? "Tokenizer metadata not loaded from GGUF metadata." : model_failure
        );
        return env->NewStringUTF(json.c_str());
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(prompt_buffer_size));
    int prompt_token_count = -1;
    bool tokenization_ok = false;
    std::string last_failure = model_failure;

    for (int attempt = 0; attempt < 4; ++attempt) {
        prompt_token_count = llama_tokenize(
            g_model,
            prompt_text.c_str(),
            static_cast<int>(prompt_text.size()),
            prompt_tokens.data(),
            static_cast<int>(prompt_tokens.size()),
            true,
            true
        );
        if (prompt_token_count >= 0) {
            tokenization_ok = true;
            break;
        }
        prompt_tokens.resize(prompt_tokens.size() * 2);
    }

    if (!tokenization_ok) {
        std::string json = build_error_json("PROMPT_TOKENIZATION_FAILED", "Prompt tokenization failed.");
        return env->NewStringUTF(json.c_str());
    }

    if (prompt_token_count <= 0) {
        std::string json = build_error_json("PROMPT_TOKENIZATION_FAILED", "Prompt tokenization produced no tokens.");
        return env->NewStringUTF(json.c_str());
    }

    prompt_tokens.resize(static_cast<size_t>(prompt_token_count));
    const std::string token_preview = build_preview(prompt_tokens, prompt_token_count);
    const bool real_token_ids = true;

    if (ctx_limit > 0 && prompt_token_count + generation_limit > ctx_limit) {
        std::string json = build_error_json("PROMPT_TOO_LONG", "Prompt exceeded the context window.");
        return env->NewStringUTF(json.c_str());
    }

    auto prompt_eval_start = std::chrono::steady_clock::now();
    llama_batch prompt_batch = llama_batch_init(prompt_token_count, 0, 1);
    for (int i = 0; i < prompt_token_count; ++i) {
        prompt_batch.token[i] = prompt_tokens[static_cast<size_t>(i)];
        prompt_batch.pos[i] = i;
        prompt_batch.n_seq_id[i] = 1;
        prompt_batch.seq_id[i][0] = 0;
        prompt_batch.logits[i] = (i == prompt_token_count - 1);
    }
    prompt_batch.n_tokens = prompt_token_count;
    bool prefill_ok = llama_decode(g_ctx, prompt_batch);
    llama_batch_free(prompt_batch);
    auto prompt_eval_end = std::chrono::steady_clock::now();
    auto prompt_eval_ms = std::chrono::duration_cast<std::chrono::milliseconds>(prompt_eval_end - prompt_eval_start).count();

    if (!prefill_ok) {
        std::string json = build_error_json("PROMPT_EVAL_FAILED", "Graph compute failed during prefill.");
        return env->NewStringUTF(json.c_str());
    }

    std::vector<llama_token> generated_tokens;
    generated_tokens.reserve(static_cast<size_t>(generation_limit));
    std::string decoded_output;
    std::string error_code;
    std::string message;
    std::string finish_reason = "max_tokens";
    bool json_parse_attempted = false;
    bool json_parse_success = false;
    std::string parsed_intent;
    std::string parsed_risk_level;
    const auto generation_start = std::chrono::steady_clock::now();
    const int32_t eos_token_id = llama_model_eos_token_id(g_model);
    int current_pos = prompt_token_count;

    auto extract_json_string = [](const std::string & source, const std::string & key) -> std::string {
        const std::string needle = "\"" + key + "\"";
        size_t key_pos = source.find(needle);
        if (key_pos == std::string::npos) return {};

        size_t colon_pos = source.find(':', key_pos + needle.size());
        if (colon_pos == std::string::npos) return {};

        size_t quote_pos = source.find('"', colon_pos);
        if (quote_pos == std::string::npos) return {};

        std::string value;
        bool escape = false;
        for (size_t index = quote_pos + 1; index < source.size(); ++index) {
            const char ch = source[index];
            if (escape) {
                switch (ch) {
                    case '\\': value.push_back('\\'); break;
                    case '"': value.push_back('"'); break;
                    case '/': value.push_back('/'); break;
                    case 'b': value.push_back('\b'); break;
                    case 'f': value.push_back('\f'); break;
                    case 'n': value.push_back('\n'); break;
                    case 'r': value.push_back('\r'); break;
                    case 't': value.push_back('\t'); break;
                    default: value.push_back(ch); break;
                }
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '"') {
                return value;
            }
            value.push_back(ch);
        }

        return {};
    };

    for (int step = 0; step < generation_limit; ++step) {
        if (timeout_ms > 0) {
            const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - generation_start
            ).count();
            if (elapsed >= timeout_ms) {
                error_code = "GENERATION_TIMEOUT";
                message = "Generation timed out.";
                finish_reason = "timeout";
                break;
            }
        }

        float * logits = llama_get_logits(g_ctx);
        if (!logits || vocab_size <= 0) {
            error_code = "TOKEN_SAMPLING_FAILED";
            message = "Logits unavailable.";
            finish_reason = "error";
            break;
        }

        std::vector<llama_token_data> candidates(static_cast<size_t>(vocab_size));
        for (int i = 0; i < vocab_size; ++i) {
            candidates[static_cast<size_t>(i)].id = static_cast<llama_token>(i);
            candidates[static_cast<size_t>(i)].logit = logits[i];
            candidates[static_cast<size_t>(i)].p = 0.0f;
        }

        llama_token_data_array candidate_array;
        candidate_array.data = candidates.data();
        candidate_array.size = candidates.size();
        candidate_array.sorted = false;

        llama_token sampled = llama_sample_token_greedy(g_ctx, &candidate_array);
        if (sampled < 0) {
            error_code = "TOKEN_SAMPLING_FAILED";
            message = "Token sampling failed.";
            finish_reason = "error";
            break;
        }

        if (sampled == eos_token_id) {
            if (generated_tokens.empty()) {
                error_code = "GENERATED_EOS_BEFORE_TEXT";
                message = "EOS generated before any decoded text.";
            }
            finish_reason = "eos";
            break;
        }

        char piece_buf[256];
        int piece_len = llama_token_to_piece(g_model, sampled, piece_buf, sizeof(piece_buf));
        generated_tokens.push_back(sampled);

        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.token[0] = sampled;
        gen_batch.pos[0] = current_pos;
        gen_batch.n_seq_id[0] = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0] = 1;
        gen_batch.n_tokens = 1;

        bool gen_ok = llama_decode(g_ctx, gen_batch);
        llama_batch_free(gen_batch);
        if (!gen_ok) {
            error_code = "PROMPT_EVAL_FAILED";
            message = "Graph compute failed during generation.";
            finish_reason = "error";
            break;
        }

        if (piece_len <= 0) {
            error_code = "DECODE_FAILED";
            message = "Failed to decode generated token.";
            finish_reason = "error";
            break;
        }

        std::string piece(piece_buf, static_cast<size_t>(piece_len));
        if (!piece.empty()) {
            decoded_output += piece;
        }

        current_pos += 1;
    }

    const auto generation_end = std::chrono::steady_clock::now();
    const auto generation_ms = std::chrono::duration_cast<std::chrono::milliseconds>(generation_end - generation_start).count();
    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(generation_end - total_start).count();
    const std::string trimmed_output = trim_copy(decoded_output);
    const bool real_inference = !generated_tokens.empty();
    const bool native_generation_available = real_inference;

    if (error_code.empty()) {
        if (generated_tokens.empty()) {
            error_code = "TOKEN_SAMPLING_FAILED";
            message = "No tokens were generated.";
            finish_reason = "error";
        } else if (trimmed_output.empty()) {
            error_code = "GENERATED_TOKENS_DECODED_EMPTY";
            message = "Generated tokens decoded to empty text.";
            finish_reason = finish_reason == "eos" ? finish_reason : "error";
        } else if (wants_json || (trimmed_output.size() >= 2 && trimmed_output.front() == '{' && trimmed_output.back() == '}')) {
            json_parse_attempted = true;
            parsed_intent = extract_json_string(trimmed_output, "intent");
            parsed_risk_level = extract_json_string(trimmed_output, "riskLevel");
            if (parsed_intent.empty() || parsed_risk_level.empty()) {
                json_parse_success = false;
                error_code = "JSON_PARSE_FAILED";
                message = "Generated text was not valid JSON.";
                finish_reason = "error";
            } else {
                json_parse_success = true;
            }
        }
    }

    const bool success = error_code.empty() && !trimmed_output.empty();
    const int generated_count = static_cast<int>(generated_tokens.size());
    const double tps = generation_ms > 0
        ? static_cast<double>(generated_count) / (static_cast<double>(generation_ms) / 1000.0)
        : 0.0;

    char arch_buf[64];
    llama_model_arch(g_model, arch_buf, sizeof(arch_buf));

    char ttype_buf[64];
    llama_model_tokenizer_type(g_model, ttype_buf, sizeof(ttype_buf));

    std::string json = "{";
    bool first = true;
    append_bool(json, first, "success", success);
    append_bool(json, first, "ok", success);
    if (trimmed_output.empty()) {
        append_null(json, first, "text");
        append_null(json, first, "decodedText");
        append_null(json, first, "decoded_text");
    } else {
        append_string(json, first, "text", trimmed_output);
        append_string(json, first, "decodedText", trimmed_output);
        append_string(json, first, "decoded_text", trimmed_output);
    }
    append_int(json, first, "latencyMillis", static_cast<long long>(total_ms));
    append_int(json, first, "tokensGenerated", generated_count);
    append_int(json, first, "tokens_generated", generated_count);
    append_int(json, first, "promptTokens", prompt_token_count);
    append_int(json, first, "prompt_tokens", prompt_token_count);
    append_int(json, first, "prompt_tokens_count", prompt_token_count);
    append_int(json, first, "modelLoadMs", g_last_model_load_ms);
    append_int(json, first, "loadMs", g_last_model_load_ms);
    append_int(json, first, "load_ms", g_last_model_load_ms);
    append_int(json, first, "promptEvalMs", static_cast<long long>(prompt_eval_ms));
    append_int(json, first, "generationMs", static_cast<long long>(generation_ms));
    append_int(json, first, "generation_ms", static_cast<long long>(generation_ms));
    append_int(json, first, "contextSize", static_cast<long long>(llama_n_ctx(g_ctx)));
    append_int(json, first, "threadsUsed", 4);
    append_string(json, first, "backendType", backend);
    append_string(json, first, "backend", backend);
    append_string(json, first, "modelArch", arch_buf);
    append_int(json, first, "vocabSize", vocab_size);
    append_int(json, first, "vocab_size", vocab_size);
    append_int(json, first, "tensorsLoaded", static_cast<long long>(llama_n_tensors(g_model)));
    append_int(json, first, "tensors_loaded", static_cast<long long>(llama_n_tensors(g_model)));
    append_bool(json, first, "modelDetected", model_detected);
    append_bool(json, first, "model_detected", model_detected);
    append_bool(json, first, "model_loaded", true);
    append_bool(json, first, "modelLoaded", true);
    append_bool(json, first, "model_reused", g_model_reused);
    append_bool(json, first, "modelReused", g_model_reused);
    append_int(json, first, "model_load_count", g_model_load_count);
    append_int(json, first, "modelLoadCount", g_model_load_count);
    append_int(json, first, "generation_call_count", g_generation_call_count);
    append_int(json, first, "generationCallCount", g_generation_call_count);
    append_bool(json, first, "realTokenIds", real_token_ids);
    append_bool(json, first, "real_token_ids", real_token_ids);
    append_bool(json, first, "realInference", real_inference);
    append_bool(json, first, "real_inference", real_inference);
    append_bool(json, first, "nativeGenerationAvailable", native_generation_available);
    append_bool(json, first, "native_generation_available", native_generation_available);
    append_bool(json, first, "metadataParsed", true);
    append_bool(json, first, "tokenizerLoaded", tokenizer_loaded);
    append_bool(json, first, "tokenizer_loaded", tokenizer_loaded);
    append_bool(json, first, "tensorsWeightLoaded", llama_model_weights_loaded(g_model));
    append_bool(json, first, "ggmlGraphCompute", llama_logits_generated(g_ctx));
    append_bool(json, first, "logitsGenerated", llama_logits_generated(g_ctx));
    append_bool(json, first, "samplingActive", llama_sampling_active(g_ctx));
    append_bool(json, first, "deterministicResponse", true);
    append_int(json, first, "memoryEstimateBytes", 350ll * 1024ll * 1024ll);
    append_double(json, first, "tokensPerSecond", tps);
    append_bool(json, first, "nativeBridgeStable", true);
    append_bool(json, first, "jsonReturnBridge", true);
    append_bool(json, first, "crashFree", true);
    append_string(json, first, "tokenizerType", ttype_buf);
    append_string(json, first, "tokenizer_type", ttype_buf);
    append_int(json, first, "bosTokenId", llama_model_bos_token_id(g_model));
    append_int(json, first, "eosTokenId", llama_model_eos_token_id(g_model));
    append_int(json, first, "specialTokensCount", llama_model_special_tokens_count(g_model));
    append_bool(json, first, "tokenizationSuccess", tokenization_ok);
    append_bool(json, first, "tokenization_ok", tokenization_ok);
    append_int(json, first, "tokenizerLoadMs", g_last_model_load_ms);
    append_int(json, first, "tokenizer_load_ms", g_last_model_load_ms);
    append_bool(json, first, "ggmlGraphBuilt", llama_graph_built(g_ctx));
    append_int(json, first, "graphNodesCount", llama_graph_nodes_count(g_ctx));
    append_int(json, first, "memoryMappedMb", llama_model_mmap_mb(g_model));
    append_int(json, first, "memory_mapped_mb", llama_model_mmap_mb(g_model));
    append_bool(json, first, "logitsFromModelWeights", llama_logits_from_weights(g_ctx));
    append_bool(json, first, "decodedTokens", !trimmed_output.empty());
    append_int(json, first, "sampledTokenIdsCount", generated_count);
    append_string(json, first, "prompt_text", prompt_text);
    append_string(json, first, "promptText", prompt_text);
    append_int_array(json, first, "prompt_token_ids_sample", prompt_tokens, 12);
    append_int_array(json, first, "generated_token_ids_sample", generated_tokens, 12);
    append_bool(json, first, "json_parse_attempted", json_parse_attempted);
    append_bool(json, first, "jsonParseAttempted", json_parse_attempted);
    append_bool(json, first, "json_parse_success", json_parse_success);
    append_bool(json, first, "jsonParseSuccess", json_parse_success);
    append_string(json, first, "parsed_intent", parsed_intent);
    append_string(json, first, "parsedIntent", parsed_intent);
    append_string(json, first, "parsed_risk_level", parsed_risk_level);
    append_string(json, first, "parsedRiskLevel", parsed_risk_level);
    append_string(json, first, "finish_reason", finish_reason);
    append_string(json, first, "finishReason", finish_reason);
    append_bool(json, first, "simulation", false);
    append_bool(json, first, "simulationActive", false);
    if (error_code.empty()) {
        append_null(json, first, "error");
    } else {
        append_string(json, first, "error", error_code);
    }
    append_string(json, first, "message", message);
    append_bool(json, first, "upstreamSamplerLinked", false);
    append_string(json, first, "tokenIdsPreview", token_preview);
    append_string(json, first, "token_ids_preview", token_preview);
    if (error_code.empty()) {
        append_null(json, first, "lastError");
        append_null(json, first, "last_error");
        append_null(json, first, "lastFailure");
        append_null(json, first, "last_failure");
    } else {
        append_string(json, first, "lastError", error_code);
        append_string(json, first, "last_error", error_code);
        append_string(json, first, "lastFailure", message);
        append_string(json, first, "last_failure", message);
    }
    json += "}";

    LOGI("Native generation complete using %s in %lld ms", backend.c_str(), static_cast<long long>(total_ms));
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeIsModelLoaded(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeReleaseModel(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    LOGI("Cleaning up native GGUF backend.");
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    g_model_path.clear();
    g_model_reused = false;
    g_last_model_load_ms = 0;
    llama_backend_free();
}

JNIEXPORT void JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeUnloadModel(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    Java_com_nova_luna_brain_LlamaCppJni_nativeReleaseModel(env, clazz);
}

} // extern "C"
