package com.nova.luna.llm

import com.nova.luna.BuildConfig

data class LocalLlmModelConfig(
    val modelId: LocalLlmModelId,
    val displayName: String,
    val role: LocalLlmRole,
    val enabledByDefault: Boolean = true,
    val assetPath: String = "",
    val tokenizerPath: String? = null,
    val maxInputTokens: Int = 2048,
    val maxOutputTokens: Int = 512,
    val timeoutMs: Long = 10_000,
    val temperature: Double = 0.1,
    val topK: Int = 40,
    val topP: Double = 0.9,
    val offlineOnly: Boolean = true,
    val allowOnlineFallback: Boolean = false,
    val minMemoryMbRequired: Int = 2048,
    val supportedLanguages: List<String> = listOf("en", "hi"),
    val priorityOrder: Int = 0
)

data class LocalLlmConfig(
    val enabled: Boolean = true,
    val models: List<LocalLlmModelConfig> = defaultModelStack()
) {
    companion object {
        fun defaultModelStack(): List<LocalLlmModelConfig> {
            return listOf(
                LocalLlmModelConfig(
                    modelId = LocalLlmModelId.GEMMA_3N_CORE,
                    displayName = "Gemma 3n",
                    role = LocalLlmRole.CORE_REASONING,
                    assetPath = BuildConfig.GEMMA_MODEL_ASSET_PATH,
                    priorityOrder = 1,
                    minMemoryMbRequired = 4096
                ),
                LocalLlmModelConfig(
                    modelId = LocalLlmModelId.QWEN_3_SMALL_MULTILINGUAL,
                    displayName = "Qwen 3 Small",
                    role = LocalLlmRole.MULTILINGUAL_BACKUP,
                    priorityOrder = 2,
                    minMemoryMbRequired = 4096
                ),
                LocalLlmModelConfig(
                    modelId = LocalLlmModelId.GEMMA_3_270M_FALLBACK,
                    displayName = "Gemma 3 270M",
                    role = LocalLlmRole.LIGHT_FALLBACK,
                    priorityOrder = 3,
                    minMemoryMbRequired = 2048
                ),
                LocalLlmModelConfig(
                    modelId = LocalLlmModelId.PHI_4_MINI_FALLBACK,
                    displayName = "Phi-4 mini",
                    role = LocalLlmRole.LIGHT_FALLBACK,
                    priorityOrder = 4,
                    minMemoryMbRequired = 2048
                )
            )
        }
    }
}
