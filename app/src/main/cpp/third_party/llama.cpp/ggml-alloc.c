#include "ggml-alloc.h"
#include <stdlib.h>

struct ggml_allocr {
    void * data;
    size_t size;
};

ggml_allocr_t ggml_allocr_new(void * data, size_t size, size_t alignment) {
    ggml_allocr_t alloc = (ggml_allocr_t)malloc(sizeof(struct ggml_allocr));
    alloc->data = data;
    alloc->size = size;
    return alloc;
}

void ggml_allocr_free(ggml_allocr_t alloc) {
    free(alloc);
}
