package com.nova.luna.brain

import com.nova.luna.screen.ScreenState

fun onlineAiConfig(
    enabled: Boolean = true,
    requireConfirmation: Boolean = true,
    providerType: OnlineAiProviderType = OnlineAiProviderType.FAKE,
    timeoutMs: Long = 1_000L,
    sendScreenText: Boolean = false,
    sendPrivateMessages: Boolean = false,
    maxPromptChars: Int = 6_144
): OnlineAiConfig {
    return OnlineAiConfig(
        enabled = enabled,
        requireConfirmation = requireConfirmation,
        providerType = providerType,
        timeoutMs = timeoutMs,
        sendScreenText = sendScreenText,
        sendPrivateMessages = sendPrivateMessages,
        maxPromptChars = maxPromptChars
    )
}

fun onlineBrainService(
    provider: OnlineAiProvider = FakeOnlineAiProvider(),
    config: OnlineAiConfig = onlineAiConfig(providerType = provider.providerType),
    internetAvailable: Boolean = true
): BrainService {
    return BrainService(
        onlineAiConfig = config,
        onlineAiProvider = provider,
        internetAvailable = internetAvailable
    )
}

fun onlineBrainRouter(
    providerType: OnlineAiProviderType = OnlineAiProviderType.FAKE,
    enabled: Boolean = true,
    internetAvailable: Boolean = true,
    requireConfirmation: Boolean = true
): BrainRouter {
    return BrainRouter(
        onlineAiConfig = onlineAiConfig(
            enabled = enabled,
            requireConfirmation = requireConfirmation,
            providerType = providerType
        ),
        internetAvailable = internetAvailable
    )
}

fun sampleScreenState(): ScreenState {
    return ScreenState(
        packageName = "com.example.mail",
        appName = "Mail",
        className = "android.widget.FrameLayout",
        timestampMillis = 1_000L,
        isAccessibilityReady = true,
        visibleText = listOf("Inbox", "Compose", "Search"),
        contentDescriptions = listOf("Search mail"),
        summarizedState = "Mail inbox with a search bar and compose button.",
        confidence = 0.85f,
        rawNodeCount = 3,
        truncated = false
    )
}
