package com.nova.luna.brain

object OnlineAiProviderFactory {
    fun create(config: OnlineAiConfig = OnlineAiConfig.fromBuildConfig()): OnlineAiProvider {
        val sanitized = config.sanitized()
        if (!sanitized.enabled) {
            return UnavailableOnlineAiProvider(
                reason = "Online helper is disabled by config.",
                providerType = sanitized.providerType
            )
        }

        return when (sanitized.providerType) {
            OnlineAiProviderType.FAKE -> FakeOnlineAiProvider()
            OnlineAiProviderType.CHATGPT ->
                UnavailableOnlineAiProvider(
                    reason = "ChatGPT integration is disabled or pending.",
                    providerType = sanitized.providerType
                )

            OnlineAiProviderType.GEMINI ->
                UnavailableOnlineAiProvider(
                    reason = "Gemini integration is disabled or pending.",
                    providerType = sanitized.providerType
                )

            OnlineAiProviderType.CLAUDE ->
                UnavailableOnlineAiProvider(
                    reason = "Claude integration is disabled or pending.",
                    providerType = sanitized.providerType
                )

            OnlineAiProviderType.UNAVAILABLE ->
                UnavailableOnlineAiProvider(
                    reason = "Online helper provider is unavailable by configuration.",
                    providerType = sanitized.providerType
                )
        }
    }
}
