#ifndef GGML_QUANTS_H
#define GGML_QUANTS_H

#include "ggml.h"

#ifdef __cplusplus
extern "C" {
#endif

// Quantization logic for Q4_K_M etc.
void ggml_quants_init(void);

#ifdef __cplusplus
}
#endif

#endif // GGML_QUANTS_H
