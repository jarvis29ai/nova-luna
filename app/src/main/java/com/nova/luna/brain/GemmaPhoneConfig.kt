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
