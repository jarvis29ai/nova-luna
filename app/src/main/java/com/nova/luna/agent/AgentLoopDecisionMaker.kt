package com.nova.luna.agent

import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.model.CommandResult
import com.nova.luna.screen.ScreenState

class AgentLoopDecisionMaker(
    private val completionDetector: TaskCompletionDetector = TaskCompletionDetector(),
    private val recoveryPolicy: AgentLoopRecoveryPolicy = AgentLoopRecoveryPolicy(),
    private val stuckDetector: StuckDetector = StuckDetector()
) {
    fun decide(
        plan: TaskPlan,
        state: AgentLoopState,
        screenState: ScreenState?,
        lastResult: CommandResult?,
        verification: AgentLoopVerification?,
        pendingConfirmation: PendingConfirmation? = null
    ): AgentLoopDecision {
        if (state.currentStepNumber >= plan.maxSteps) {
            return AgentLoopDecision.Stop(
                message = "I reached the safe step limit for this task.",
                stopReason = AgentLoopStopReason.MAX_STEPS_REACHED
            )
        }

        if (stuckDetector.isStuck(state.history, screenState?.signature())) {
            return AgentLoopDecision.Stop(
                message = "I detected a repeated screen, so I stopped to avoid looping forever.",
                stopReason = AgentLoopStopReason.STUCK_DETECTED
            )
        }

        if (completionDetector.isComplete(plan, state, screenState, lastResult, verification)) {
            return AgentLoopDecision.Complete(
                message = lastResult?.message?.takeIf { it.isNotBlank() }
                    ?: "The task is complete."
            )
        }

        return recoveryPolicy.evaluate(
            plan = plan,
            state = state,
            screenState = screenState,
            lastResult = lastResult,
            verification = verification,
            pendingConfirmation = pendingConfirmation
        )
    }
}
