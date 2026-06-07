package com.nova.luna.brain

object BrainProviderFactory {
    fun create(
        config: BrainRuntimeConfig = BrainRuntimeConfig.fromBuildConfig(),
        client: OllamaClient = HttpOllamaClient(),
        internetAvailable: Boolean = false,
        phoneBrainProvider: PhoneBrainProvider = UnavailablePhoneBrainProvider()
    ): BrainProvider {
        return createSelection(
            config = config,
            client = client,
            internetAvailable = internetAvailable,
            phoneBrainProvider = phoneBrainProvider
        ).provider
    }

    fun createSelection(
        config: BrainRuntimeConfig = BrainRuntimeConfig.fromBuildConfig(),
        client: OllamaClient = HttpOllamaClient(),
        internetAvailable: Boolean = false,
        phoneBrainProvider: PhoneBrainProvider = UnavailablePhoneBrainProvider()
    ): BrainRuntimeSelection {
        val localLlmProvider = if (config.useLocalLlm()) {
            PhoneLocalLlmProvider(
                runtime = PhoneLocalLlmRuntime(
                    config = PhoneLocalLlmConfig.fromBuildConfig()
                )
            )
        } else {
            null
        }

        return NetworkAwareBrainSelector().select(
            capabilityMode = config.capabilityMode,
            internetAvailable = internetAvailable,
            localModelAvailable = localLlmProvider?.available == true,
            phoneBrainProvider = phoneBrainProvider,
            localLlmProvider = localLlmProvider,
            fallbackProvider = LocalMockBrainProvider()
        )
    }
}
