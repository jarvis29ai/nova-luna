#ifndef GGML_ALLOC_H
#define GGML_ALLOC_H

#include "ggml.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ggml_allocr * ggml_allocr_t;

ggml_allocr_t ggml_allocr_new(void * data, size_t size, size_t alignment);
void ggml_allocr_free(ggml_allocr_t alloc);

#ifdef __cplusplus
}
#endif

#endif // GGML_ALLOC_H
