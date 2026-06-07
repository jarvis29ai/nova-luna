package com.nova.luna.agent

import com.nova.luna.model.CommandResult

data class AgentLoopResult(
    val taskPlan: TaskPlan,
    val state: AgentLoopState,
    val finalCommandResult: CommandResult,
    val steps: List<TaskStep>,
    val started: Boolean,
    val completed: Boolean,
    val recoveryUsed: Boolean,
    val askedUser: Boolean,
    val stopReason: AgentLoopStopReason,
    val verification: AgentLoopVerification? = null
) {
    val loopId: String
        get() = state.loopId

    val stepCount: Int
        get() = steps.size
}
