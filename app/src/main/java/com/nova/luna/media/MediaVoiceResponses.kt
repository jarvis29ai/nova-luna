package com.nova.luna.media

object MediaVoiceResponses {
    fun openingApp(provider: MediaProvider) = "Opening ${provider.displayName}."
    fun appNotInstalled(provider: MediaProvider) = "${provider.displayName} is not installed. I can open the browser or you can install it from the Play Store."
    fun whatToSearch() = "What should I search for?"
    fun foundResults(count: Int) = "I found $count results. Which one should I play?"
    fun playingContent(title: String, provider: MediaProvider) = "Playing $title on ${provider.displayName}."
    fun playbackPaused() = "Paused."
    fun playbackResumed() = "Resumed."
    fun confirmAction(action: String) = "Should I $action?"
    fun safetyBlocked() = "This action seems to require a payment or login. Please complete it manually for your security."
    fun manualHandoff() = "I can't complete this automatically. Please take over."
}
