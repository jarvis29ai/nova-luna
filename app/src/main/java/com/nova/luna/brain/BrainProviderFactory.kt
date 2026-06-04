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
            LocalLlmBrainProvider(
                config = config,
                client = client
            )
        } else {
            null
        }

        return NetworkAwareBrainSelector().select(
            capabilityMode = config.capabilityMode,
            internetAvailable = internetAvailable,
            localModelAvailable = localLlmProvider != null,
            phoneBrainProvider = phoneBrainProvider,
            localLlmProvider = localLlmProvider,
            fallbackProvider = LocalMockBrainProvider()
        )
    }
}
