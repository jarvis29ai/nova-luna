package com.nova.luna.brain

import com.nova.luna.model.BrainCapabilityMode
import com.nova.luna.model.BrainRuntimeStatus

class NetworkAwareBrainSelector {
    fun select(
        capabilityMode: BrainCapabilityMode,
        internetAvailable: Boolean,
        localModelAvailable: Boolean,
        phoneBrainProvider: PhoneBrainProvider,
        localLlmProvider: BrainProvider?,
        fallbackProvider: BrainProvider = LocalMockBrainProvider()
    ): BrainRuntimeSelection {
        return when (capabilityMode) {
            BrainCapabilityMode.OFFLINE_ONLY -> {
                if (phoneBrainProvider.available) {
                    buildSelection(
                        provider = phoneBrainProvider,
                        capabilityMode = capabilityMode,
                        internetAvailable = internetAvailable,
                        localModelAvailable = false,
                        fallbackActive = false,
                        reason = if (internetAvailable) {
                            "Offline-only mode is active. Internet is ignored and execution stays local."
                        } else {
                            "Offline-only mode is active."
                        }
                    )
                } else {
                    buildSelection(
                        provider = fallbackProvider,
                        capabilityMode = capabilityMode,
                        internetAvailable = internetAvailable,
                        localModelAvailable = false,
                        fallbackActive = true,
                        reason = "Offline-only mode has no phone-local model yet, so LocalMockBrainProvider is the guaranteed fallback."
                    )
                }
            }

            BrainCapabilityMode.ONLINE_ASSISTED -> {
                if (phoneBrainProvider.available) {
                    buildSelection(
                        provider = phoneBrainProvider,
                        capabilityMode = capabilityMode,
                        internetAvailable = internetAvailable,
                        localModelAvailable = false,
                        fallbackActive = false,
                        reason = if (internetAvailable) {
                            "Online-assisted mode may use internet for information lookup only; SafetyGate still controls execution."
                        } else {
                            "Online-assisted mode requested, but no internet is available, so execution stays local."
                        }
                    )
                } else {
                    buildSelection(
                        provider = fallbackProvider,
                        capabilityMode = capabilityMode,
                        internetAvailable = internetAvailable,
                        localModelAvailable = false,
                        fallbackActive = true,
                        reason = if (internetAvailable) {
                            "Online-assisted mode is enabled, but the phone-local model is unavailable, so LocalMockBrainProvider is used."
                        } else {
                            "No internet is available and the phone-local model is unavailable, so LocalMockBrainProvider is used."
                        }
                    )
                }
            }

            BrainCapabilityMode.LOCAL_LLM_DEV -> {
                if (localLlmProvider != null && localModelAvailable) {
                    buildSelection(
                        provider = localLlmProvider,
                        capabilityMode = capabilityMode,
                        internetAvailable = internetAvailable,
                        localModelAvailable = true,
                        fallbackActive = false,
                        reason = "Local LLM dev mode is enabled for desktop testing."
                    )
                } else {
                    buildSelection(
                        provider = fallbackProvider,
                        capabilityMode = capabilityMode,
                        internetAvailable = internetAvailable,
                        localModelAvailable = false,
                        fallbackActive = true,
                        reason = "Local LLM dev mode is requested, but no local model is available, so LocalMockBrainProvider is used."
                    )
                }
            }
        }
    }

    private fun buildSelection(
        provider: BrainProvider,
        capabilityMode: BrainCapabilityMode,
        internetAvailable: Boolean,
        localModelAvailable: Boolean,
        fallbackActive: Boolean,
        reason: String
    ): BrainRuntimeSelection {
        return BrainRuntimeSelection(
            provider = provider,
            runtimeStatus = BrainRuntimeStatus(
                selectedProvider = providerName(provider),
                capabilityMode = capabilityMode,
                internetAvailable = internetAvailable,
                localModelAvailable = localModelAvailable,
                fallbackActive = fallbackActive,
                reason = reason,
                safetyChainActive = true
            )
        )
    }

    private fun providerName(provider: BrainProvider): String {
        return provider::class.java.simpleName.takeIf { it.isNotBlank() }
            ?: provider::class.qualifiedName.orEmpty()
    }
}
