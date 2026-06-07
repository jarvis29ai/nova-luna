package com.nova.luna.brain

import com.nova.luna.BuildConfig

data class PhoneLocalLlmModelConfig(
    val id: PhoneLocalLlmModelId,
    val enabled: Boolean = true,
    val assetPath: String = "",
    val quantizedFileName: String = id.defaultQuantizedFileName,
    val minimumRamMb: Int? = id.minimumRamMbHint,
    val maxInputTokens: Int = 4096,
    val maxPromptChars: Int = 8192,
    val timeoutMs: Long = 5_000,
    val priority: Int = id.priority
) {
    fun sanitized(): PhoneLocalLlmModelConfig {
        return copy(
            assetPath = assetPath.trim(),
            quantizedFileName = quantizedFileName.trim(),
            minimumRamMb = minimumRamMb?.coerceAtLeast(1),
            maxInputTokens = maxInputTokens.coerceAtLeast(1),
            maxPromptChars = maxPromptChars.coerceAtLeast(1),
            timeoutMs = timeoutMs.coerceAtLeast(1L)
        )
    }
}

data class PhoneLocalLlmConfig(
    val enabled: Boolean = false,
    val maxInputTokens: Int = 4096,
    val maxPromptChars: Int = 8_192,
    val timeoutMs: Long = 5_000,
    val models: List<PhoneLocalLlmModelConfig> = defaultModelStack()
) {
    val selectedModelPriority: List<PhoneLocalLlmModelId>
        get() = models.sortedBy { it.priority }.map { it.id }

    fun sanitized(): PhoneLocalLlmConfig {
        return copy(
            maxInputTokens = maxInputTokens.coerceAtLeast(1),
            maxPromptChars = maxPromptChars.coerceAtLeast(1),
            timeoutMs = timeoutMs.coerceAtLeast(1L),
            models = models.map { it.sanitized() }.sortedBy { it.priority }
        )
    }

    fun modelConfig(modelId: PhoneLocalLlmModelId): PhoneLocalLlmModelConfig? {
        return models.firstOrNull { it.id == modelId }
    }

    companion object {
        fun fromBuildConfig(): PhoneLocalLlmConfig {
            return PhoneLocalLlmConfig(
                enabled = BuildConfig.GEMMA_ENABLED,
                maxInputTokens = BuildConfig.GEMMA_MAX_TOKENS,
                maxPromptChars = BuildConfig.GEMMA_CONTEXT_WINDOW,
                timeoutMs = 5_000,
                models = listOf(
                    PhoneLocalLlmModelConfig(
                        id = PhoneLocalLlmModelId.GEMMA_3N,
                        enabled = BuildConfig.GEMMA_ENABLED && BuildConfig.GEMMA_ROLE_ENABLED,
                        assetPath = BuildConfig.GEMMA_MODEL_ASSET_PATH,
                        quantizedFileName = PhoneLocalLlmModelId.GEMMA_3N.defaultQuantizedFileName,
                        minimumRamMb = PhoneLocalLlmModelId.GEMMA_3N.minimumRamMbHint,
                        maxInputTokens = BuildConfig.GEMMA_MAX_TOKENS,
                        maxPromptChars = BuildConfig.GEMMA_CONTEXT_WINDOW,
                        timeoutMs = 5_000,
                        priority = PhoneLocalLlmModelId.GEMMA_3N.priority
                    ),
                    PhoneLocalLlmModelConfig(
                        id = PhoneLocalLlmModelId.QWEN_3_SMALL,
                        enabled = false,
                        assetPath = "",
                        quantizedFileName = PhoneLocalLlmModelId.QWEN_3_SMALL.defaultQuantizedFileName,
                        minimumRamMb = PhoneLocalLlmModelId.QWEN_3_SMALL.minimumRamMbHint,
                        maxInputTokens = 4096,
                        maxPromptChars = 6_144,
                        timeoutMs = 5_000,
                        priority = PhoneLocalLlmModelId.QWEN_3_SMALL.priority
                    ),
                    PhoneLocalLlmModelConfig(
                        id = PhoneLocalLlmModelId.GEMMA_3_270M,
                        enabled = false,
                        assetPath = "",
                        quantizedFileName = PhoneLocalLlmModelId.GEMMA_3_270M.defaultQuantizedFileName,
                        minimumRamMb = PhoneLocalLlmModelId.GEMMA_3_270M.minimumRamMbHint,
                        maxInputTokens = 2_048,
                        maxPromptChars = 4_096,
                        timeoutMs = 5_000,
                        priority = PhoneLocalLlmModelId.GEMMA_3_270M.priority
                    ),
                    PhoneLocalLlmModelConfig(
                        id = PhoneLocalLlmModelId.PHI_4_MINI,
                        enabled = false,
                        assetPath = "",
                        quantizedFileName = PhoneLocalLlmModelId.PHI_4_MINI.defaultQuantizedFileName,
                        minimumRamMb = PhoneLocalLlmModelId.PHI_4_MINI.minimumRamMbHint,
                        maxInputTokens = 2_048,
                        maxPromptChars = 4_096,
                        timeoutMs = 5_000,
                        priority = PhoneLocalLlmModelId.PHI_4_MINI.priority
                    )
                )
            ).sanitized()
        }

        fun defaultModelStack(): List<PhoneLocalLlmModelConfig> {
            return listOf(
                PhoneLocalLlmModelConfig(id = PhoneLocalLlmModelId.GEMMA_3N),
                PhoneLocalLlmModelConfig(id = PhoneLocalLlmModelId.QWEN_3_SMALL, enabled = false),
                PhoneLocalLlmModelConfig(id = PhoneLocalLlmModelId.GEMMA_3_270M, enabled = false),
                PhoneLocalLlmModelConfig(id = PhoneLocalLlmModelId.PHI_4_MINI, enabled = false)
            )
        }
    }
}
