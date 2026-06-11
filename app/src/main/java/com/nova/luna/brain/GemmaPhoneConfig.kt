package com.nova.luna.brain

import com.nova.luna.BuildConfig

data class GemmaPhoneConfig(
    val gemmaEnabled: Boolean,
    val gemmaModelAssetPath: String,
    val gemmaMaxTokens: Int,
    val gemmaTemperature: Double,
    val gemmaTopK: Int,
    val gemmaContextWindow: Int,
    val gemmaRoleEnabled: Boolean
) {
    val modelPathConfigured: Boolean
        get() = gemmaModelAssetPath.isNotBlank()

    val runtimeFeatureEnabled: Boolean
        get() = gemmaEnabled && gemmaRoleEnabled

    fun sanitized(): GemmaPhoneConfig {
        return copy(
            gemmaModelAssetPath = gemmaModelAssetPath.trim(),
            gemmaMaxTokens = gemmaMaxTokens.coerceAtLeast(1),
            gemmaTemperature = gemmaTemperature.coerceIn(0.0, 2.0),
            gemmaTopK = gemmaTopK.coerceAtLeast(1),
            gemmaContextWindow = gemmaContextWindow.coerceAtLeast(1)
        )
    }

    fun toPhoneLocalLlmConfig(): PhoneLocalLlmConfig {
        val sanitized = sanitized()
        return PhoneLocalLlmConfig(
            enabled = sanitized.gemmaEnabled && sanitized.gemmaRoleEnabled,
            maxInputTokens = sanitized.gemmaMaxTokens,
            maxPromptChars = sanitized.gemmaContextWindow,
            timeoutMs = 5_000,
            models = listOf(
                PhoneLocalLlmModelConfig(
                    id = PhoneLocalLlmModelId.GEMMA_3N,
                    enabled = sanitized.gemmaEnabled && sanitized.gemmaRoleEnabled,
                    assetPath = sanitized.gemmaModelAssetPath,
                    quantizedFileName = PhoneLocalLlmModelId.GEMMA_3N.defaultQuantizedFileName,
                    minimumRamMb = PhoneLocalLlmModelId.GEMMA_3N.minimumRamMbHint,
                    maxInputTokens = sanitized.gemmaMaxTokens,
                    maxPromptChars = sanitized.gemmaContextWindow,
                    timeoutMs = 5_000,
                    priority = PhoneLocalLlmModelId.GEMMA_3N.priority
                ),
                PhoneLocalLlmModelConfig(
                    id = PhoneLocalLlmModelId.QWEN_1_5B,
                    enabled = false,
                    assetPath = "",
                    quantizedFileName = PhoneLocalLlmModelId.QWEN_1_5B.defaultQuantizedFileName,
                    minimumRamMb = PhoneLocalLlmModelId.QWEN_1_5B.minimumRamMbHint,
                    maxInputTokens = 4_096,
                    maxPromptChars = 6_144,
                    timeoutMs = 5_000,
                    priority = PhoneLocalLlmModelId.QWEN_1_5B.priority
                ),
                PhoneLocalLlmModelConfig(
                    id = PhoneLocalLlmModelId.QWEN_0_5B,
                    enabled = false,
                    assetPath = "",
                    quantizedFileName = PhoneLocalLlmModelId.QWEN_0_5B.defaultQuantizedFileName,
                    minimumRamMb = PhoneLocalLlmModelId.QWEN_0_5B.minimumRamMbHint,
                    maxInputTokens = 2_048,
                    maxPromptChars = 4_096,
                    timeoutMs = 5_000,
                    priority = PhoneLocalLlmModelId.QWEN_0_5B.priority
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

    companion object {
        fun fromBuildConfig(): GemmaPhoneConfig {
            return GemmaPhoneConfig(
                gemmaEnabled = BuildConfig.GEMMA_ENABLED,
                gemmaModelAssetPath = BuildConfig.GEMMA_MODEL_ASSET_PATH,
                gemmaMaxTokens = BuildConfig.GEMMA_MAX_TOKENS,
                gemmaTemperature = BuildConfig.GEMMA_TEMPERATURE,
                gemmaTopK = BuildConfig.GEMMA_TOP_K,
                gemmaContextWindow = BuildConfig.GEMMA_CONTEXT_WINDOW,
                gemmaRoleEnabled = BuildConfig.GEMMA_ROLE_ENABLED
            ).sanitized()
        }
    }
}
