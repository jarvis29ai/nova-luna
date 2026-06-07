package com.nova.luna.agent

import com.nova.luna.model.CommandResult

class RetryPolicy(
    private val config: AgentLoopConfig = AgentLoopConfig()
) {
    fun canRetry(state: AgentLoopState, retryable: Boolean): Boolean {
        return retryable && state.retryCount < state.maxRetries && state.currentStepNumber < state.maxSteps
    }

    fun shouldRetryForScreenFailure(result: CommandResult): Boolean {
        return !result.success && !result.safetyDecision.humanRequired
    }

    fun nextRetryCount(state: AgentLoopState): Int {
        return state.retryCount + 1
    }

    fun isLoadingRetryAllowed(state: AgentLoopState): Boolean {
        return config.retryLoadingOnce && state.retryCount == 0
    }
}
