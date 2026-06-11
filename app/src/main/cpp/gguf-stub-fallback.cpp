#include "llama.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

void llama_backend_init(bool numa) {}
void llama_backend_free() {}

struct llama_model_params llama_model_default_params() {
    return {0, false, true, false};
}

struct llama_context_params llama_context_default_params() {
    return {2048, 512, 4, 4, 10000.0f, 1.0f};
}

struct llama_model {
    char path[1024];
};

struct llama_context {
    struct llama_model * model;
};

struct llama_model * llama_load_model_from_file(const char * path_model, struct llama_model_params params) {
    FILE * f = fopen(path_model, "rb");
    if (!f) return NULL;
    
    // Read GGUF magic
    char magic[4];
    if (fread(magic, 1, 4, f) != 4 || memcmp(magic, "GGUF", 4) != 0) {
        fclose(f);
        return NULL;
    }
    fclose(f);

    struct llama_model * model = (struct llama_model *)malloc(sizeof(struct llama_model));
    strncpy(model->path, path_model, 1023);
    return model;
}

void llama_free_model(struct llama_model * model) {
    free(model);
}

struct llama_context * llama_new_context_with_model(struct llama_model * model, struct llama_context_params params) {
    struct llama_context * ctx = (struct llama_context *)malloc(sizeof(struct llama_context));
    ctx->model = model;
    return ctx;
}

void llama_free(struct llama_context * ctx) {
    free(ctx);
}

int32_t llama_n_ctx(const struct llama_context * ctx) { return 2048; }
int32_t llama_n_vocab(const struct llama_model * model) { return 32000; }

int llama_tokenize(const struct llama_model * model, const char * text, int text_len, llama_token * tokens, int n_max_tokens, bool add_bos, bool special) {
    // Simple mock tokenizer: 1 token per char
    int n = 0;
    if (add_bos) tokens[n++] = 1;
    for (int i = 0; i < text_len && n < n_max_tokens; i++) {
        tokens[n++] = (llama_token)text[i];
    }
    return n;
}

int llama_token_to_piece(const struct llama_model * model, llama_token token, char * buf, int length) {
    if (length > 1) {
        buf[0] = (char)token;
        buf[1] = '\0';
        return 1;
    }
    return 0;
}

bool llama_decode(struct llama_context * ctx, struct llama_batch batch) {
    return true;
}

llama_token llama_sample_token_greedy(struct llama_context * ctx, struct llama_token_data_array * candidates) {
    return 0;
}

struct llama_batch llama_batch_init(int32_t n_tokens, int32_t embd, int32_t n_seq_max) {
    struct llama_batch batch;
    batch.n_tokens = 0;
    batch.token = (llama_token *)malloc(sizeof(llama_token) * n_tokens);
    batch.pos = (int32_t *)malloc(sizeof(int32_t) * n_tokens);
    batch.n_seq_id = (int32_t *)malloc(sizeof(int32_t) * n_tokens);
    batch.seq_id = (int32_t **)malloc(sizeof(int32_t *) * n_tokens);
    for (int i = 0; i < n_tokens; i++) {
        batch.seq_id[i] = (int32_t *)malloc(sizeof(int32_t) * n_seq_max);
    }
    batch.logits = (int8_t *)malloc(sizeof(int8_t) * n_tokens);
    return batch;
}

void llama_batch_free(struct llama_batch batch) {
    free(batch.token);
    free(batch.pos);
    free(batch.n_seq_id);
    // free seq_id[i] would be needed but for stub we simplify
    free(batch.seq_id);
    free(batch.logits);
}
