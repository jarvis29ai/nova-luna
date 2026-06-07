package com.nova.luna.brain

import com.nova.luna.model.BrainAction

data class OnlineAiResult(
    val providerType: OnlineAiProviderType,
    val status: OnlineAiStatus,
    val available: Boolean,
    val rawResponse: String? = null,
    val sanitizedText: String? = null,
    val candidateAction: BrainAction? = null,
    val reason: String,
    val redactionCount: Int = 0,
    val promptBuilt: Boolean = false,
    val policyDecision: OnlineAiPolicyDecision? = null,
    val trace: OnlineAiTrace? = null,
    val latencyMillis: Long? = null,
    val providerName: String? = null
) {
    fun withTrace(trace: OnlineAiTrace): OnlineAiResult {
        return copy(trace = trace)
    }
}
