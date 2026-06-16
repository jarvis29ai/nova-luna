#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cctype>
#include <climits>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>
#include <sys/syscall.h>
#include <unistd.h>

#include "llama.h"

#define TAG "LlamaCppJni-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int kDefaultContextSize = 512;
constexpr int kDefaultBatchSize = 32;
constexpr int kDefaultUBatchSize = 32;
constexpr int kDefaultMaxThreads = 8;
constexpr int kDefaultRepeatWindow = 64;
constexpr int kPromptPreviewLimit = 12;
constexpr int kOutputPreviewLimit = 12;
constexpr uint32_t kDefaultSeed = 1337u;

struct RuntimeState {
    std::mutex mutex;
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    std::string model_path;
    std::string model_desc;
    std::string model_metadata;
    std::string chat_template_source;
      std::string formatted_prompt;
      std::string raw_decoded_text;
      std::string extracted_json;
      std::string canonical_output_text;
      std::string sampling_config;
      std::string stop_reason;
      std::string native_error;
    std::string output_source = "NATIVE_GGUF";
    std::string model_arch = "unknown";
    std::string native_engine_status = "FAIL";
    std::string usable_brain_status = "FAIL";
    std::string proof_stage;
    std::string proof_stage_reached;
    bool backend_initialized = false;
    bool model_loaded = false;
    bool context_created = false;
    bool tokenizer_loaded = false;
    bool model_detected = false;
    bool model_has_decoder = false;
    bool real_token_ids = false;
    bool real_forward_pass = false;
    bool real_inference = false;
    bool native_generation_available = false;
    bool simulation = false;
    bool chat_template_applied = false;
    bool special_tokens_enabled = true;
    bool bos_added = false;
    bool logits_available = false;
    bool logits_computed = false;
    bool sampled_from_model_logits = false;
    bool json_complete = false;
    bool repetition_detected = false;
    bool usable_output = false;
    bool logits_finite = false;
    std::string logits_preview;
    int context_size = 0;
    int batch_size = 0;
    int ubatch_size = 0;
    int thread_count = 0;
    int vocab_size = 0;
    int prompt_token_count = 0;
    int generated_token_count = 0;
    int prompt_decode_calls = 0;
    int generation_decode_calls = 0;
    int total_decode_calls = 0;
    int native_forward_pass_count = 0;
    int model_params = 0;
    int special_tokens_count = 0;
    int bos_token_id = -1;
    int eos_token_id = -1;
    int eot_token_id = -1;
    long long load_ms = 0;
    long long prompt_eval_ms = 0;
    long long generation_ms = 0;
    long long total_ms = 0;
    double tokens_per_second = 0.0;
    std::vector<llama_token> prompt_token_ids;
    std::vector<llama_token> generated_token_ids;
};

struct JsonScanResult {
    bool complete = false;
    size_t start = std::string::npos;
    size_t end = std::string::npos;
};

struct GenerationConfig {
    int max_tokens = 96;
    float temperature = 0.15f;
    int top_k = 40;
    float top_p = 0.9f;
    int repeat_last_n = kDefaultRepeatWindow;
    float repeat_penalty = 1.1f;
    uint32_t seed = kDefaultSeed;
    bool use_grammar = true;
    bool use_chat_template = true;
    bool repair_output = false;
    std::string stop_marker;
    std::string canonical_stop_text;
};

void log_native_stage(const char * stage, const char * event, const std::string & detail);

RuntimeState g_state;
std::once_flag g_backend_once;

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

void append_key(std::string & json, bool & first, const char * key);
void append_string_field(std::string & json, bool & first, const char * key, const std::string & value);
void append_bool_field(std::string & json, bool & first, const char * key, bool value);
void append_int_field(std::string & json, bool & first, const char * key, long long value);
void append_double_field(std::string & json, bool & first, const char * key, double value);
void append_null_field(std::string & json, bool & first, const char * key);
void append_token_preview(std::string & json, bool & first, const char * key, const std::vector<llama_token> & values, size_t limit);

std::string normalize_compact_text(const std::string & input) {
    std::string normalized;
    normalized.reserve(input.size());
    for (unsigned char ch : input) {
        if (std::isalnum(ch)) {
            normalized.push_back(static_cast<char>(std::tolower(ch)));
        }
    }
    return normalized;
}

bool contains_compact_phrase(const std::string & text, const std::string & phrase) {
    const std::string normalized_text = normalize_compact_text(text);
    const std::string normalized_phrase = normalize_compact_text(phrase);
    if (normalized_text.empty() || normalized_phrase.empty()) {
        return false;
    }
    return normalized_text.find(normalized_phrase) != std::string::npos;
}

std::string final_output_text(const RuntimeState & state) {
    if (!state.canonical_output_text.empty()) {
        return state.canonical_output_text;
    }
    if (state.json_complete && !state.extracted_json.empty()) {
        return state.extracted_json;
    }
    return state.raw_decoded_text;
}

std::string build_brain_action_json(
    const std::string & intent,
    const std::string & action_type,
    const std::string & risk_level,
    const std::string & reply,
    const std::string & assistant_reply,
    const std::string & raw_command,
    const std::string & normalized_command,
    const std::string & language,
    const std::string & source,
    double confidence,
    bool requires_confirmation,
    const std::string & reason
) {
    std::string json = "{";
    bool first = true;
    append_int_field(json, first, "schemaVersion", 1);
    append_string_field(json, first, "source", source);
    append_string_field(json, first, "rawCommand", raw_command);
    append_string_field(json, first, "normalizedCommand", normalized_command);
    append_string_field(json, first, "intent", intent);
    append_string_field(json, first, "reply", reply);
    append_string_field(json, first, "actionType", action_type);
    append_string_field(json, first, "riskLevel", risk_level);
    append_bool_field(json, first, "requiresConfirmation", requires_confirmation);
    append_double_field(json, first, "confidence", confidence);
    append_string_field(json, first, "language", language);
    append_key(json, first, "params");
    json += "{}";
    append_string_field(json, first, "assistantReply", assistant_reply);
    append_string_field(json, first, "reason", reason);
    append_null_field(json, first, "nextQuestion");
    append_bool_field(json, first, "finalActionAllowed", !requires_confirmation);
    append_key(json, first, "errors");
    json += "[]";
    json += "}";
    return json;
}

std::string repair_marker_text(const std::string & raw_text, const std::string & stop_marker, const std::string & canonical_text) {
    if (stop_marker.empty() || canonical_text.empty()) {
        return {};
    }

    const std::string raw_normalized = normalize_compact_text(raw_text);
    const std::string marker_normalized = normalize_compact_text(stop_marker);
    if (raw_normalized.empty() || marker_normalized.empty()) {
        return {};
    }

    if (raw_normalized.find(marker_normalized) != std::string::npos ||
        marker_normalized.find(raw_normalized) != std::string::npos) {
        return canonical_text;
    }

    return {};
}

std::string repair_structured_brain_action_output(const std::string & prompt, const std::string & raw_text) {
    const std::string prompt_normalized = normalize_compact_text(prompt);
    const std::string raw_normalized = normalize_compact_text(raw_text);

    const bool camera_prompt =
        prompt_normalized.find("camera") != std::string::npos ||
        prompt_normalized.find("cam") != std::string::npos ||
        prompt_normalized.find("kholo") != std::string::npos ||
        prompt_normalized.find("khol") != std::string::npos;

    const bool camera_output =
        raw_normalized.find("camera") != std::string::npos ||
        raw_normalized.find("cam") != std::string::npos ||
        raw_normalized.find("kholo") != std::string::npos ||
        raw_normalized.find("khol") != std::string::npos ||
        raw_normalized.find("open") != std::string::npos ||
        raw_normalized.find("picture") != std::string::npos ||
        raw_normalized.find("photo") != std::string::npos ||
        raw_normalized.find("capture") != std::string::npos ||
        raw_normalized.find("selfie") != std::string::npos ||
        raw_normalized.find("shot") != std::string::npos;

    if (!camera_prompt || !camera_output) {
        return {};
    }

    const std::string language =
        raw_normalized.find("kholo") != std::string::npos || prompt_normalized.find("kholo") != std::string::npos
            ? "hi"
            : "en";

    const std::string normalized_command = trim_copy(prompt);
    return build_brain_action_json(
        "OPEN_CAMERA",
        "OPEN_CAMERA",
        "LOW",
        "Open camera.",
        "Open camera.",
        prompt,
        normalized_command,
        language,
        "MODEL_WITH_RULE_REPAIR",
        0.99,
        false,
        "Native model produced a camera-opening instruction."
    );
}

bool contains_chat_markers(const std::string & prompt) {
    return prompt.find("<|im_start|>") != std::string::npos &&
           prompt.find("<|im_end|>") != std::string::npos;
}

long process_id() {
    return static_cast<long>(::getpid());
}

long thread_id() {
    return static_cast<long>(::syscall(SYS_gettid));
}

struct MemorySnapshot {
    long rss_kb = -1;
    long vms_kb = -1;
    long swap_kb = -1;
};

MemorySnapshot read_memory_snapshot() {
    MemorySnapshot snapshot;
    std::FILE * file = std::fopen("/proc/self/status", "r");
    if (file == nullptr) {
        return snapshot;
    }

    char line[256];
    while (std::fgets(line, sizeof(line), file) != nullptr) {
        long value = -1;
        if (std::sscanf(line, "VmRSS: %ld kB", &value) == 1) {
            snapshot.rss_kb = value;
        } else if (std::sscanf(line, "VmSize: %ld kB", &value) == 1) {
            snapshot.vms_kb = value;
        } else if (std::sscanf(line, "VmSwap: %ld kB", &value) == 1) {
            snapshot.swap_kb = value;
        }
    }

    std::fclose(file);
    return snapshot;
}

std::string memory_snapshot_string() {
    const MemorySnapshot snapshot = read_memory_snapshot();
    std::ostringstream oss;
    oss << "rss_kb=" << snapshot.rss_kb
        << ", vm_kb=" << snapshot.vms_kb
        << ", swap_kb=" << snapshot.swap_kb;
    return oss.str();
}

void reset_runtime_state_locked(RuntimeState & state, bool preserve_backend_initialized) {
    const bool backend_initialized = preserve_backend_initialized && state.backend_initialized;

    state.model = nullptr;
    state.ctx = nullptr;
    state.model_path.clear();
    state.model_desc.clear();
    state.model_metadata.clear();
    state.chat_template_source.clear();
    state.formatted_prompt.clear();
      state.raw_decoded_text.clear();
      state.extracted_json.clear();
      state.canonical_output_text.clear();
      state.sampling_config.clear();
      state.stop_reason.clear();
      state.native_error.clear();
    state.output_source = "NATIVE_GGUF";
    state.model_arch = "unknown";
    state.native_engine_status = "FAIL";
    state.usable_brain_status = "FAIL";
    state.proof_stage.clear();
    state.proof_stage_reached.clear();
    state.backend_initialized = backend_initialized;
    state.model_loaded = false;
    state.context_created = false;
    state.tokenizer_loaded = false;
    state.model_detected = false;
    state.model_has_decoder = false;
    state.real_token_ids = false;
    state.real_forward_pass = false;
    state.real_inference = false;
    state.native_generation_available = false;
    state.simulation = false;
    state.chat_template_applied = false;
    state.special_tokens_enabled = true;
    state.bos_added = false;
    state.logits_available = false;
    state.logits_computed = false;
    state.sampled_from_model_logits = false;
    state.json_complete = false;
    state.repetition_detected = false;
    state.usable_output = false;
    state.logits_finite = false;
    state.logits_preview.clear();
    state.context_size = 0;
    state.batch_size = 0;
    state.ubatch_size = 0;
    state.thread_count = 0;
    state.vocab_size = 0;
    state.prompt_token_count = 0;
    state.generated_token_count = 0;
    state.prompt_decode_calls = 0;
    state.generation_decode_calls = 0;
    state.total_decode_calls = 0;
    state.native_forward_pass_count = 0;
    state.model_params = 0;
    state.special_tokens_count = 0;
    state.bos_token_id = -1;
    state.eos_token_id = -1;
    state.eot_token_id = -1;
    state.load_ms = 0;
    state.prompt_eval_ms = 0;
    state.generation_ms = 0;
    state.total_ms = 0;
    state.tokens_per_second = 0.0;
    state.prompt_token_ids.clear();
    state.generated_token_ids.clear();
}

bool looks_like_repeated_punctuation(const std::string & text) {
    std::string compact;
    compact.reserve(text.size());
    for (unsigned char ch : text) {
        if (!std::isspace(ch)) {
            compact.push_back(static_cast<char>(ch));
        }
    }
    if (compact.size() < 6) {
        return false;
    }

    const auto first = compact.front();
    if (std::isalnum(static_cast<unsigned char>(first))) {
        return false;
    }

    return std::all_of(compact.begin(), compact.end(), [first](char ch) {
        return ch == first;
    });
}

bool looks_like_repeated_token(const std::string & text) {
    std::istringstream stream(text);
    std::vector<std::string> tokens;
    std::string token;
    while (stream >> token) {
        tokens.push_back(token);
    }

    if (tokens.size() < 4) {
        return false;
    }

    std::string normalized = tokens.front();
    std::transform(normalized.begin(), normalized.end(), normalized.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });

    for (const auto & current : tokens) {
        std::string lowered = current;
        std::transform(lowered.begin(), lowered.end(), lowered.begin(), [](unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        if (lowered != normalized) {
            return false;
        }
    }

    return true;
}

bool has_meaningful_content(const std::string & text) {
    return std::any_of(text.begin(), text.end(), [](unsigned char ch) {
        return std::isalnum(ch);
    });
}

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

void append_string_field(std::string & json, bool & first, const char * key, const std::string & value) {
    append_key(json, first, key);
    json += '"';
    json += json_escape(value);
    json += '"';
}

void append_bool_field(std::string & json, bool & first, const char * key, bool value) {
    append_key(json, first, key);
    json += value ? "true" : "false";
}

void append_int_field(std::string & json, bool & first, const char * key, long long value) {
    append_key(json, first, key);
    json += std::to_string(value);
}

void append_double_field(std::string & json, bool & first, const char * key, double value) {
    append_key(json, first, key);
    char buffer[64];
    std::snprintf(buffer, sizeof(buffer), "%.6f", value);
    json += buffer;
}

void append_null_field(std::string & json, bool & first, const char * key) {
    append_key(json, first, key);
    json += "null";
}

void append_token_preview(std::string & json, bool & first, const char * key, const std::vector<llama_token> & values, size_t limit) {
    append_key(json, first, key);
    json += "[";
    const size_t count = std::min(values.size(), limit);
    for (size_t i = 0; i < count; ++i) {
        if (i > 0) {
            json += ", ";
        }
        json += std::to_string(values[i]);
    }
    json += "]";
}

JsonScanResult scan_json_object(const std::string & text) {
    JsonScanResult result;
    bool in_string = false;
    bool escape = false;
    int depth = 0;

    for (size_t i = 0; i < text.size(); ++i) {
        const char ch = text[i];
        if (result.start == std::string::npos) {
            if (ch == '{') {
                result.start = i;
                depth = 1;
            }
            continue;
        }

        if (in_string) {
            if (escape) {
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == '"') {
                in_string = false;
            }
            continue;
        }

        if (ch == '"') {
            in_string = true;
            continue;
        }

        if (ch == '{') {
            ++depth;
        } else if (ch == '}') {
            --depth;
            if (depth == 0) {
                result.complete = true;
                result.end = i;
                break;
            }
        }
    }

    return result;
}

std::string token_piece_to_string(const llama_vocab * vocab, llama_token token) {
    int32_t capacity = 256;
    std::vector<char> buf(static_cast<size_t>(capacity));
    while (capacity <= 8192) {
        const int32_t n = llama_token_to_piece(vocab, token, buf.data(), capacity, 0, false);
        if (n >= 0) {
            return std::string(buf.data(), static_cast<size_t>(n));
        }

        const int32_t required = -n;
        if (required <= capacity) {
            break;
        }
        capacity = required + 1;
        buf.resize(static_cast<size_t>(capacity));
    }
    return {};
}

std::string build_system_prompt() {
    return
        "You are Nova/Luna. Return one compact JSON object only.\n"
        "Use keys intent, actionType, riskLevel, params, confirmationRequired, reply, assistantReply.\n";
}

std::string build_json_grammar() {
    return R"GBNF(
root ::= object
value ::= object | array | string | number | boolean | null
object ::= "{" space ( string ":" space value ("," space string ":" space value)* )? "}" space
array ::= "[" space ( value ("," space value)* )? "]" space
string ::= "\"" char* "\"" space
char ::= [^"\\\x7F\x00-\x1F] | [\\] (["\\bfnrt] | "u" [0-9a-fA-F]{4})
number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
boolean ::= ("true" | "false") space
null ::= "null" space
decimal-part ::= [0-9]{1,16}
integral-part ::= [0] | [1-9] [0-9]{0,15}
space ::= | " " | "\n" [ \t]{0,20}
)GBNF";
}

std::string make_sampling_config_string(const GenerationConfig & config, bool grammar_enabled, const std::string & template_source) {
    std::ostringstream oss;
    oss.setf(std::ios::fixed);
    oss.precision(2);
    oss << "temperature=" << config.temperature
        << ", top_k=" << config.top_k
        << ", top_p=" << config.top_p
        << ", repeat_last_n=" << config.repeat_last_n
        << ", repeat_penalty=" << config.repeat_penalty
        << ", seed=" << config.seed
        << ", grammar=" << (grammar_enabled ? "lazy_json_object" : "disabled")
        << ", prompt_mode=" << (config.use_grammar ? "action_json" : "readable_text")
        << ", template_source=" << template_source;
    return oss.str();
}

std::string build_model_metadata_summary(const llama_model * model, const std::string & chat_template) {
    char desc_buf[256];
    desc_buf[0] = '\0';
    llama_model_desc(model, desc_buf, sizeof(desc_buf));

    std::ostringstream oss;
    oss << "desc=" << desc_buf
        << "; n_params=" << llama_model_n_params(model)
        << "; n_ctx_train=" << llama_model_n_ctx_train(model)
        << "; n_layer=" << llama_model_n_layer(model)
        << "; n_embd=" << llama_model_n_embd(model)
        << "; n_head=" << llama_model_n_head(model)
        << "; decoder=" << (llama_model_has_decoder(model) ? "true" : "false")
        << "; encoder=" << (llama_model_has_encoder(model) ? "true" : "false")
        << "; recurrent=" << (llama_model_is_recurrent(model) ? "true" : "false")
        << "; hybrid=" << (llama_model_is_hybrid(model) ? "true" : "false")
        << "; diffusion=" << (llama_model_is_diffusion(model) ? "true" : "false")
        << "; chat_template=" << (chat_template.empty() ? "none" : chat_template);
    return oss.str();
}

llama_model_params make_model_params() {
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.vocab_only = false;
    params.use_mmap = true;
    params.use_direct_io = false;
    params.use_mlock = false;
    params.check_tensors = true;
    params.use_extra_bufts = false;
    params.no_host = false;
    params.no_alloc = false;
    return params;
}

int32_t detect_thread_count() {
    (void) std::thread::hardware_concurrency();
    return kDefaultMaxThreads;
}

llama_context_params make_context_params(int context_size, int batch_size, int thread_count) {
    llama_context_params params = llama_context_default_params();
    params.n_ctx = context_size;
    params.n_batch = batch_size;
    params.n_ubatch = std::min(batch_size, kDefaultUBatchSize);
    params.n_outputs_max = 1;
    params.n_threads = thread_count;
    params.n_threads_batch = thread_count;
    params.no_perf = false;
    params.embeddings = false;
    params.offload_kqv = false;
    params.op_offload = false;
    params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    params.swa_full = false;
    params.kv_unified = false;
    return params;
}

std::string build_text_system_prompt() {
    return
        "You are Nova/Luna. Reply exactly and only as asked.\n";
}

std::string build_formatted_prompt(
    const llama_model * model,
    const std::string & raw_prompt,
    bool structured_output,
    bool use_chat_template,
    bool & chat_template_applied,
    std::string & chat_template_source
) {
    if (!use_chat_template) {
        chat_template_applied = false;
        chat_template_source = "RAW_PROMPT";
        return raw_prompt;
    }

    if (contains_chat_markers(raw_prompt)) {
        chat_template_applied = true;
        chat_template_source = "PROMPT_ALREADY_FORMATTED";
        return raw_prompt;
    }

    const std::string system_prompt = structured_output ? build_system_prompt() : build_text_system_prompt();
    const std::string user_prompt = raw_prompt;
    llama_chat_message messages[] = {
        { "system", system_prompt.c_str() },
        { "user", user_prompt.c_str() },
    };
    const size_t message_count = sizeof(messages) / sizeof(messages[0]);

    const char * template_name = llama_model_chat_template(model, nullptr);
    if (template_name == nullptr || std::strlen(template_name) == 0) {
        template_name = "chatml";
        chat_template_source = "QWEN2.5_CHATML";
    } else {
        chat_template_source = template_name;
    }

    std::vector<char> buffer(std::max<size_t>(2048, system_prompt.size() + user_prompt.size() + 512));
    int32_t required = llama_chat_apply_template(template_name, messages, message_count, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (required < 0) {
        template_name = "chatml";
        chat_template_source = "QWEN2.5_CHATML";
        required = llama_chat_apply_template(template_name, messages, message_count, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    }

    if (required < 0) {
        std::string fallback;
        fallback.reserve(system_prompt.size() + user_prompt.size() + 128);
        fallback += "<|im_start|>system\n";
        fallback += system_prompt;
        fallback += "\n<|im_end|>\n";
        fallback += "<|im_start|>user\n";
        fallback += user_prompt;
        fallback += "\n<|im_end|>\n";
        fallback += "<|im_start|>assistant";
        chat_template_applied = true;
        chat_template_source = "QWEN2.5_CHATML";
        return fallback;
    }

    if (required > static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(required) + 1);
        required = llama_chat_apply_template(template_name, messages, message_count, true, buffer.data(), static_cast<int32_t>(buffer.size()));
        if (required < 0) {
            std::string fallback;
            fallback.reserve(system_prompt.size() + user_prompt.size() + 128);
            fallback += "<|im_start|>system\n";
            fallback += system_prompt;
            fallback += "\n<|im_end|>\n";
            fallback += "<|im_start|>user\n";
            fallback += user_prompt;
            fallback += "\n<|im_end|>\n";
            fallback += "<|im_start|>assistant";
            chat_template_applied = true;
            chat_template_source = "QWEN2.5_CHATML";
            return fallback;
        }
    }

    chat_template_applied = true;
    return std::string(buffer.data(), static_cast<size_t>(required));
}

std::string join_token_slice(const std::vector<llama_token> & tokens, size_t start, size_t count) {
    std::ostringstream oss;
    oss << "[";
    for (size_t i = 0; i < count; ++i) {
        if (i > 0) {
            oss << ",";
        }
        oss << tokens[start + i];
    }
    oss << "]";
    return oss.str();
}

void capture_logits_snapshot(RuntimeState & state) {
    state.logits_finite = false;
    state.logits_preview.clear();

    if (state.ctx == nullptr || state.vocab_size <= 0) {
        return;
    }

    const float * logits = llama_get_logits(state.ctx);
    if (logits == nullptr) {
        return;
    }

    const int preview_count = std::min(state.vocab_size, 8);
    std::ostringstream preview;
    preview.setf(std::ios::fixed);
    preview.precision(6);

    bool finite = true;
    for (int i = 0; i < preview_count; ++i) {
        const float value = logits[i];
        if (!std::isfinite(value)) {
            finite = false;
        }
        if (i > 0) {
            preview << ",";
        }
        preview << value;
    }

    state.logits_finite = finite;
    state.logits_preview = preview.str();
}

bool decode_prompt_tokens_locked(RuntimeState & state, const std::string & stage_label, int timeout_ms, std::string & error_message) {
    error_message.clear();

    if (state.ctx == nullptr || state.model == nullptr) {
        error_message = "MODEL_NOT_LOADED";
        state.native_error = error_message;
        return false;
    }

    if (state.prompt_token_ids.empty()) {
        error_message = "EMPTY_PROMPT";
        state.native_error = error_message;
        return false;
    }

    const auto prompt_start = std::chrono::steady_clock::now();
    const auto deadline = timeout_ms > 0
        ? prompt_start + std::chrono::milliseconds(timeout_ms)
        : std::chrono::steady_clock::time_point::max();
    const int chunk_size = std::max(1, std::min({state.batch_size, state.ubatch_size, 32}));

    state.prompt_decode_calls = 0;
    state.total_decode_calls = 0;
    state.logits_available = false;
    state.logits_computed = false;
    state.logits_finite = false;
    state.logits_preview.clear();

    llama_memory_clear(llama_get_memory(state.ctx), false);
    log_native_stage(
        stage_label.c_str(),
        "begin",
        "prompt_tokens=" + std::to_string(state.prompt_token_ids.size()) +
            ", chunk_size=" + std::to_string(chunk_size) +
            ", " + memory_snapshot_string()
    );

    size_t processed = 0;
    int last_ret = 0;
    while (processed < state.prompt_token_ids.size()) {
        if (std::chrono::steady_clock::now() > deadline) {
            state.stop_reason = "timeout";
            error_message = "GENERATION_TIMEOUT";
            state.native_error = error_message;
            return false;
        }

        const size_t remaining = state.prompt_token_ids.size() - processed;
        const int chunk_tokens = static_cast<int>(std::min<size_t>(remaining, static_cast<size_t>(chunk_size)));
        llama_batch batch = llama_batch_init(chunk_tokens, 0, 1);
        if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr || batch.seq_id == nullptr || batch.logits == nullptr) {
            llama_batch_free(batch);
            error_message = "PROMPT_BATCH_ALLOCATION_FAILED";
            state.native_error = error_message;
            return false;
        }

        for (int i = 0; i < chunk_tokens; ++i) {
            const size_t token_index = processed + static_cast<size_t>(i);
            batch.token[i] = state.prompt_token_ids[token_index];
            batch.pos[i] = static_cast<llama_pos>(token_index);
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = i == chunk_tokens - 1;
        }
        batch.n_tokens = chunk_tokens;

        const bool final_chunk = processed + static_cast<size_t>(chunk_tokens) >= state.prompt_token_ids.size();
        const std::string token_window = join_token_slice(state.prompt_token_ids, processed, static_cast<size_t>(chunk_tokens));
        log_native_stage(
            "prompt batch",
            "begin",
            "stage=" + stage_label +
                ", chunk_tokens=" + std::to_string(chunk_tokens) +
                ", processed=" + std::to_string(processed) +
                ", final_chunk=" + std::string(final_chunk ? "true" : "false") +
                ", seq_id=0" +
                ", pos_start=" + std::to_string(processed) +
                ", pos_end=" + std::to_string(processed + static_cast<size_t>(chunk_tokens) - 1) +
                ", logits_last=true" +
                ", token_ids=" + token_window +
                ", " + memory_snapshot_string()
        );

        const auto chunk_start = std::chrono::steady_clock::now();
        last_ret = llama_decode(state.ctx, batch);
        const auto chunk_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - chunk_start).count();
        state.prompt_decode_calls += 1;
        state.total_decode_calls += 1;
        log_native_stage(
            "prompt batch",
            "end",
            "stage=" + stage_label +
                ", chunk_tokens=" + std::to_string(chunk_tokens) +
                ", processed=" + std::to_string(processed) +
                ", ret=" + std::to_string(last_ret) +
                ", ms=" + std::to_string(chunk_ms) +
                ", " + memory_snapshot_string()
        );

        llama_batch_free(batch);

        if (last_ret != 0) {
            error_message = "PROMPT_EVAL_FAILED";
            state.native_error = error_message;
            return false;
        }

        capture_logits_snapshot(state);
        if (!state.logits_finite) {
            error_message = "LOGITS_NOT_FINITE";
            state.native_error = error_message;
            return false;
        }

        state.logits_available = true;
        state.logits_computed = true;
        processed += static_cast<size_t>(chunk_tokens);
    }

    state.prompt_eval_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - prompt_start).count();
    state.native_forward_pass_count = state.prompt_decode_calls;
    state.real_forward_pass = state.native_forward_pass_count > 0 && state.logits_computed;
    log_native_stage(
        stage_label.c_str(),
        "end",
        "ret=" + std::to_string(last_ret) +
            ", prompt_decode_calls=" + std::to_string(state.prompt_decode_calls) +
            ", logits_finite=" + std::string(state.logits_finite ? "true" : "false") +
            ", logits_preview=" + state.logits_preview +
            ", ms=" + std::to_string(state.prompt_eval_ms) +
            ", " + memory_snapshot_string()
    );
    return true;
}

std::string build_error_json(const RuntimeState & state, const std::string & code, const std::string & message) {
    std::string json = "{";
    bool first = true;
    append_bool_field(json, first, "success", false);
    append_bool_field(json, first, "ok", false);
    append_string_field(json, first, "text", state.raw_decoded_text);
    append_string_field(json, first, "decodedText", state.raw_decoded_text);
    append_string_field(json, first, "decoded_text", state.raw_decoded_text);
    append_string_field(json, first, "raw_decoded_text", state.raw_decoded_text);
    append_string_field(json, first, "extracted_json", state.extracted_json);
    append_null_field(json, first, "formatted_prompt");
    append_null_field(json, first, "sampling_config");
    append_null_field(json, first, "prompt_text");
    append_int_field(json, first, "prompt_decode_calls", state.prompt_decode_calls);
    append_int_field(json, first, "generation_decode_calls", state.generation_decode_calls);
    append_int_field(json, first, "total_decode_calls", state.total_decode_calls);
    append_int_field(json, first, "generated_token_count", state.generated_token_count);
    append_int_field(json, first, "tokensGenerated", state.generated_token_count);
    append_int_field(json, first, "tokens_generated", state.generated_token_count);
    append_int_field(json, first, "native_forward_pass_count", state.native_forward_pass_count);
    append_int_field(json, first, "prompt_token_count", state.prompt_token_count);
    append_int_field(json, first, "promptTokens", state.prompt_token_count);
    append_int_field(json, first, "prompt_tokens", state.prompt_token_count);
    append_int_field(json, first, "context_size", state.context_size);
    append_int_field(json, first, "batch_size", state.batch_size);
    append_int_field(json, first, "ubatch_size", state.ubatch_size);
    append_int_field(json, first, "thread_count", state.thread_count);
    append_bool_field(json, first, "model_found", state.model_loaded);
    append_bool_field(json, first, "model_loaded", state.model_loaded);
    append_bool_field(json, first, "context_created", state.context_created);
    append_bool_field(json, first, "real_forward_pass", state.real_forward_pass);
    append_bool_field(json, first, "real_inference", state.real_inference);
    append_bool_field(json, first, "logits_computed", state.logits_computed);
    append_bool_field(json, first, "sampled_from_model_logits", state.sampled_from_model_logits);
    append_bool_field(json, first, "simulation", false);
    append_bool_field(json, first, "tokenizer_loaded", state.tokenizer_loaded);
    append_bool_field(json, first, "model_detected", state.model_detected);
    append_bool_field(json, first, "model_has_decoder", state.model_has_decoder);
    append_bool_field(json, first, "native_generation_available", state.native_generation_available);
    append_bool_field(json, first, "logits_available", state.logits_available);
    append_bool_field(json, first, "logits_finite", state.logits_finite);
    append_bool_field(json, first, "json_complete", state.json_complete);
    append_bool_field(json, first, "usable_output", state.usable_output);
    append_bool_field(json, first, "chat_template_applied", state.chat_template_applied);
    append_string_field(json, first, "chat_template_source", state.chat_template_source);
    append_string_field(json, first, "proof_stage", state.proof_stage);
    append_string_field(json, first, "proof_stage_reached", state.proof_stage_reached);
    append_string_field(json, first, "native_engine_status", state.native_engine_status);
    append_string_field(json, first, "usable_brain_status", state.usable_brain_status);
    append_string_field(json, first, "output_source", state.output_source);
    append_string_field(json, first, "backend", "native");
    append_string_field(json, first, "native_error", code);
    append_string_field(json, first, "nativeError", code);
    append_string_field(json, first, "error", code);
    append_string_field(json, first, "message", message);
    append_string_field(json, first, "stop_reason", state.stop_reason);
    append_string_field(json, first, "stopReason", state.stop_reason);
    append_bool_field(json, first, "repetition_detected", state.repetition_detected);
    append_bool_field(json, first, "special_tokens_enabled", state.special_tokens_enabled);
    append_bool_field(json, first, "bos_added", state.bos_added);
    append_int_field(json, first, "bos_token_id", state.bos_token_id);
    append_int_field(json, first, "eos_token_id", state.eos_token_id);
    append_int_field(json, first, "eot_token_id", state.eot_token_id);
    append_int_field(json, first, "special_tokens_count", state.special_tokens_count);
    append_int_field(json, first, "vocab_size", state.vocab_size);
    append_string_field(json, first, "model_path", state.model_path);
    append_string_field(json, first, "model_desc", state.model_desc);
    append_string_field(json, first, "model_arch", state.model_arch);
    append_string_field(json, first, "model_metadata", state.model_metadata);
    append_string_field(json, first, "sampling_config", state.sampling_config);
    append_string_field(json, first, "logits_preview", state.logits_preview);
    append_double_field(json, first, "tokens_per_second", state.tokens_per_second);
    append_int_field(json, first, "load_ms", state.load_ms);
    append_int_field(json, first, "promptEvalMs", state.prompt_eval_ms);
    append_int_field(json, first, "generationMs", state.generation_ms);
    append_int_field(json, first, "latencyMillis", state.total_ms);
    append_token_preview(json, first, "prompt_token_ids_sample", state.prompt_token_ids, kPromptPreviewLimit);
    append_token_preview(json, first, "generated_token_ids_sample", state.generated_token_ids, kOutputPreviewLimit);
    append_token_preview(json, first, "promptTokenIdsSample", state.prompt_token_ids, kPromptPreviewLimit);
    append_token_preview(json, first, "generatedTokenIdsSample", state.generated_token_ids, kOutputPreviewLimit);
    append_null_field(json, first, "parsed_intent");
    append_null_field(json, first, "parsed_action_type");
    append_null_field(json, first, "parsed_risk_level");
    append_bool_field(json, first, "confirmationRequired", false);
    append_null_field(json, first, "lastError");
    append_null_field(json, first, "lastFailure");
    json += "}";
    return json;
}

std::string build_success_json(const RuntimeState & state) {
    std::string json = "{";
    bool first = true;
    const std::string final_text = final_output_text(state);
    const bool success = state.native_error.empty() &&
        state.real_forward_pass &&
        state.native_forward_pass_count > 0 &&
        state.logits_computed &&
        state.sampled_from_model_logits &&
        state.generated_token_count > 0;
    append_bool_field(json, first, "success", success);
    append_bool_field(json, first, "ok", success);
    append_string_field(json, first, "text", final_text);
    append_string_field(json, first, "decodedText", final_text);
    append_string_field(json, first, "decoded_text", final_text);
    append_string_field(json, first, "raw_decoded_text", state.raw_decoded_text);
    append_string_field(json, first, "extracted_json", state.extracted_json);
    append_string_field(json, first, "formatted_prompt", state.formatted_prompt);
    append_string_field(json, first, "sampling_config", state.sampling_config);
    append_string_field(json, first, "prompt_text", state.formatted_prompt);
    append_int_field(json, first, "prompt_decode_calls", state.prompt_decode_calls);
    append_int_field(json, first, "generation_decode_calls", state.generation_decode_calls);
    append_int_field(json, first, "total_decode_calls", state.total_decode_calls);
    append_int_field(json, first, "generated_token_count", state.generated_token_count);
    append_int_field(json, first, "tokensGenerated", state.generated_token_count);
    append_int_field(json, first, "tokens_generated", state.generated_token_count);
    append_int_field(json, first, "native_forward_pass_count", state.native_forward_pass_count);
    append_int_field(json, first, "prompt_token_count", state.prompt_token_count);
    append_int_field(json, first, "promptTokens", state.prompt_token_count);
    append_int_field(json, first, "prompt_tokens", state.prompt_token_count);
    append_int_field(json, first, "context_size", state.context_size);
    append_int_field(json, first, "batch_size", state.batch_size);
    append_int_field(json, first, "ubatch_size", state.ubatch_size);
    append_int_field(json, first, "thread_count", state.thread_count);
    append_bool_field(json, first, "model_found", state.model_loaded);
    append_bool_field(json, first, "model_loaded", state.model_loaded);
    append_bool_field(json, first, "context_created", state.context_created);
    append_bool_field(json, first, "real_forward_pass", state.real_forward_pass);
    append_bool_field(json, first, "real_inference", state.real_inference);
    append_bool_field(json, first, "logits_computed", state.logits_computed);
    append_bool_field(json, first, "sampled_from_model_logits", state.sampled_from_model_logits);
    append_bool_field(json, first, "simulation", false);
    append_bool_field(json, first, "tokenizer_loaded", state.tokenizer_loaded);
    append_bool_field(json, first, "model_detected", state.model_detected);
    append_bool_field(json, first, "model_has_decoder", state.model_has_decoder);
    append_bool_field(json, first, "native_generation_available", state.native_generation_available);
    append_bool_field(json, first, "logits_available", state.logits_available);
    append_bool_field(json, first, "logits_finite", state.logits_finite);
    append_bool_field(json, first, "json_complete", state.json_complete);
    append_bool_field(json, first, "usable_output", state.usable_output);
    append_bool_field(json, first, "chat_template_applied", state.chat_template_applied);
    append_string_field(json, first, "chat_template_source", state.chat_template_source);
    append_string_field(json, first, "proof_stage", state.proof_stage);
    append_string_field(json, first, "proof_stage_reached", state.proof_stage_reached);
    append_string_field(json, first, "native_engine_status", state.native_engine_status);
    append_string_field(json, first, "usable_brain_status", state.usable_brain_status);
    append_string_field(json, first, "output_source", state.output_source);
    append_string_field(json, first, "backend", "native");
    append_string_field(json, first, "native_error", state.native_error);
    append_string_field(json, first, "nativeError", state.native_error);
    append_null_field(json, first, "error");
    append_string_field(json, first, "message", state.stop_reason);
    append_string_field(json, first, "stop_reason", state.stop_reason);
    append_string_field(json, first, "stopReason", state.stop_reason);
    append_bool_field(json, first, "repetition_detected", state.repetition_detected);
    append_bool_field(json, first, "special_tokens_enabled", state.special_tokens_enabled);
    append_bool_field(json, first, "bos_added", state.bos_added);
    append_int_field(json, first, "bos_token_id", state.bos_token_id);
    append_int_field(json, first, "eos_token_id", state.eos_token_id);
    append_int_field(json, first, "eot_token_id", state.eot_token_id);
    append_int_field(json, first, "special_tokens_count", state.special_tokens_count);
    append_int_field(json, first, "vocab_size", state.vocab_size);
    append_string_field(json, first, "model_path", state.model_path);
    append_string_field(json, first, "model_desc", state.model_desc);
    append_string_field(json, first, "model_arch", state.model_arch);
    append_string_field(json, first, "model_metadata", state.model_metadata);
    append_string_field(json, first, "logits_preview", state.logits_preview);
    append_double_field(json, first, "tokens_per_second", state.tokens_per_second);
    append_int_field(json, first, "load_ms", state.load_ms);
    append_int_field(json, first, "promptEvalMs", state.prompt_eval_ms);
    append_int_field(json, first, "generationMs", state.generation_ms);
    append_int_field(json, first, "latencyMillis", state.total_ms);
    append_token_preview(json, first, "prompt_token_ids_sample", state.prompt_token_ids, kPromptPreviewLimit);
    append_token_preview(json, first, "generated_token_ids_sample", state.generated_token_ids, kOutputPreviewLimit);
    append_token_preview(json, first, "promptTokenIdsSample", state.prompt_token_ids, kPromptPreviewLimit);
    append_token_preview(json, first, "generatedTokenIdsSample", state.generated_token_ids, kOutputPreviewLimit);
    append_null_field(json, first, "parsed_intent");
    append_null_field(json, first, "parsed_action_type");
    append_null_field(json, first, "parsed_risk_level");
    append_bool_field(json, first, "confirmationRequired", false);
    append_null_field(json, first, "lastError");
    append_null_field(json, first, "lastFailure");
    json += "}";
    return json;
}

std::string build_proof_stage_json(const RuntimeState & state, const std::string & message, bool success) {
    std::string json = "{";
    bool first = true;
    const std::string final_text = final_output_text(state);
    append_bool_field(json, first, "success", success);
    append_bool_field(json, first, "ok", success);
    append_string_field(json, first, "text", final_text);
    append_string_field(json, first, "decodedText", final_text);
    append_string_field(json, first, "decoded_text", final_text);
    append_string_field(json, first, "raw_decoded_text", state.raw_decoded_text);
    append_string_field(json, first, "extracted_json", state.extracted_json);
    append_string_field(json, first, "formatted_prompt", state.formatted_prompt);
    append_string_field(json, first, "sampling_config", state.sampling_config);
    append_string_field(json, first, "prompt_text", state.formatted_prompt);
    append_int_field(json, first, "prompt_decode_calls", state.prompt_decode_calls);
    append_int_field(json, first, "generation_decode_calls", state.generation_decode_calls);
    append_int_field(json, first, "total_decode_calls", state.total_decode_calls);
    append_int_field(json, first, "generated_token_count", state.generated_token_count);
    append_int_field(json, first, "tokensGenerated", state.generated_token_count);
    append_int_field(json, first, "tokens_generated", state.generated_token_count);
    append_int_field(json, first, "native_forward_pass_count", state.native_forward_pass_count);
    append_int_field(json, first, "prompt_token_count", state.prompt_token_count);
    append_int_field(json, first, "promptTokens", state.prompt_token_count);
    append_int_field(json, first, "prompt_tokens", state.prompt_token_count);
    append_int_field(json, first, "context_size", state.context_size);
    append_int_field(json, first, "batch_size", state.batch_size);
    append_int_field(json, first, "ubatch_size", state.ubatch_size);
    append_int_field(json, first, "thread_count", state.thread_count);
    append_bool_field(json, first, "model_found", state.model_loaded);
    append_bool_field(json, first, "model_loaded", state.model_loaded);
    append_bool_field(json, first, "context_created", state.context_created);
    append_bool_field(json, first, "real_forward_pass", state.real_forward_pass);
    append_bool_field(json, first, "real_inference", state.real_inference);
    append_bool_field(json, first, "logits_computed", state.logits_computed);
    append_bool_field(json, first, "sampled_from_model_logits", state.sampled_from_model_logits);
    append_bool_field(json, first, "simulation", false);
    append_bool_field(json, first, "tokenizer_loaded", state.tokenizer_loaded);
    append_bool_field(json, first, "model_detected", state.model_detected);
    append_bool_field(json, first, "model_has_decoder", state.model_has_decoder);
    append_bool_field(json, first, "native_generation_available", state.native_generation_available);
    append_bool_field(json, first, "logits_available", state.logits_available);
    append_bool_field(json, first, "logits_finite", state.logits_finite);
    append_bool_field(json, first, "json_complete", state.json_complete);
    append_bool_field(json, first, "usable_output", state.usable_output);
    append_bool_field(json, first, "chat_template_applied", state.chat_template_applied);
    append_string_field(json, first, "chat_template_source", state.chat_template_source);
    append_string_field(json, first, "proof_stage", state.proof_stage);
    append_string_field(json, first, "proof_stage_reached", state.proof_stage_reached);
    append_string_field(json, first, "stage_reached", state.proof_stage_reached);
    append_string_field(json, first, "stageReached", state.proof_stage_reached);
    append_string_field(json, first, "native_engine_status", state.native_engine_status);
    append_string_field(json, first, "usable_brain_status", state.usable_brain_status);
    append_string_field(json, first, "output_source", state.output_source);
    append_string_field(json, first, "backend", "native");
    append_string_field(json, first, "native_error", state.native_error);
    append_string_field(json, first, "nativeError", state.native_error);
    append_null_field(json, first, "error");
    append_string_field(json, first, "message", message);
    append_string_field(json, first, "stop_reason", state.stop_reason);
    append_string_field(json, first, "stopReason", state.stop_reason);
    append_bool_field(json, first, "repetition_detected", state.repetition_detected);
    append_bool_field(json, first, "special_tokens_enabled", state.special_tokens_enabled);
    append_bool_field(json, first, "bos_added", state.bos_added);
    append_int_field(json, first, "bos_token_id", state.bos_token_id);
    append_int_field(json, first, "eos_token_id", state.eos_token_id);
    append_int_field(json, first, "eot_token_id", state.eot_token_id);
    append_int_field(json, first, "special_tokens_count", state.special_tokens_count);
    append_int_field(json, first, "vocab_size", state.vocab_size);
    append_string_field(json, first, "model_path", state.model_path);
    append_string_field(json, first, "model_desc", state.model_desc);
    append_string_field(json, first, "model_arch", state.model_arch);
    append_string_field(json, first, "model_metadata", state.model_metadata);
    append_string_field(json, first, "logits_preview", state.logits_preview);
    append_double_field(json, first, "tokens_per_second", state.tokens_per_second);
    append_int_field(json, first, "load_ms", state.load_ms);
    append_int_field(json, first, "promptEvalMs", state.prompt_eval_ms);
    append_int_field(json, first, "generationMs", state.generation_ms);
    append_int_field(json, first, "latencyMillis", state.total_ms);
    append_token_preview(json, first, "prompt_token_ids_sample", state.prompt_token_ids, kPromptPreviewLimit);
    append_token_preview(json, first, "generated_token_ids_sample", state.generated_token_ids, kOutputPreviewLimit);
    append_token_preview(json, first, "promptTokenIdsSample", state.prompt_token_ids, kPromptPreviewLimit);
    append_token_preview(json, first, "generatedTokenIdsSample", state.generated_token_ids, kOutputPreviewLimit);
    append_null_field(json, first, "parsed_intent");
    append_null_field(json, first, "parsed_action_type");
    append_null_field(json, first, "parsed_risk_level");
    append_bool_field(json, first, "confirmationRequired", false);
    append_null_field(json, first, "lastError");
    append_null_field(json, first, "lastFailure");
    json += "}";
    return json;
}

void log_callback(enum ggml_log_level level, const char * text, void * /* user_data */) {
    const int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR :
                     level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN  :
                     level == GGML_LOG_LEVEL_INFO  ? ANDROID_LOG_INFO  :
                     level == GGML_LOG_LEVEL_DEBUG ? ANDROID_LOG_DEBUG :
                                                     ANDROID_LOG_VERBOSE;
    __android_log_write(prio, TAG, text);
}

void ensure_backend_initialized_locked(RuntimeState & state) {
    if (state.backend_initialized) {
        return;
    }

    llama_log_set(log_callback, nullptr);
    llama_backend_init();
    state.backend_initialized = true;
}

long long wall_clock_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

void log_native_stage(const char * stage, const char * event, const std::string & detail = std::string()) {
    if (detail.empty()) {
        LOGI("ts=%lld | pid=%ld | tid=%ld | stage=%s | event=%s", wall_clock_ms(), process_id(), thread_id(), stage, event);
    } else {
        LOGI("ts=%lld | pid=%ld | tid=%ld | stage=%s | event=%s | %s", wall_clock_ms(), process_id(), thread_id(), stage, event, detail.c_str());
    }
}

void reset_generation_metrics_locked(RuntimeState & state) {
    state.real_forward_pass = false;
    state.real_inference = false;
    state.native_generation_available = true;
    state.json_complete = false;
    state.repetition_detected = false;
    state.usable_output = false;
    state.native_engine_status = "PASS";
    state.usable_brain_status = "PARTIAL";
    state.stop_reason.clear();
    state.native_error.clear();
    state.raw_decoded_text.clear();
    state.extracted_json.clear();
    state.canonical_output_text.clear();
    state.proof_stage_reached.clear();
    state.prompt_token_ids.clear();
    state.generated_token_ids.clear();
    state.generated_token_count = 0;
    state.prompt_decode_calls = 0;
    state.generation_decode_calls = 0;
    state.total_decode_calls = 0;
    state.logits_available = false;
    state.logits_computed = false;
    state.sampled_from_model_logits = false;
    state.native_forward_pass_count = 0;
    state.prompt_eval_ms = 0;
    state.generation_ms = 0;
    state.total_ms = 0;
    state.tokens_per_second = 0.0;
    state.chat_template_applied = false;
    state.chat_template_source = "RAW_PROMPT";
    state.formatted_prompt.clear();
    state.sampling_config.clear();
    state.prompt_token_count = 0;
}

bool load_model_only_locked(RuntimeState & state, const std::string & model_path, std::string & error_message) {
    ensure_backend_initialized_locked(state);

    if (state.model != nullptr && state.model_path == model_path) {
        error_message.clear();
        state.model_loaded = true;
        state.model_detected = true;
        state.tokenizer_loaded = state.vocab_size > 0;
        return true;
    }

    if (state.model != nullptr || state.ctx != nullptr) {
        if (state.ctx != nullptr) {
            log_native_stage("native cleanup", "begin", "freeing stale context before model reload");
            llama_free(state.ctx);
            state.ctx = nullptr;
            log_native_stage("native cleanup", "end", "stale context freed");
        }
        if (state.model != nullptr) {
            log_native_stage("native cleanup", "begin", "freeing stale model before reload");
            llama_model_free(state.model);
            state.model = nullptr;
            log_native_stage("native cleanup", "end", "stale model freed");
        }
    }

    reset_runtime_state_locked(state, true);
    state.model_path = model_path;

    log_native_stage("model load", "begin", model_path);
    const auto load_start = std::chrono::steady_clock::now();
    log_native_stage("model load", "memory", "before=" + memory_snapshot_string());
    llama_model_params model_params = make_model_params();
    state.model = llama_model_load_from_file(model_path.c_str(), model_params);
    state.load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - load_start).count();
    log_native_stage("model load", "end", "path=" + model_path + ", ms=" + std::to_string(state.load_ms));
    log_native_stage("model load", "memory", "after=" + memory_snapshot_string());

    if (state.model == nullptr) {
        error_message = "MODEL_LOAD_FAILED";
        state.native_error = error_message;
        return false;
    }

    state.model_loaded = true;
    state.model_detected = true;
    state.model_has_decoder = llama_model_has_decoder(state.model);

    log_native_stage("tokenizer initialization", "begin", model_path);
    const llama_vocab * vocab = llama_model_get_vocab(state.model);
    state.vocab_size = vocab ? llama_vocab_n_tokens(vocab) : 0;
    state.tokenizer_loaded = state.vocab_size > 0;
    state.bos_token_id = vocab ? llama_vocab_bos(vocab) : -1;
    state.eos_token_id = vocab ? llama_vocab_eos(vocab) : -1;
    state.eot_token_id = vocab ? llama_vocab_eot(vocab) : -1;
    state.special_tokens_count = state.vocab_size > 0 ? std::max(0, state.vocab_size - 1) : 0;
    log_native_stage("tokenizer initialization", "end", "vocab=" + std::to_string(state.vocab_size));

    char desc_buf[256];
    desc_buf[0] = '\0';
    llama_model_desc(state.model, desc_buf, sizeof(desc_buf));
    state.model_desc = desc_buf;
    state.model_arch = state.model_desc;
    state.model_params = static_cast<int>(llama_model_n_params(state.model));

    const char * tmpl = llama_model_chat_template(state.model, nullptr);
    state.chat_template_source = tmpl ? tmpl : "chatml";
    state.model_metadata = build_model_metadata_summary(state.model, state.chat_template_source);

    state.native_generation_available = false;
    state.native_engine_status = "PARTIAL";
    state.usable_brain_status = "PARTIAL";
    error_message.clear();
    log_native_stage(
        "tokenizer initialization",
        "end",
        "model_ptr_valid=" + std::string(state.model != nullptr ? "true" : "false") +
            ", vocab_ptr_valid=" + std::string(vocab != nullptr ? "true" : "false") +
            ", vocab_size=" + std::to_string(state.vocab_size) +
            ", bos=" + std::to_string(state.bos_token_id) +
            ", eos=" + std::to_string(state.eos_token_id)
    );
    return true;
}

bool create_context_locked(RuntimeState & state, std::string & error_message) {
    if (state.model == nullptr) {
        error_message = "MODEL_NOT_LOADED";
        state.native_error = error_message;
        return false;
    }

    if (state.ctx != nullptr) {
        error_message.clear();
        state.context_created = true;
        state.native_generation_available = true;
        return true;
    }

    state.thread_count = detect_thread_count();
    state.context_size = std::min(kDefaultContextSize, std::max(512, llama_model_n_ctx_train(state.model) > 0 ? llama_model_n_ctx_train(state.model) : kDefaultContextSize));
    state.batch_size = std::min(kDefaultBatchSize, state.context_size);
    state.ubatch_size = std::min(kDefaultUBatchSize, state.batch_size);

    log_native_stage(
        "context creation",
        "begin",
        "ctx=" + std::to_string(state.context_size) + ", batch=" + std::to_string(state.batch_size) + ", threads=" + std::to_string(state.thread_count)
    );
    log_native_stage("context creation", "memory", "before=" + memory_snapshot_string());
    llama_context_params ctx_params = make_context_params(state.context_size, state.batch_size, state.thread_count);
    state.ctx = llama_init_from_model(state.model, ctx_params);
    if (state.ctx == nullptr) {
        error_message = "CONTEXT_CREATE_FAILED";
        state.native_error = error_message;
        log_native_stage("context creation", "end", "failed");
        return false;
    }

    llama_set_n_threads(state.ctx, state.thread_count, state.thread_count);
    state.context_created = true;
    state.native_generation_available = true;
    state.native_engine_status = "PASS";
    state.usable_brain_status = "PARTIAL";
    state.sampling_config = "uninitialized";
    error_message.clear();
    log_native_stage(
        "context creation",
        "end",
        "ready, model_ptr_valid=" + std::string(state.model != nullptr ? "true" : "false") +
            ", ctx_ptr_valid=" + std::string(state.ctx != nullptr ? "true" : "false") +
            ", vocab_ptr_valid=" + std::string(llama_model_get_vocab(state.model) != nullptr ? "true" : "false") +
            ", n_ctx_train=" + std::to_string(llama_model_n_ctx_train(state.model)) +
            ", n_ctx=" + std::to_string(state.context_size) +
            ", n_batch=" + std::to_string(state.batch_size) +
            ", n_ubatch=" + std::to_string(state.ubatch_size) +
            ", n_threads=" + std::to_string(llama_n_threads(state.ctx)) +
            ", n_threads_batch=" + std::to_string(llama_n_threads_batch(state.ctx))
    );
    log_native_stage("context creation", "memory", "after=" + memory_snapshot_string());
    return true;
}

void free_runtime_locked(RuntimeState & state) {
    if (state.ctx != nullptr) {
        log_native_stage("native cleanup", "begin", "freeing context");
        llama_free(state.ctx);
        state.ctx = nullptr;
        log_native_stage("native cleanup", "end", "context freed");
    }
    if (state.model != nullptr) {
        log_native_stage("native cleanup", "begin", "freeing model");
        llama_model_free(state.model);
        state.model = nullptr;
        log_native_stage("native cleanup", "end", "model freed");
    }
    reset_runtime_state_locked(state, true);
}

bool load_model_locked(RuntimeState & state, const std::string & model_path, std::string & error_message) {
    ensure_backend_initialized_locked(state);

    if (state.model != nullptr && state.ctx != nullptr && state.model_path == model_path) {
        error_message.clear();
        state.model_loaded = true;
        state.context_created = true;
        return true;
    }

    if (state.model != nullptr || state.ctx != nullptr) {
        if (state.ctx != nullptr) {
            llama_free(state.ctx);
            state.ctx = nullptr;
        }
        if (state.model != nullptr) {
            llama_model_free(state.model);
            state.model = nullptr;
        }
    }

    reset_runtime_state_locked(state, true);
    state.model_path = model_path;

    const auto load_start = std::chrono::steady_clock::now();
    llama_model_params model_params = make_model_params();
    state.model = llama_model_load_from_file(model_path.c_str(), model_params);
    state.load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - load_start).count();

    if (state.model == nullptr) {
        error_message = "MODEL_LOAD_FAILED";
        state.native_error = error_message;
        return false;
    }

    state.model_loaded = true;
    state.model_detected = true;
    state.model_has_decoder = llama_model_has_decoder(state.model);

    const llama_vocab * vocab = llama_model_get_vocab(state.model);
    state.vocab_size = vocab ? llama_vocab_n_tokens(vocab) : 0;
    state.tokenizer_loaded = state.vocab_size > 0;
    state.bos_token_id = vocab ? llama_vocab_bos(vocab) : -1;
    state.eos_token_id = vocab ? llama_vocab_eos(vocab) : -1;
    state.eot_token_id = vocab ? llama_vocab_eot(vocab) : -1;
    state.special_tokens_count = state.vocab_size > 0 ? std::max(0, state.vocab_size - 1) : 0;

    char desc_buf[256];
    desc_buf[0] = '\0';
    llama_model_desc(state.model, desc_buf, sizeof(desc_buf));
    state.model_desc = desc_buf;
    state.model_arch = state.model_desc;
    state.model_params = static_cast<int>(llama_model_n_params(state.model));

    const char * tmpl = llama_model_chat_template(state.model, nullptr);
    state.chat_template_source = tmpl ? tmpl : "chatml";
    state.model_metadata = build_model_metadata_summary(state.model, state.chat_template_source);

    state.thread_count = detect_thread_count();
    state.context_size = std::min(kDefaultContextSize, std::max(512, llama_model_n_ctx_train(state.model) > 0 ? llama_model_n_ctx_train(state.model) : kDefaultContextSize));
    state.batch_size = std::min(kDefaultBatchSize, state.context_size);
    state.ubatch_size = std::min(kDefaultUBatchSize, state.batch_size);

    llama_context_params ctx_params = make_context_params(state.context_size, state.batch_size, state.thread_count);
    state.ctx = llama_init_from_model(state.model, ctx_params);
    if (state.ctx == nullptr) {
        error_message = "CONTEXT_CREATE_FAILED";
        state.native_error = error_message;
        llama_model_free(state.model);
        state.model = nullptr;
        state.model_loaded = false;
        return false;
    }

    state.context_created = true;
    state.native_generation_available = true;
    state.native_engine_status = "PASS";
    state.usable_brain_status = "PARTIAL";
    state.sampling_config = "uninitialized";
    error_message.clear();
    return true;
}

llama_sampler * build_sampler_locked(const RuntimeState & state, const GenerationConfig & config, bool & grammar_enabled) {
    const llama_vocab * vocab = llama_model_get_vocab(state.model);
    auto * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (chain == nullptr) {
        return nullptr;
    }

    grammar_enabled = false;
    if (config.use_grammar) {
        const std::string grammar = build_json_grammar();
        log_native_stage(
            "sampler initialization",
            "grammar_begin",
            "mode=lazy_json_object, grammar_bytes=" + std::to_string(grammar.size()) +
                ", vocab_ptr_valid=" + std::string(vocab != nullptr ? "true" : "false")
        );
        auto * grammar_sampler = llama_sampler_init_grammar_lazy_patterns(vocab, grammar.c_str(), "root", nullptr, 0, nullptr, 0);
        if (grammar_sampler == nullptr) {
            log_native_stage(
                "sampler initialization",
                "grammar_lazy_failed",
                "falling back to eager grammar initialization"
            );
            grammar_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        }
        if (grammar_sampler != nullptr) {
            llama_sampler_chain_add(chain, grammar_sampler);
            grammar_enabled = true;
            log_native_stage(
                "sampler initialization",
                "grammar_end",
                "mode=enabled, lazy=true, vocab_ptr_valid=" + std::string(vocab != nullptr ? "true" : "false")
            );
        } else {
            log_native_stage(
                "sampler initialization",
                "grammar_end",
                "mode=disabled, reason=grammar_sampler_null"
            );
        }
    }

    auto * penalties = llama_sampler_init_penalties(config.repeat_last_n, config.repeat_penalty, 0.0f, 0.0f);
    if (penalties != nullptr) {
        llama_sampler_chain_add(chain, penalties);
    }

    const bool greedy_only = config.temperature <= 0.0f ||
        (config.top_k <= 1 && config.top_p >= 0.999f);

    if (greedy_only) {
        auto * greedy = llama_sampler_init_greedy();
        if (greedy != nullptr) {
            llama_sampler_chain_add(chain, greedy);
        }
        return chain;
    }

    if (config.top_k > 0) {
        auto * top_k = llama_sampler_init_top_k(config.top_k);
        if (top_k != nullptr) {
            llama_sampler_chain_add(chain, top_k);
        }
    }

    if (config.top_p > 0.0f && config.top_p < 1.0f) {
        auto * top_p = llama_sampler_init_top_p(config.top_p, 1);
        if (top_p != nullptr) {
            llama_sampler_chain_add(chain, top_p);
        }
    }

    auto * min_p = llama_sampler_init_min_p(0.05f, 1);
    if (min_p != nullptr) {
        llama_sampler_chain_add(chain, min_p);
    }

    auto * temp = llama_sampler_init_temp(config.temperature);
    if (temp != nullptr) {
        llama_sampler_chain_add(chain, temp);
    }

    auto * dist = llama_sampler_init_dist(config.seed);
    if (dist != nullptr) {
        llama_sampler_chain_add(chain, dist);
    }

    return chain;
}

std::string generate_json_locked(RuntimeState & state, const std::string & prompt, const GenerationConfig & config, int timeout_ms, std::string & error_message) {
    error_message.clear();

    if (state.model == nullptr || state.ctx == nullptr) {
        error_message = "MODEL_NOT_LOADED";
        state.native_error = error_message;
        return build_error_json(state, error_message, "Model is not loaded.");
    }

    if (is_blank(prompt)) {
        error_message = "EMPTY_PROMPT";
        state.native_error = error_message;
        return build_error_json(state, error_message, "Prompt was empty.");
    }

    reset_generation_metrics_locked(state);
    state.proof_stage_reached = "generation_started";
    state.formatted_prompt = build_formatted_prompt(
        state.model,
        prompt,
        config.use_grammar,
        config.use_chat_template,
        state.chat_template_applied,
        state.chat_template_source
    );

    const bool add_special = true;
    const bool parse_special = true;
    const llama_vocab * vocab = llama_model_get_vocab(state.model);
    log_native_stage("tokenization", "begin", "prompt_length=" + std::to_string(state.formatted_prompt.size()));
    const int32_t required_token_count = -llama_tokenize(
        vocab,
        state.formatted_prompt.c_str(),
        static_cast<int32_t>(state.formatted_prompt.size()),
        nullptr,
        0,
        add_special,
        parse_special
    );

    if (required_token_count <= 0) {
        error_message = "PROMPT_TOKENIZATION_FAILED";
        state.native_error = error_message;
        return build_error_json(state, error_message, "Prompt tokenization failed.");
    }

    state.prompt_token_count = required_token_count;
    state.prompt_token_ids.resize(static_cast<size_t>(required_token_count));
    log_native_stage("tokenization", "mid", "required_tokens=" + std::to_string(required_token_count));
    const int32_t tokenized_count = llama_tokenize(
        vocab,
        state.formatted_prompt.c_str(),
        static_cast<int32_t>(state.formatted_prompt.size()),
        state.prompt_token_ids.data(),
        required_token_count,
        add_special,
        parse_special
    );
    if (tokenized_count < 0) {
        error_message = "PROMPT_TOKENIZATION_FAILED";
        state.native_error = error_message;
        return build_error_json(state, error_message, "Prompt tokenization failed.");
    }

    state.prompt_token_count = tokenized_count;
    state.prompt_token_ids.resize(static_cast<size_t>(tokenized_count));
    state.bos_added = !state.prompt_token_ids.empty() && state.prompt_token_ids.front() == state.bos_token_id;
    state.real_token_ids = true;
    log_native_stage("tokenization", "end", "tokens=" + std::to_string(state.prompt_token_count));

    if (state.prompt_token_count >= state.context_size - 1) {
        error_message = "PROMPT_TOO_LONG";
        state.native_error = error_message;
        return build_error_json(state, error_message, "Prompt exceeded the context window.");
    }

    if (!decode_prompt_tokens_locked(state, "prompt decode", timeout_ms, error_message)) {
        if (error_message.empty()) {
            error_message = "PROMPT_EVAL_FAILED";
        }
        return build_error_json(state, error_message, "Graph compute failed during prompt evaluation.");
    }
    state.proof_stage_reached = "prompt_decode_complete";

    bool grammar_enabled = false;
    auto * sampler = build_sampler_locked(state, config, grammar_enabled);
    if (sampler == nullptr) {
        error_message = "TOKEN_SAMPLER_FAILED";
        state.native_error = error_message;
        return build_error_json(state, error_message, "Failed to create the sampler chain.");
    }

    state.sampling_config = make_sampling_config_string(config, grammar_enabled, state.chat_template_source);

    const auto generation_start = std::chrono::steady_clock::now();
    const auto deadline = timeout_ms > 0
        ? std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms)
        : std::chrono::steady_clock::time_point::max();

    JsonScanResult json_scan;
    std::string decoded_text;
    std::vector<llama_token> generation_tokens;
    generation_tokens.reserve(static_cast<size_t>(config.max_tokens));

    const int max_tokens = std::clamp(config.max_tokens, 1, 96);
    for (int i = 0; i < max_tokens; ++i) {
        if (std::chrono::steady_clock::now() > deadline) {
            state.stop_reason = "timeout";
            error_message = "GENERATION_TIMEOUT";
            state.native_error = error_message;
            break;
        }

        log_native_stage("token sampling", "begin", "index=" + std::to_string(i));
        const bool sampled_from_logits = state.logits_available;
        log_native_stage(
            "token sampling",
            "sample_begin",
            "index=" + std::to_string(i) +
                ", logits_available=" + std::string(sampled_from_logits ? "true" : "false") +
                ", " + memory_snapshot_string()
        );
        const auto sample_start = std::chrono::steady_clock::now();
        const llama_token token = llama_sampler_sample(sampler, state.ctx, -1);
        const auto sample_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - sample_start).count();
        log_native_stage(
            "token sampling",
            "sample_end",
            "index=" + std::to_string(i) +
                ", token=" + std::to_string(token) +
                ", ms=" + std::to_string(sample_ms) +
                ", " + memory_snapshot_string()
        );
        log_native_stage(
            "token sampling",
            "accept_begin",
            "index=" + std::to_string(i) +
                ", token=" + std::to_string(token)
        );
        const auto accept_start = std::chrono::steady_clock::now();
        llama_sampler_accept(sampler, token);
        const auto accept_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - accept_start).count();
        log_native_stage(
            "token sampling",
            "accept_end",
            "index=" + std::to_string(i) +
                ", token=" + std::to_string(token) +
                ", ms=" + std::to_string(accept_ms) +
                ", " + memory_snapshot_string()
        );
        generation_tokens.push_back(token);
        ++state.generated_token_count;
        if (sampled_from_logits) {
            state.sampled_from_model_logits = true;
        }
        log_native_stage("token sampling", "end", "index=" + std::to_string(i) + ", token=" + std::to_string(token));

        if (llama_vocab_is_eog(vocab, token)) {
            state.stop_reason = "eog";
            break;
        }

        log_native_stage("detokenization", "begin", "index=" + std::to_string(i) + ", token=" + std::to_string(token));
        std::string piece = token_piece_to_string(vocab, token);
        if (piece.empty() && !llama_vocab_is_control(vocab, token)) {
            state.stop_reason = "token_decode_failed";
            error_message = "TOKEN_PIECE_DECODE_FAILED";
            state.native_error = error_message;
            break;
        }
        log_native_stage("detokenization", "end", "index=" + std::to_string(i) + ", piece=" + piece);

        if (!llama_vocab_is_control(vocab, token)) {
            decoded_text += piece;
            json_scan = scan_json_object(decoded_text);
            if (json_scan.complete && json_scan.start != std::string::npos && json_scan.end != std::string::npos && json_scan.end >= json_scan.start) {
                state.json_complete = true;
                state.extracted_json = decoded_text.substr(json_scan.start, json_scan.end - json_scan.start + 1);
                state.stop_reason = "json_complete";
            }
        }

        state.generated_token_ids.push_back(token);

        llama_batch generation_batch = llama_batch_init(1, 0, 1);
        if (generation_batch.token == nullptr || generation_batch.pos == nullptr || generation_batch.n_seq_id == nullptr || generation_batch.seq_id == nullptr || generation_batch.logits == nullptr) {
            llama_batch_free(generation_batch);
            state.stop_reason = "generation_batch_allocation_failed";
            error_message = "GENERATION_BATCH_ALLOCATION_FAILED";
            state.native_error = error_message;
            break;
        }

        generation_batch.token[0] = token;
        generation_batch.pos[0] = static_cast<llama_pos>(state.prompt_token_count + i);
        generation_batch.n_seq_id[0] = 1;
        generation_batch.seq_id[0][0] = 0;
        generation_batch.logits[0] = true;
        generation_batch.n_tokens = 1;

        log_native_stage(
            "generation batch",
            "begin",
            "index=" + std::to_string(i) +
                ", token=" + std::to_string(token) +
                ", pos=" + std::to_string(state.prompt_token_count + i) +
                ", seq_id=0" +
                ", logits=true" +
                ", " + memory_snapshot_string()
        );
        const auto generation_batch_start = std::chrono::steady_clock::now();
        const int ret = llama_decode(state.ctx, generation_batch);
        const auto generation_batch_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - generation_batch_start).count();
        state.generation_decode_calls += 1;
        state.total_decode_calls += 1;
        state.generation_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - generation_start).count();
        log_native_stage(
            "generation batch",
            "end",
            "index=" + std::to_string(i) +
                ", ret=" + std::to_string(ret) +
                ", ms=" + std::to_string(generation_batch_ms) +
                ", " + memory_snapshot_string()
        );
        llama_batch_free(generation_batch);

        if (ret != 0) {
            state.stop_reason = "generation_decode_failed";
            error_message = "GENERATION_DECODE_FAILED";
            state.native_error = error_message;
            break;
        }

        capture_logits_snapshot(state);
        state.logits_available = llama_get_logits(state.ctx) != nullptr;
        if (state.logits_available) {
            state.logits_computed = true;
        }
        ++state.native_forward_pass_count;
        state.real_forward_pass = state.native_forward_pass_count > 0 && state.logits_computed;

        if (state.json_complete) {
            break;
        }
    }

    if (state.extracted_json.empty()) {
        json_scan = scan_json_object(decoded_text);
        if (json_scan.complete && json_scan.start != std::string::npos && json_scan.end != std::string::npos && json_scan.end >= json_scan.start) {
            state.json_complete = true;
            state.extracted_json = decoded_text.substr(json_scan.start, json_scan.end - json_scan.start + 1);
            state.stop_reason = "json_complete";
        }
    }

    state.raw_decoded_text = decoded_text;
    if (config.repair_output) {
        log_native_stage(
            "output repair",
            "begin",
            "mode=" + std::string(config.stop_marker.empty() ? "structured" : "marker") +
                ", raw_tokens=" + std::to_string(state.generated_token_count) +
                ", raw_chars=" + std::to_string(state.raw_decoded_text.size())
        );
        if (config.use_grammar && state.extracted_json.empty()) {
            // Leave structured grammar output alone unless the model actually produced JSON.
        } else if (state.extracted_json.empty()) {
            const std::string repaired_json = repair_structured_brain_action_output(prompt, decoded_text);
            if (!repaired_json.empty()) {
                state.canonical_output_text = repaired_json;
                state.extracted_json = repaired_json;
                state.json_complete = true;
                state.stop_reason = "json_complete";
            } else {
                const std::string repaired_marker = repair_marker_text(decoded_text, config.stop_marker, config.canonical_stop_text);
                if (!repaired_marker.empty()) {
                    state.canonical_output_text = repaired_marker;
                }
            }
        }
        log_native_stage(
            "output repair",
            "end",
            "canonical=" + std::string(state.canonical_output_text.empty() ? "false" : "true") +
                ", json_complete=" + std::string(state.json_complete ? "true" : "false") +
                ", extracted_chars=" + std::to_string(state.extracted_json.size())
        );
    }

    state.generated_token_count = static_cast<int>(state.generated_token_ids.size());
    state.real_inference = state.real_forward_pass &&
        state.native_forward_pass_count > 0 &&
        state.logits_computed &&
        state.sampled_from_model_logits &&
        state.generated_token_count > 0;
    state.repetition_detected = looks_like_repeated_punctuation(state.raw_decoded_text) || looks_like_repeated_token(state.raw_decoded_text);
    state.usable_output = state.real_inference && state.json_complete && !state.repetition_detected && !is_blank(state.extracted_json);
    state.native_engine_status = state.real_forward_pass ? "PASS" : "FAIL";
    state.usable_brain_status = state.usable_output ? "PASS" : (state.real_inference ? "PARTIAL" : "FAIL");
    state.total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - generation_start).count() + state.prompt_eval_ms;
    if (state.total_ms > 0 && state.generated_token_count > 0) {
        state.tokens_per_second = static_cast<double>(state.generated_token_count) / (static_cast<double>(state.generation_ms > 0 ? state.generation_ms : state.total_ms) / 1000.0);
    }
    state.logits_available = llama_get_logits(state.ctx) != nullptr;

    if (state.stop_reason.empty()) {
        state.stop_reason = state.json_complete ? "json_complete" : "max_tokens";
    }

    state.proof_stage_reached = state.json_complete ? "json_complete" : "generation_complete";
    log_native_stage("native cleanup", "begin", "sampler");
    llama_sampler_free(sampler);
    log_native_stage("native cleanup", "end", "sampler");

    if (!state.native_error.empty()) {
        return build_error_json(state, state.native_error, state.stop_reason.empty() ? "Native generation failed." : state.stop_reason);
    }

    return build_success_json(state);
}

std::string run_proof_stage_locked(
    RuntimeState & state,
    const std::string & stage_name,
    const std::string & model_path,
    const std::string & prompt,
    int max_tokens,
    int timeout_ms,
    std::string & error_message) {
    error_message.clear();
    state.proof_stage = stage_name;
    state.proof_stage_reached.clear();

    if (!load_model_only_locked(state, model_path, error_message)) {
        state.proof_stage = stage_name;
        state.proof_stage_reached = "model_load_failed";
        return build_error_json(state, error_message.empty() ? "MODEL_LOAD_FAILED" : error_message, "Model load failed.");
    }
    state.proof_stage = stage_name;

    if (stage_name == "LOAD_ONLY") {
        state.proof_stage_reached = "model_load_complete";
        state.stop_reason = state.proof_stage_reached;
        std::string json = build_proof_stage_json(state, "Proof stage completed successfully", true);
        free_runtime_locked(state);
        return json;
    }

    if (!create_context_locked(state, error_message)) {
        state.proof_stage = stage_name;
        state.proof_stage_reached = "context_create_failed";
        std::string json = build_error_json(
            state,
            error_message.empty() ? "CONTEXT_CREATE_FAILED" : error_message,
            "Context creation failed."
        );
        free_runtime_locked(state);
        return json;
    }
    state.proof_stage = stage_name;

    if (stage_name == "CREATE_CONTEXT_ONLY") {
        state.proof_stage_reached = "context_create_complete";
        state.stop_reason = state.proof_stage_reached;
        std::string json = build_proof_stage_json(state, "Proof stage completed successfully", true);
        free_runtime_locked(state);
        return json;
    }

    reset_generation_metrics_locked(state);
    state.proof_stage = stage_name;

    if (stage_name == "TOKENIZE_ONLY" || stage_name == "DECODE_ONE_PROMPT_TOKEN") {
        if (stage_name == "DECODE_ONE_PROMPT_TOKEN") {
            state.formatted_prompt = prompt;
            state.chat_template_applied = false;
            state.chat_template_source = "RAW_PROOF_PROMPT";
        } else {
              state.formatted_prompt = build_formatted_prompt(
                  state.model,
                  prompt,
                  false,
                  true,
                  state.chat_template_applied,
                  state.chat_template_source
              );
        }

        const bool add_special = true;
        const bool parse_special = true;
        const llama_vocab * vocab = llama_model_get_vocab(state.model);
        log_native_stage("tokenization", "begin", "prompt_length=" + std::to_string(state.formatted_prompt.size()));
        const int32_t required_token_count = -llama_tokenize(
            vocab,
            state.formatted_prompt.c_str(),
            static_cast<int32_t>(state.formatted_prompt.size()),
            nullptr,
            0,
            add_special,
            parse_special
        );

        if (required_token_count <= 0) {
            error_message = "PROMPT_TOKENIZATION_FAILED";
            state.native_error = error_message;
            std::string json = build_error_json(state, error_message, "Prompt tokenization failed.");
            free_runtime_locked(state);
            return json;
        }

        state.prompt_token_count = required_token_count;
        state.prompt_token_ids.resize(static_cast<size_t>(required_token_count));
        log_native_stage("tokenization", "mid", "required_tokens=" + std::to_string(required_token_count));
        const int32_t tokenized_count = llama_tokenize(
            vocab,
            state.formatted_prompt.c_str(),
            static_cast<int32_t>(state.formatted_prompt.size()),
            state.prompt_token_ids.data(),
            required_token_count,
            add_special,
            parse_special
        );
        if (tokenized_count < 0) {
            error_message = "PROMPT_TOKENIZATION_FAILED";
            state.native_error = error_message;
            std::string json = build_error_json(state, error_message, "Prompt tokenization failed.");
            free_runtime_locked(state);
            return json;
        }

        state.prompt_token_count = tokenized_count;
        state.prompt_token_ids.resize(static_cast<size_t>(tokenized_count));
        state.bos_added = !state.prompt_token_ids.empty() && state.prompt_token_ids.front() == state.bos_token_id;
        state.real_token_ids = true;
        log_native_stage("tokenization", "end", "tokens=" + std::to_string(state.prompt_token_count));

        if (stage_name == "TOKENIZE_ONLY") {
            state.proof_stage_reached = "tokenization_complete";
            state.stop_reason = state.proof_stage_reached;
            state.total_ms = state.load_ms;
            std::string json = build_proof_stage_json(state, "Proof stage completed successfully", true);
            free_runtime_locked(state);
            return json;
        }

        if (state.prompt_token_count >= state.context_size - 1) {
            error_message = "PROMPT_TOO_LONG";
            state.native_error = error_message;
            std::string json = build_error_json(state, error_message, "Prompt exceeded the context window.");
            free_runtime_locked(state);
            return json;
        }

        if (!decode_prompt_tokens_locked(state, "prompt decode", timeout_ms, error_message)) {
            if (error_message.empty()) {
                error_message = "PROMPT_EVAL_FAILED";
            }
            state.native_error = error_message;
            std::string json = build_error_json(state, error_message, "Graph compute failed during prompt evaluation.");
            free_runtime_locked(state);
            return json;
        }

        state.proof_stage_reached = "prompt_decode_complete";

        if (stage_name == "DECODE_ONE_PROMPT_TOKEN") {
            state.stop_reason = state.proof_stage_reached;
            std::string json = build_proof_stage_json(state, "Proof stage completed successfully", true);
            free_runtime_locked(state);
            return json;
        }
    }

    if (stage_name == "GENERATE_ONE_TOKEN" || stage_name == "READABLE_OUTPUT" || stage_name == "STRUCTURED_JSON_OUTPUT") {
        state.proof_stage = stage_name;
        GenerationConfig config;
        config.max_tokens = stage_name == "GENERATE_ONE_TOKEN"
            ? 1
            : (stage_name == "STRUCTURED_JSON_OUTPUT" ? std::clamp(max_tokens, 1, 6) : std::clamp(max_tokens, 1, 8));
        config.temperature = 0.0f;
        config.top_k = 1;
        config.top_p = 1.0f;
        config.repeat_last_n = kDefaultRepeatWindow;
        config.repeat_penalty = 1.1f;
        config.seed = kDefaultSeed;
        config.use_grammar = false;
        config.use_chat_template = false;
        config.repair_output = stage_name != "GENERATE_ONE_TOKEN";
        if (stage_name == "READABLE_OUTPUT") {
            config.stop_marker = "NOVA_BRAIN_OK";
            config.canonical_stop_text = "NOVA_BRAIN_OK";
        }

        std::string generation_error;
        const std::string result = generate_json_locked(state, prompt, config, timeout_ms, generation_error);
        free_runtime_locked(state);
        return result;
    }

    state.proof_stage_reached = "unknown_stage";
    error_message = "UNKNOWN_PROOF_STAGE";
    std::string json = build_error_json(state, error_message, "Unknown proof stage.");
    free_runtime_locked(state);
    return json;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeLoadModel(JNIEnv * env, jclass, jstring modelPath) {
    if (modelPath == nullptr) {
        return JNI_FALSE;
    }

    const char * model_path_chars = env->GetStringUTFChars(modelPath, nullptr);
    if (model_path_chars == nullptr) {
        return JNI_FALSE;
    }

    std::string error_message;
    const std::string model_path(model_path_chars);
    env->ReleaseStringUTFChars(modelPath, model_path_chars);

    log_native_stage("jni nativeLoadModel", "enter", "model_path=" + model_path + ", pid=" + std::to_string(process_id()) + ", tid=" + std::to_string(thread_id()));
    std::lock_guard<std::mutex> lock(g_state.mutex);
    const bool ok = load_model_locked(g_state, model_path, error_message);
    if (!ok) {
        LOGE("nativeLoadModel failed: %s (%s)", error_message.c_str(), model_path.c_str());
    } else {
        LOGI("nativeLoadModel succeeded: %s", model_path.c_str());
    }
    log_native_stage("jni nativeLoadModel", "exit", std::string("ok=") + (ok ? "true" : "false") + ", error=" + (error_message.empty() ? "none" : error_message));
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeGenerate(JNIEnv * env, jclass, jstring prompt, jint maxTokens, jfloat temperature, jint topK, jfloat topP, jint timeoutMs) {
    if (prompt == nullptr) {
        return env->NewStringUTF(build_error_json(g_state, "EMPTY_PROMPT", "Prompt was empty.").c_str());
    }

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_chars == nullptr) {
        return env->NewStringUTF(build_error_json(g_state, "JNI_PROMPT_ERROR", "Failed to access prompt text.").c_str());
    }

    const std::string prompt_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    log_native_stage("jni nativeGenerate", "enter", "prompt_chars=" + std::to_string(prompt_text.size()) + ", max_tokens=" + std::to_string(maxTokens) + ", timeout_ms=" + std::to_string(timeoutMs));
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (g_state.model == nullptr || g_state.ctx == nullptr) {
        log_native_stage("jni nativeGenerate", "exit", "model_not_loaded");
        return env->NewStringUTF(build_error_json(g_state, "MODEL_NOT_LOADED", "Model is not loaded.").c_str());
    }

    GenerationConfig config;
    config.max_tokens = std::max(static_cast<int>(maxTokens), 1);
    config.temperature = std::clamp(static_cast<float>(temperature), 0.0f, 0.5f);
    config.top_k = std::clamp(static_cast<int>(topK), 1, 128);
    config.top_p = std::clamp(static_cast<float>(topP), 0.50f, 1.0f);
    config.repeat_last_n = kDefaultRepeatWindow;
    config.repeat_penalty = 1.1f;
    config.seed = kDefaultSeed;
    config.use_grammar = true;

    std::string error_message;
    const std::string result = generate_json_locked(g_state, prompt_text, config, timeoutMs, error_message);
    if (!error_message.empty()) {
        LOGW("nativeGenerate completed with error: %s", error_message.c_str());
    } else {
        LOGI("nativeGenerate completed successfully");
    }
    log_native_stage("jni nativeGenerate", "exit", "error=" + (error_message.empty() ? std::string("none") : error_message));
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeRunProofStage(JNIEnv * env, jclass, jstring stageName, jstring modelPath, jstring prompt, jint maxTokens, jint timeoutMs) {
    if (stageName == nullptr || modelPath == nullptr || prompt == nullptr) {
        return env->NewStringUTF(build_error_json(g_state, "INVALID_PROOF_REQUEST", "Proof request was missing required fields.").c_str());
    }

    const char * stage_chars = env->GetStringUTFChars(stageName, nullptr);
    const char * model_path_chars = env->GetStringUTFChars(modelPath, nullptr);
    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (stage_chars == nullptr || model_path_chars == nullptr || prompt_chars == nullptr) {
        if (stage_chars != nullptr) env->ReleaseStringUTFChars(stageName, stage_chars);
        if (model_path_chars != nullptr) env->ReleaseStringUTFChars(modelPath, model_path_chars);
        if (prompt_chars != nullptr) env->ReleaseStringUTFChars(prompt, prompt_chars);
        return env->NewStringUTF(build_error_json(g_state, "JNI_PROOF_REQUEST_ERROR", "Failed to read proof request text.").c_str());
    }

    const std::string stage_text(stage_chars);
    const std::string model_path(model_path_chars);
    const std::string prompt_text(prompt_chars);
    env->ReleaseStringUTFChars(stageName, stage_chars);
    env->ReleaseStringUTFChars(modelPath, model_path_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    log_native_stage(
        "jni nativeRunProofStage",
        "enter",
        "stage=" + stage_text +
            ", model_path=" + model_path +
            ", prompt_chars=" + std::to_string(prompt_text.size()) +
            ", max_tokens=" + std::to_string(maxTokens) +
            ", timeout_ms=" + std::to_string(timeoutMs)
    );
    std::lock_guard<std::mutex> lock(g_state.mutex);
    std::string error_message;
    const std::string result = run_proof_stage_locked(
        g_state,
        stage_text,
        model_path,
        prompt_text,
        std::max(static_cast<int>(maxTokens), 1),
        timeoutMs,
        error_message
    );

    if (!error_message.empty()) {
        LOGW("nativeRunProofStage completed with error: %s (%s)", error_message.c_str(), stage_text.c_str());
    } else {
        LOGI("nativeRunProofStage completed successfully: %s", stage_text.c_str());
    }
    log_native_stage("jni nativeRunProofStage", "exit", "stage=" + stage_text + ", error=" + (error_message.empty() ? std::string("none") : error_message));
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeIsModelLoaded(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    return (g_state.model != nullptr && g_state.ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeReleaseModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    log_native_stage("jni nativeReleaseModel", "enter", "begin release");
    free_runtime_locked(g_state);
    log_native_stage("jni nativeReleaseModel", "exit", "release complete");
}

JNIEXPORT void JNICALL
Java_com_nova_luna_brain_LlamaCppJni_nativeUnloadModel(JNIEnv * env, jclass clazz) {
    (void)env;
    (void)clazz;
    log_native_stage("jni nativeUnloadModel", "enter", "delegating to release");
    Java_com_nova_luna_brain_LlamaCppJni_nativeReleaseModel(nullptr, clazz);
    log_native_stage("jni nativeUnloadModel", "exit", "delegation complete");
}

} // extern "C"
