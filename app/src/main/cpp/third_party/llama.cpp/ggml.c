#include "ggml.h"
#include <stdlib.h>
#include <string.h>
#include <time.h>

struct ggml_context {
    size_t mem_size;
    void * mem_buffer;
};

void ggml_time_init(void) {}

int64_t ggml_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + (int64_t)ts.tv_nsec / 1000000;
}
