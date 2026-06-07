package com.nova.luna.agent

sealed class AgentLoopDecision(
    open val step: AgentLoopStep,
    open val message: String,
    open val stopReason: AgentLoopStopReason? = null,
    open val retryable: Boolean = false
) {
    data class Continue(
        override val step: AgentLoopStep = AgentLoopStep.PLAN_NEXT_STEP,
        override val message: String = "Continuing the task.",
        override val retryable: Boolean = false
    ) : AgentLoopDecision(step, message, null, retryable)

    data class Recover(
        override val step: AgentLoopStep = AgentLoopStep.RECOVER,
        override val message: String,
        override val stopReason: AgentLoopStopReason? = null,
        override val retryable: Boolean = true
    ) : AgentLoopDecision(step, message, stopReason, retryable)

    data class AskUser(
        override val step: AgentLoopStep = AgentLoopStep.ASK_USER,
        override val message: String,
        override val stopReason: AgentLoopStopReason = AgentLoopStopReason.NEEDS_USER_INPUT
    ) : AgentLoopDecision(step, message, stopReason, false)

    data class ManualHandoff(
        override val step: AgentLoopStep = AgentLoopStep.MANUAL_HANDOFF,
        override val message: String,
        override val stopReason: AgentLoopStopReason = AgentLoopStopReason.MANUAL_HANDOFF
    ) : AgentLoopDecision(step, message, stopReason, false)

    data class Complete(
        override val step: AgentLoopStep = AgentLoopStep.COMPLETE,
        override val message: String,
        override val stopReason: AgentLoopStopReason = AgentLoopStopReason.COMPLETED
    ) : AgentLoopDecision(step, message, stopReason, false)

    data class Stop(
        override val step: AgentLoopStep = AgentLoopStep.STOPPED,
        override val message: String,
        override val stopReason: AgentLoopStopReason
    ) : AgentLoopDecision(step, message, stopReason, false)
}
