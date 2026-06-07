package com.nova.luna.brain

import com.nova.luna.BuildConfig
import com.nova.luna.model.BrainCapabilityMode

private fun inferCapabilityMode(
    brainProvider: String,
    llmEnabled: Boolean
): BrainCapabilityMode {
    return if (llmEnabled) {
        BrainCapabilityMode.LOCAL_LLM_DEV
    } else {
        BrainCapabilityMode.OFFLINE_ONLY
    }
}

data class BrainRuntimeConfig(
    val brainProvider: String,
    val ollamaBaseUrl: String,
    val ollamaModel: String,
    val llmEnabled: Boolean,
    val capabilityMode: BrainCapabilityMode = inferCapabilityMode(brainProvider, llmEnabled)
) {
    fun useLocalLlm(): Boolean {
        return llmEnabled && capabilityMode == BrainCapabilityMode.LOCAL_LLM_DEV
    }

    companion object {
        fun fromBuildConfig(): BrainRuntimeConfig {
            val brainProvider = BuildConfig.BRAIN_PROVIDER
            val llmEnabled = BuildConfig.LLM_ENABLED
            return BrainRuntimeConfig(
                brainProvider = brainProvider,
                ollamaBaseUrl = BuildConfig.OLLAMA_BASE_URL,
                ollamaModel = BuildConfig.OLLAMA_MODEL,
                llmEnabled = llmEnabled,
                capabilityMode = BrainCapabilityMode.fromWireValue(BuildConfig.BRAIN_CAPABILITY_MODE)
                    ?: inferCapabilityMode(brainProvider, llmEnabled)
            )
        }
    }
}
