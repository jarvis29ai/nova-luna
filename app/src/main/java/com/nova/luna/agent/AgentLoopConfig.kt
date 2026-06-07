package com.nova.luna.agent

data class AgentLoopConfig(
    val maxStepsPerTask: Int = 6,
    val maxRetriesPerStep: Int = 1,
    val actionDelayMs: Long = 0L,
    val verificationDelayMs: Long = 0L,
    val screenReadTimeoutMs: Long = 2_000L,
    val modelTimeoutMs: Long = 5_000L,
    val allowOnlineHelper: Boolean = false,
    val allowSensitiveFields: Boolean = false,
    val allowPayment: Boolean = false,
    val requireConfirmationForFinalActions: Boolean = true,
    val stopOnRepeatedScreen: Boolean = true,
    val stopOnSensitiveScreen: Boolean = true,
    val maxElapsedMillis: Long = 45_000L,
    val maxRepeatedScreenCount: Int = 2,
    val retryLoadingOnce: Boolean = true
) {
    companion object {
        fun safeDefaults(): AgentLoopConfig = AgentLoopConfig()
    }
}
