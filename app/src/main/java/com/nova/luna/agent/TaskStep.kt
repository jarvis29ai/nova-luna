package com.nova.luna.agent

import com.nova.luna.model.CommandResult
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.BrainAction

data class TaskStep(
    val stepNumber: Int,
    val step: AgentLoopStep,
    val status: TaskStepStatus,
    val screenSnapshotId: String? = null,
    val screenSummary: String? = null,
    val brainAction: BrainAction? = null,
    val safetyDecision: SafetyDecision? = null,
    val commandResult: CommandResult? = null,
    val verification: AgentLoopVerification? = null,
    val stopReason: AgentLoopStopReason? = null,
    val message: String? = null,
    val retryable: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
