package com.nova.luna.brain

class UnavailableOnlineAiProvider(
    private val reason: String = "Online helper is unavailable.",
    override val providerType: OnlineAiProviderType = OnlineAiProviderType.UNAVAILABLE
) : OnlineAiProvider {
    override val available: Boolean = false

    override fun generate(prompt: String, timeoutMs: Long): OnlineAiResult {
        return OnlineAiResult(
            providerType = providerType,
            status = OnlineAiStatus.UNAVAILABLE,
            available = false,
            rawResponse = null,
            reason = reason,
            promptBuilt = false,
            providerName = this::class.java.simpleName,
            latencyMillis = 0L
        )
    }
}
