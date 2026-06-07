package com.nova.luna.agent

import com.nova.luna.model.CommandResult
import com.nova.luna.screen.ScreenState
import java.util.Locale

class TaskCompletionDetector {
    fun isComplete(
        plan: TaskPlan,
        state: AgentLoopState,
        screenState: ScreenState?,
        commandResult: CommandResult?,
        verification: AgentLoopVerification?
    ): Boolean {
        val screenVerification = verification?.screenVerification

        if (commandResult == null) {
            return false
        }

        if (commandResult.shouldStopListening) {
            return true
        }

        if (!commandResult.success) {
            return false
        }

        if (commandResult.awaitingConfirmation || commandResult.safetyDecision.requiresConfirmation) {
            return false
        }

        if (hasCompletionHint(plan, commandResult, screenState, verification)) {
            return true
        }

        if (!plan.loopCapable) {
            return true
        }

        return state.currentStepNumber > 0 &&
            screenVerification?.applicable == true &&
            screenVerification?.verified == true
    }

    private fun hasCompletionHint(
        plan: TaskPlan,
        commandResult: CommandResult,
        screenState: ScreenState?,
        verification: AgentLoopVerification?
    ): Boolean {
        val haystack = buildString {
            append(commandResult.message)
            append(' ')
            append(commandResult.entities.values.joinToString(separator = " "))
            append(' ')
            append(commandResult.memoryMetadata.values.joinToString(separator = " "))
            append(' ')
            append(screenState?.summarizedState.orEmpty())
            append(' ')
            append(screenState?.visibleText.orEmpty().joinToString(separator = " "))
            append(' ')
            append(verification?.message.orEmpty())
        }.lowercase(Locale.US)

        return plan.completionHints.any { hint ->
            val normalizedHint = hint.lowercase(Locale.US).trim()
            normalizedHint.isNotBlank() && haystack.contains(normalizedHint)
        }
    }
}
