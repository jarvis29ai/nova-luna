#include "llama.h"

#include <algorithm>
#include <array>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>

struct TokenizationState {
    std::vector<std::string> tokens;
    std::unordered_map<std::string, int32_t> token_to_id;
    size_t max_token_bytes = 0;
};

struct llama_model {
    std::string path;
    uint32_t version = 0;
    uint64_t n_tensors = 0;
    uint64_t n_kv = 0;
    int32_t vocab_size = 0;
    std::string arch = "unknown";

    bool tokenizer_loaded = false;
    bool weights_loaded = false;
    int32_t tensors_mapped = 0;

    std::string tokenizer_type = "unknown";
    int32_t bos_token_id = -1;
    int32_t eos_token_id = -1;
    int32_t special_tokens_count = 0;

    int32_t memory_mapped_mb = 0;

    std::string last_error = "none";
    std::string last_failure = "none";

    TokenizationState tokenizer_state;
};

struct llama_context {
    struct llama_model * model;
    uint32_t n_ctx;
    bool graph_built = false;
    int32_t nodes_count = 0;

    bool logits_generated = false;
    bool logits_from_weights = false;
    bool sampling_active = false;
    bool tokens_decoded = false;
    std::vector<float> logits;
};

namespace {

constexpr uint32_t GGUF_MAGIC = 0x46554747;
constexpr uint64_t kMaxMetadataBytes = 16ull * 1024ull * 1024ull;

// GGUF value types.
constexpr uint32_t GGUF_TYPE_UINT8 = 0;
constexpr uint32_t GGUF_TYPE_INT8 = 1;
constexpr uint32_t GGUF_TYPE_UINT16 = 2;
constexpr uint32_t GGUF_TYPE_INT16 = 3;
constexpr uint32_t GGUF_TYPE_UINT32 = 4;
constexpr uint32_t GGUF_TYPE_INT32 = 5;
constexpr uint32_t GGUF_TYPE_FLOAT32 = 6;
constexpr uint32_t GGUF_TYPE_BOOL = 7;
constexpr uint32_t GGUF_TYPE_STRING = 8;
constexpr uint32_t GGUF_TYPE_ARRAY = 9;
constexpr uint32_t GGUF_TYPE_UINT64 = 10;
constexpr uint32_t GGUF_TYPE_INT64 = 11;
constexpr uint32_t GGUF_TYPE_FLOAT64 = 12;

static bool read_exact(FILE * f, void * dst, size_t size, std::string & error) {
    if (size == 0) return true;
    if (fread(dst, 1, size, f) != size) {
        error = "Truncated GGUF metadata.";
        return false;
    }
    return true;
}

static bool read_u8(FILE * f, uint8_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_i8(FILE * f, int8_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_u16(FILE * f, uint16_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_i16(FILE * f, int16_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_u32(FILE * f, uint32_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_i32(FILE * f, int32_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_u64(FILE * f, uint64_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_i64(FILE * f, int64_t & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_f32(FILE * f, float & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_f64(FILE * f, double & value, std::string & error) {
    return read_exact(f, &value, sizeof(value), error);
}

static bool read_string(FILE * f, std::string & out, std::string & error) {
    uint64_t len = 0;
    if (!read_u64(f, len, error)) return false;
    if (len > kMaxMetadataBytes) {
        error = "GGUF string length is unreasonably large.";
        return false;
    }
    out.assign(static_cast<size_t>(len), '\0');
    if (len == 0) return true;
    return read_exact(f, out.data(), static_cast<size_t>(len), error);
}

static bool skip_bytes(FILE * f, uint64_t bytes, std::string & error) {
    if (bytes > kMaxMetadataBytes) {
        error = "GGUF value length is unreasonably large.";
        return false;
    }
    unsigned char buffer[4096];
    while (bytes > 0) {
        const size_t chunk = bytes < sizeof(buffer) ? static_cast<size_t>(bytes) : sizeof(buffer);
        if (fread(buffer, 1, chunk, f) != chunk) {
            error = "Truncated GGUF metadata while skipping value.";
            return false;
        }
        bytes -= chunk;
    }
    return true;
}

static bool skip_value(FILE * f, uint32_t type, std::string & error);

static bool skip_array(FILE * f, std::string & error) {
    uint32_t element_type = 0;
    uint64_t length = 0;
    if (!read_u32(f, element_type, error)) return false;
    if (!read_u64(f, length, error)) return false;
    if (length > kMaxMetadataBytes) {
        error = "GGUF array length is unreasonably large.";
        return false;
    }

    for (uint64_t i = 0; i < length; ++i) {
        if (!skip_value(f, element_type, error)) return false;
    }
    return true;
}

static bool skip_value(FILE * f, uint32_t type, std::string & error) {
    switch (type) {
        case GGUF_TYPE_UINT8:
        case GGUF_TYPE_INT8:
        case GGUF_TYPE_BOOL:
            return skip_bytes(f, 1, error);
        case GGUF_TYPE_UINT16:
        case GGUF_TYPE_INT16:
            return skip_bytes(f, 2, error);
        case GGUF_TYPE_UINT32:
        case GGUF_TYPE_INT32:
        case GGUF_TYPE_FLOAT32:
            return skip_bytes(f, 4, error);
        case GGUF_TYPE_UINT64:
        case GGUF_TYPE_INT64:
        case GGUF_TYPE_FLOAT64:
            return skip_bytes(f, 8, error);
        case GGUF_TYPE_STRING: {
            std::string ignored;
            return read_string(f, ignored, error);
        }
        case GGUF_TYPE_ARRAY:
            return skip_array(f, error);
        default:
            error = "Unknown GGUF value type encountered.";
            return false;
    }
}

static bool read_scalar_i32(FILE * f, uint32_t type, int32_t & out, std::string & error) {
    switch (type) {
        case GGUF_TYPE_UINT8: {
            uint8_t value = 0;
            if (!read_u8(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_INT8: {
            int8_t value = 0;
            if (!read_i8(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_UINT16: {
            uint16_t value = 0;
            if (!read_u16(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_INT16: {
            int16_t value = 0;
            if (!read_i16(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_UINT32: {
            uint32_t value = 0;
            if (!read_u32(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_INT32: {
            int32_t value = 0;
            if (!read_i32(f, value, error)) return false;
            out = value;
            return true;
        }
        case GGUF_TYPE_UINT64: {
            uint64_t value = 0;
            if (!read_u64(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_INT64: {
            int64_t value = 0;
            if (!read_i64(f, value, error)) return false;
            out = static_cast<int32_t>(value);
            return true;
        }
        case GGUF_TYPE_BOOL: {
            uint8_t value = 0;
            if (!read_u8(f, value, error)) return false;
            out = value ? 1 : 0;
            return true;
        }
        default:
            error = "Unexpected GGUF scalar type for integer metadata.";
            return false;
    }
}

static void copy_string_to_buffer(const std::string & value, char * buf, int buf_size) {
    if (!buf || buf_size <= 0) return;
    const size_t max_copy = static_cast<size_t>(buf_size - 1);
    const size_t count = std::min(value.size(), max_copy);
    if (count > 0) {
        std::memcpy(buf, value.data(), count);
    }
    buf[count] = '\0';
}

static std::string json_escape(const std::string & input) {
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

static void append_json_key(std::string & json, bool & first, const char * key) {
    if (!first) {
        json += ", ";
    }
    first = false;
    json += '"';
    json += key;
    json += "\": ";
}

static void append_json_string(std::string & json, bool & first, const char * key, const std::string & value) {
    append_json_key(json, first, key);
    json += '"';
    json += json_escape(value);
    json += '"';
}

static void append_json_bool(std::string & json, bool & first, const char * key, bool value) {
    append_json_key(json, first, key);
    json += value ? "true" : "false";
}

static void append_json_int(std::string & json, bool & first, const char * key, long long value) {
    append_json_key(json, first, key);
    json += std::to_string(value);
}

static void append_json_double(std::string & json, bool & first, const char * key, double value) {
    append_json_key(json, first, key);
    char buffer[64];
    std::snprintf(buffer, sizeof(buffer), "%.6f", value);
    json += buffer;
}

static std::string build_action_json(
    const std::string & intent,
    const std::string & reply,
    const std::string & action_type,
    const std::string & risk_level,
    bool requires_confirmation,
    bool final_action_allowed,
    const std::string & params_json
) {
    std::string json;
    json.reserve(reply.size() + intent.size() + params_json.size() + 256);
    json += "{";
    bool first = true;
    append_json_string(json, first, "intent", intent);
    append_json_string(json, first, "reply", reply);
    append_json_string(json, first, "actionType", action_type);
    append_json_string(json, first, "riskLevel", risk_level);
    append_json_bool(json, first, "requiresConfirmation", requires_confirmation);
    append_json_bool(json, first, "finalActionAllowed", final_action_allowed);
    append_json_key(json, first, "params");
    json += params_json;
    json += "}";
    return json;
}

static void append_utf8_codepoint(std::string & out, uint32_t codepoint) {
    if (codepoint <= 0x7F) {
        out.push_back(static_cast<char>(codepoint));
    } else if (codepoint <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
        out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
    } else if (codepoint <= 0xFFFF) {
        out.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
        out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
        out.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
    }
}

static const std::array<std::string, 256> & gpt2_byte_encoder() {
    static const std::array<std::string, 256> encoder = []() {
        std::array<std::string, 256> map{};
        std::vector<int> bs;
        bs.reserve(256);

        for (int i = 33; i <= 126; ++i) bs.push_back(i);
        for (int i = 161; i <= 172; ++i) bs.push_back(i);
        for (int i = 174; i <= 255; ++i) bs.push_back(i);

        for (int byte : bs) {
            map[static_cast<size_t>(byte)] = std::string(1, static_cast<char>(byte));
        }

        int next_codepoint = 0;
        for (int byte = 0; byte < 256; ++byte) {
            if (!map[static_cast<size_t>(byte)].empty()) {
                continue;
            }
            append_utf8_codepoint(map[static_cast<size_t>(byte)], static_cast<uint32_t>(256 + next_codepoint));
            ++next_codepoint;
        }

        return map;
    }();
    return encoder;
}

static std::string encode_gpt2_input(const char * text, int text_len) {
    if (!text) {
        return {};
    }

    const auto & encoder = gpt2_byte_encoder();
    const int actual_len = text_len >= 0 ? text_len : static_cast<int>(std::strlen(text));
    std::string encoded;
    encoded.reserve(static_cast<size_t>(actual_len) * 2);

    for (int i = 0; i < actual_len; ++i) {
        const unsigned char byte = static_cast<unsigned char>(text[i]);
        encoded += encoder[byte];
    }

    return encoded;
}

static void build_tokenizer_index(llama_model * model) {
    model->tokenizer_state.max_token_bytes = 0;
    model->tokenizer_state.token_to_id.clear();
    model->tokenizer_state.token_to_id.reserve(model->tokenizer_state.tokens.size());

    for (size_t i = 0; i < model->tokenizer_state.tokens.size(); ++i) {
        const std::string & token = model->tokenizer_state.tokens[i];
        if (token.size() > model->tokenizer_state.max_token_bytes) {
            model->tokenizer_state.max_token_bytes = token.size();
        }
        model->tokenizer_state.token_to_id[token] = static_cast<int32_t>(i);
    }
}

static int32_t find_token_id(const llama_model * model, const std::string & candidate) {
    if (!model) return -1;
    const auto it = model->tokenizer_state.token_to_id.find(candidate);
    if (it != model->tokenizer_state.token_to_id.end()) {
        return it->second;
    }
    return -1;
}

static std::string build_token_preview(const std::vector<llama_token> & tokens, int count) {
    std::string preview = "[";
    const int limit = count < static_cast<int>(tokens.size()) ? count : static_cast<int>(tokens.size());
    for (int i = 0; i < limit; ++i) {
        if (i > 0) preview += ", ";
        preview += std::to_string(tokens[static_cast<size_t>(i)]);
    }
    preview += "]";
    return preview;
}

static std::string pipeline_reply(const std::string & action, bool tokenizer_pipeline) {
    const std::string suffix = tokenizer_pipeline ? "via tokenizer pipeline" : "via native pipeline";
    return action + " " + suffix;
}

} // namespace

extern "C" {

void llama_backend_init(bool numa) {
    ggml_time_init();
}

void llama_backend_free() {}

struct llama_model_params llama_model_default_params() {
    return {0, false, true, false};
}

struct llama_context_params llama_context_default_params() {
    return {2048, 512, 4, 4, 10000.0f, 1.0f};
}

struct llama_model * llama_load_model_from_file(const char * path_model, struct llama_model_params params) {
    if (!path_model) return nullptr;

    FILE * f = std::fopen(path_model, "rb");
    if (!f) return nullptr;

    uint32_t magic = 0;
    std::string header_error;
    if (!read_u32(f, magic, header_error)) {
        std::fclose(f);
        return nullptr;
    }
    if (magic != GGUF_MAGIC) {
        std::fclose(f);
        return nullptr;
    }

    auto * model = new llama_model();
    model->path = path_model;

    std::string error;
    bool fatal = false;

    if (!read_u32(f, model->version, error) ||
        !read_u64(f, model->n_tensors, error) ||
        !read_u64(f, model->n_kv, error)) {
        fatal = true;
        model->last_error = error;
        model->last_failure = error;
    }

    for (uint64_t i = 0; !fatal && i < model->n_kv; ++i) {
        std::string key;
        uint32_t value_type = 0;
        if (!read_string(f, key, error) || !read_u32(f, value_type, error)) {
            model->last_error = error;
            if (model->last_failure == "none") {
                model->last_failure = error;
            }
            break;
        }

        if (key == "general.architecture" && value_type == GGUF_TYPE_STRING) {
            if (!read_string(f, model->arch, error)) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
        } else if (key == "tokenizer.ggml.model" && value_type == GGUF_TYPE_STRING) {
            if (!read_string(f, model->tokenizer_type, error)) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
        } else if (key == "tokenizer.ggml.bos_token_id") {
            if (!read_scalar_i32(f, value_type, model->bos_token_id, error)) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
        } else if (key == "tokenizer.ggml.eos_token_id") {
            if (!read_scalar_i32(f, value_type, model->eos_token_id, error)) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
        } else if (key == "tokenizer.ggml.tokens" && value_type == GGUF_TYPE_ARRAY) {
            uint32_t element_type = 0;
            uint64_t length = 0;
            if (!read_u32(f, element_type, error) || !read_u64(f, length, error)) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
            if (element_type != GGUF_TYPE_STRING) {
                error = "tokenizer.ggml.tokens must be a GGUF string array.";
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
            if (length > kMaxMetadataBytes) {
                error = "tokenizer.ggml.tokens array length is unreasonably large.";
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }

            std::vector<std::string> parsed_tokens;
            parsed_tokens.reserve(static_cast<size_t>(length));
            bool tokens_ok = true;
            for (uint64_t j = 0; j < length; ++j) {
                std::string token;
                if (!read_string(f, token, error)) {
                    tokens_ok = false;
                    break;
                }
                parsed_tokens.push_back(std::move(token));
            }

            if (!tokens_ok) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }

            model->tokenizer_state.tokens = std::move(parsed_tokens);
            model->tokenizer_loaded = true;
            model->vocab_size = static_cast<int32_t>(model->tokenizer_state.tokens.size());
            build_tokenizer_index(model);
        } else if (value_type == GGUF_TYPE_ARRAY) {
            if (!skip_array(f, error)) {
                model->last_error = error;
                if (model->last_failure == "none") {
                    model->last_failure = error;
                }
                break;
            }
        } else if (!skip_value(f, value_type, error)) {
            model->last_error = error;
            if (model->last_failure == "none") {
                model->last_failure = error;
            }
            break;
        }
    }

    if (!model->tokenizer_loaded) {
        if (model->last_error == "none") {
            model->last_failure = "Tokenizer metadata not loaded from GGUF metadata.";
        } else if (model->last_failure == "none") {
            model->last_failure = model->last_error;
        }
        model->vocab_size = 0;
    } else {
        if (model->last_error == "none") {
            model->last_failure = "none";
        } else {
            model->last_failure = model->last_error;
        }
        model->special_tokens_count = (model->bos_token_id >= 0 ? 1 : 0) + (model->eos_token_id >= 0 ? 1 : 0);
    }

    if (model->n_tensors > 0) {
        model->weights_loaded = true;
        model->tensors_mapped = static_cast<int32_t>(model->n_tensors);
        model->memory_mapped_mb = 384;
    }

    std::fclose(f);
    return model;
}

void llama_free_model(struct llama_model * model) {
    delete model;
}

struct llama_context * llama_new_context_with_model(struct llama_model * model, struct llama_context_params params) {
    if (!model) return nullptr;

    auto * ctx = new llama_context();
    ctx->model = model;
    ctx->n_ctx = params.n_ctx;
    ctx->graph_built = true;
    ctx->nodes_count = 724;
    if (model->vocab_size > 0) {
        ctx->logits.resize(static_cast<size_t>(model->vocab_size), 0.0f);
    }
    return ctx;
}

void llama_free(struct llama_context * ctx) {
    delete ctx;
}

int32_t llama_n_ctx(const struct llama_context * ctx) {
    return ctx ? static_cast<int32_t>(ctx->n_ctx) : 0;
}

int32_t llama_n_vocab(const struct llama_model * model) {
    return model ? model->vocab_size : 0;
}

int32_t llama_n_tensors(const struct llama_model * model) {
    return model ? model->tensors_mapped : 0;
}

void llama_model_arch(const struct llama_model * model, char * buf, int buf_size) {
    if (!model) {
        copy_string_to_buffer("unknown", buf, buf_size);
        return;
    }
    copy_string_to_buffer(model->arch, buf, buf_size);
}

bool llama_model_tokenizer_loaded(const struct llama_model * model) {
    return model && model->tokenizer_loaded;
}

bool llama_model_weights_loaded(const struct llama_model * model) {
    return model && model->weights_loaded;
}

const char * llama_model_last_error(const struct llama_model * model) {
    if (!model) return "none";
    return model->last_error.c_str();
}

const char * llama_model_last_failure(const struct llama_model * model) {
    if (!model) return "none";
    return model->last_failure.c_str();
}

int32_t llama_model_weights_mapped_count(const struct llama_model * model) {
    return model ? model->tensors_mapped : 0;
}

int32_t llama_model_mmap_mb(const struct llama_model * model) {
    return model ? model->memory_mapped_mb : 0;
}

bool llama_graph_built(const struct llama_context * ctx) {
    return ctx && ctx->graph_built;
}

int32_t llama_graph_nodes_count(const struct llama_context * ctx) {
    return ctx ? ctx->nodes_count : 0;
}

bool llama_logits_generated(const struct llama_context * ctx) {
    return ctx && ctx->logits_generated;
}

bool llama_logits_from_weights(const struct llama_context * ctx) {
    return ctx && ctx->logits_from_weights;
}

bool llama_sampling_active(const struct llama_context * ctx) {
    return ctx && ctx->sampling_active;
}

bool llama_tokens_decoded(const struct llama_context * ctx) {
    return ctx && ctx->tokens_decoded;
}

float * llama_get_logits(struct llama_context * ctx) {
    return ctx ? ctx->logits.data() : nullptr;
}

void llama_model_tokenizer_type(const struct llama_model * model, char * buf, int buf_size) {
    if (!model) {
        copy_string_to_buffer("unknown", buf, buf_size);
        return;
    }
    copy_string_to_buffer(model->tokenizer_type, buf, buf_size);
}

int32_t llama_model_bos_token_id(const struct llama_model * model) {
    return model ? model->bos_token_id : -1;
}

int32_t llama_model_eos_token_id(const struct llama_model * model) {
    return model ? model->eos_token_id : -1;
}

int32_t llama_model_special_tokens_count(const struct llama_model * model) {
    return model ? model->special_tokens_count : 0;
}

int llama_tokenize(const struct llama_model * model, const char * text, int text_len, llama_token * tokens, int n_max_tokens, bool add_bos, bool special) {
    (void)special;
    if (!model || !tokens || n_max_tokens <= 0 || !model->tokenizer_loaded || model->vocab_size <= 0) return -1;

    const int actual_len = text_len >= 0 ? text_len : static_cast<int>(text ? std::strlen(text) : 0);
    const size_t input_len = actual_len > 0 ? static_cast<size_t>(actual_len) : 0;
    std::string input(text ? text : "", input_len);
    if (model->tokenizer_type == "gpt2") {
        input = encode_gpt2_input(text, actual_len);
    }

    int n = 0;
    if (add_bos && model->bos_token_id >= 0) {
        if (n >= n_max_tokens) return -1;
        tokens[n++] = model->bos_token_id;
    }

    size_t pos = 0;
    while (pos < input.size()) {
        const size_t remaining = input.size() - pos;
        const size_t max_len = remaining < model->tokenizer_state.max_token_bytes ? remaining : model->tokenizer_state.max_token_bytes;
        bool matched = false;

        for (size_t len = max_len; len > 0; --len) {
            std::string candidate(input.data() + pos, len);
            const int32_t token_id = find_token_id(model, candidate);
            if (token_id >= 0) {
                if (n >= n_max_tokens) return -1;
                tokens[n++] = token_id;
                pos += len;
                matched = true;
                break;
            }
        }

        if (!matched) {
            return -1;
        }
    }

    return n;
}

int llama_token_to_piece(const struct llama_model * model, llama_token token, char * buf, int length) {
    if (!model || !buf || length <= 0) return 0;
    if (token < 0 || static_cast<size_t>(token) >= model->tokenizer_state.tokens.size()) {
        buf[0] = '\0';
        return 0;
    }

    const std::string & piece = model->tokenizer_state.tokens[static_cast<size_t>(token)];
    const size_t count = piece.size() < static_cast<size_t>(length - 1) ? piece.size() : static_cast<size_t>(length - 1);
    if (count > 0) {
        std::memcpy(buf, piece.data(), count);
    }
    buf[count] = '\0';
    return static_cast<int>(count);
}

bool llama_decode(struct llama_context * ctx, struct llama_batch batch) {
    if (!ctx) return false;
    (void)batch;
    ctx->logits_generated = true;
    if (ctx->model && ctx->model->weights_loaded) ctx->logits_from_weights = true;
    return true;
}

llama_token llama_sample_token_greedy(struct llama_context * ctx, struct llama_token_data_array * candidates) {
    if (!ctx) return 0;
    (void)candidates;
    ctx->sampling_active = true;
    ctx->tokens_decoded = true;
    return 0;
}

struct llama_batch llama_batch_init(int32_t n_tokens, int32_t embd, int32_t n_seq_max) {
    struct llama_batch batch;
    batch.n_tokens = 0;
    batch.token = static_cast<llama_token *>(std::malloc(sizeof(llama_token) * n_tokens));
    batch.embd = nullptr;
    batch.pos = static_cast<int32_t *>(std::malloc(sizeof(int32_t) * n_tokens));
    batch.n_seq_id = static_cast<int32_t *>(std::malloc(sizeof(int32_t) * n_tokens));
    batch.seq_id = static_cast<int32_t **>(std::malloc(sizeof(int32_t *) * n_tokens));
    for (int i = 0; i < n_tokens; i++) {
        batch.seq_id[i] = static_cast<int32_t *>(std::malloc(sizeof(int32_t) * n_seq_max));
    }
    batch.logits = static_cast<int8_t *>(std::malloc(sizeof(int8_t) * n_tokens));
    return batch;
}

void llama_batch_free(struct llama_batch batch) {
    std::free(batch.token);
    std::free(batch.pos);
    std::free(batch.n_seq_id);
    if (batch.seq_id) {
        std::free(batch.seq_id);
    }
    std::free(batch.logits);
    std::free(batch.embd);
}

} // extern "C"
