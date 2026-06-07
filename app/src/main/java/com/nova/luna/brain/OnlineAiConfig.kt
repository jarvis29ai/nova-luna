package com.nova.luna.brain

import com.nova.luna.BuildConfig

data class OnlineAiConfig(
    val enabled: Boolean = false,
    val requireConfirmation: Boolean = true,
    val providerType: OnlineAiProviderType = OnlineAiProviderType.UNAVAILABLE,
    val timeoutMs: Long = 4_000,
    val sendScreenText: Boolean = false,
    val sendPrivateMessages: Boolean = false,
    val maxPromptChars: Int = 6_144
) {
    fun sanitized(): OnlineAiConfig {
        return copy(
            timeoutMs = timeoutMs.coerceAtLeast(1L),
            maxPromptChars = maxPromptChars.coerceAtLeast(1)
        )
    }

    companion object {
        fun fromBuildConfig(): OnlineAiConfig {
            return OnlineAiConfig(
                enabled = BuildConfig.ONLINE_AI_ENABLED,
                requireConfirmation = BuildConfig.ONLINE_AI_REQUIRE_CONFIRMATION,
                providerType = OnlineAiProviderType.fromWireValue(BuildConfig.ONLINE_AI_PROVIDER)
                    ?: OnlineAiProviderType.UNAVAILABLE,
                timeoutMs = BuildConfig.ONLINE_AI_TIMEOUT_MS,
                sendScreenText = BuildConfig.ONLINE_AI_SEND_SCREEN_TEXT,
                sendPrivateMessages = BuildConfig.ONLINE_AI_SEND_PRIVATE_MESSAGES
            ).sanitized()
        }
    }
}
