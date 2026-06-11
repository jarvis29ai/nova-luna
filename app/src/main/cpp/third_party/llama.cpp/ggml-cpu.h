#ifndef GGML_CPU_H
#define GGML_CPU_H

#include "ggml-backend.h"

#ifdef __cplusplus
extern "C" {
#endif

ggml_backend_t ggml_backend_cpu_init(void);

#ifdef __cplusplus
}
#endif

#endif // GGML_CPU_H
