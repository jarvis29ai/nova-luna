#ifndef GGML_BACKEND_H
#define GGML_BACKEND_H

#include "ggml.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ggml_backend * ggml_backend_t;

void ggml_backend_init(void);
void ggml_backend_free(void);

#ifdef __cplusplus
}
#endif

#endif // GGML_BACKEND_H
