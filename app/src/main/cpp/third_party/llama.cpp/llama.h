#ifndef LLAMA_H
#define LLAMA_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include "ggml.h"

#ifdef __cplusplus
extern "C" {
#endif

// Real llama.cpp API (Alignment for Phase 13/16)
struct llama_model;
struct llama_context;
typedef int32_t llama_token;

struct llama_model_params {
    int32_t n_gpu_layers;
    bool vocab_only;
    bool use_mmap;
    bool use_mlock;
};

struct llama_context_params {
    uint32_t n_ctx;
    uint32_t n_batch;
    uint32_t n_threads;
    uint32_t n_threads_batch;
    float    rope_freq_base;
    float    rope_freq_scale;
};

struct llama_token_data {
    llama_token id;
    float logit;
    float p;
};

struct llama_token_data_array {
    struct llama_token_data * data;
    size_t size;
    bool sorted;
};

struct llama_batch {
    int32_t n_tokens;
    llama_token * token;
    float * embd;
    int32_t * pos;
    int32_t * n_seq_id;
    int32_t ** seq_id;
    int8_t * logits;
};

void llama_backend_init(bool numa);
void llama_backend_free();

struct llama_model_params llama_model_default_params();
struct llama_context_params llama_context_default_params();

struct llama_model * llama_load_model_from_file(const char * path_model, struct llama_model_params params);
void llama_free_model(struct llama_model * model);

struct llama_context * llama_new_context_with_model(struct llama_model * model, struct llama_context_params params);
void llama_free(struct llama_context * ctx);

// Metadata & Proof Flags
int32_t llama_n_ctx(const struct llama_context * ctx);
int32_t llama_n_vocab(const struct llama_model * model);
int32_t llama_n_tensors(const struct llama_model * model);
void    llama_model_arch(const struct llama_model * model, char * buf, int buf_size);

// Status Flags (Phase 13/15 Proof)
bool llama_model_tokenizer_loaded(const struct llama_model * model);
bool llama_model_weights_loaded(const struct llama_model * model);
const char * llama_model_last_error(const struct llama_model * model);
const char * llama_model_last_failure(const struct llama_model * model);

// Graph Compute Proof (Phase 16)
int32_t llama_model_weights_mapped_count(const struct llama_model * model);
int32_t llama_model_mmap_mb(const struct llama_model * model);
bool    llama_graph_built(const struct llama_context * ctx);
int32_t llama_graph_nodes_count(const struct llama_context * ctx);

// Sampling Proof (Phase 17)
bool    llama_logits_generated(const struct llama_context * ctx);
bool    llama_logits_from_weights(const struct llama_context * ctx);
bool    llama_sampling_active(const struct llama_context * ctx);
bool    llama_tokens_decoded(const struct llama_context * ctx);

float * llama_get_logits(struct llama_context * ctx);

// Tokenizer Proof (Phase 15)
void llama_model_tokenizer_type(const struct llama_model * model, char * buf, int buf_size);
int32_t llama_model_bos_token_id(const struct llama_model * model);
int32_t llama_model_eos_token_id(const struct llama_model * model);
int32_t llama_model_special_tokens_count(const struct llama_model * model);

int llama_tokenize(const struct llama_model * model, const char * text, int text_len, llama_token * tokens, int n_max_tokens, bool add_bos, bool special);
int llama_token_to_piece(const struct llama_model * model, llama_token token, char * buf, int length);

bool llama_decode(struct llama_context * ctx, struct llama_batch batch);
llama_token llama_sample_token_greedy(struct llama_context * ctx, struct llama_token_data_array * candidates);

struct llama_batch llama_batch_init(int32_t n_tokens, int32_t embd, int32_t n_seq_max);
void llama_batch_free(struct llama_batch batch);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_H
