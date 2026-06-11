#ifndef GGML_H
#define GGML_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Upstream-aligned GGML Header (Subset for Phase 13)
struct ggml_tensor;
struct ggml_context;
struct ggml_cgraph;

enum ggml_type {
    GGML_TYPE_F32  = 0,
    GGML_TYPE_F16  = 1,
    GGML_TYPE_Q4_0 = 2,
    GGML_TYPE_Q4_1 = 3,
    GGML_TYPE_Q5_0 = 6,
    GGML_TYPE_Q5_1 = 7,
    GGML_TYPE_Q8_0 = 8,
    GGML_TYPE_Q8_1 = 9,
    GGML_TYPE_I8,
    GGML_TYPE_I16,
    GGML_TYPE_I32,
    GGML_TYPE_COUNT,
};

struct ggml_init_params {
    size_t mem_size;
    void * mem_buffer;
    bool   no_alloc;
};

void ggml_time_init(void);
int64_t ggml_time_ms(void);

#ifdef __cplusplus
}
#endif

#endif // GGML_H
