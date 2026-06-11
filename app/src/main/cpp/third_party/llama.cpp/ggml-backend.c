#include "ggml-backend.h"
#include <stdlib.h>

struct ggml_backend {
    const char * name;
};

void ggml_backend_init(void) {}
void ggml_backend_free(void) {}
