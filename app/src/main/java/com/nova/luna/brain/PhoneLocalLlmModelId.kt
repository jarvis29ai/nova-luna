package com.nova.luna.brain

enum class PhoneLocalLlmModelId(
    val wireValue: String,
    val displayName: String,
    val roleLabel: String,
    val priority: Int,
    val defaultQuantizedFileName: String,
    val minimumRamMbHint: Int? = null
) {
    GEMMA_3N(
        wireValue = "gemma_3n",
        displayName = "Gemma 3n",
        roleLabel = "core phone brain",
        priority = 0,
        defaultQuantizedFileName = "gemma-3n-E2B-it-int4.litertlm",
        minimumRamMbHint = 4096
    ),
    QWEN_1_5B(
        wireValue = "qwen_1_5b",
        displayName = "Qwen 2.5 1.5B",
        roleLabel = "multilingual backup",
        priority = 1,
        defaultQuantizedFileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        minimumRamMbHint = 4096
    ),
    QWEN_0_5B(
        wireValue = "qwen_0_5b",
        displayName = "Qwen 2.5 0.5B",
        roleLabel = "lightweight fallback",
        priority = 2,
        defaultQuantizedFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        minimumRamMbHint = 2048
    ),
    GEMMA_3_270M(
        wireValue = "gemma_3_270m",
        displayName = "Gemma 3 270M",
        roleLabel = "lightweight fallback",
        priority = 3,
        defaultQuantizedFileName = "gemma-3-270m-q4.gguf",
        minimumRamMbHint = 2048
    ),
    PHI_4_MINI(
        wireValue = "phi_4_mini",
        displayName = "Phi-4 mini",
        roleLabel = "lightweight fallback",
        priority = 3,
        defaultQuantizedFileName = "phi-4-mini-q4.gguf",
        minimumRamMbHint = 2048
    );

    companion object {
        fun fromWireValue(value: String?): PhoneLocalLlmModelId? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
