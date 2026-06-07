package com.nova.luna.brain

interface OnlineAiProvider {
    val providerType: OnlineAiProviderType
    val available: Boolean

    fun generate(prompt: String, timeoutMs: Long): OnlineAiResult

    fun cancel(): Boolean = false
    fun diagnostics(): String = providerType.wireValue
}
