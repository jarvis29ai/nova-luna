package com.nova.luna.agent

import com.nova.luna.model.CommandResult
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.BrainAction
import com.nova.luna.memory.BrainSessionType
import java.util.UUID

data class AgentLoopState(
    val loopId: String = UUID.randomUUID().toString(),
    val sessionId: String? = null,
    val taskGoal: String,
    val domainSessionType: BrainSessionType? = null,
    val currentStepNumber: Int = 0,
    val maxSteps: Int = 6,
    val retryCount: Int = 0,
    val maxRetries: Int = 1,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val lastUpdatedAtMillis: Long = startedAtMillis,
    val lastScreenSnapshotId: String? = null,
    val lastScreenSummary: String? = null,
    val lastBrainAction: BrainAction? = null,
    val lastSafetyDecision: SafetyDecision? = null,
    val lastCommandResult: CommandResult? = null,
    val lastVerification: AgentLoopVerification? = null,
    val stuckCount: Int = 0,
    val userFacingStatus: String = "Starting",
    val stopReason: AgentLoopStopReason? = null,
    val completionStatus: TaskStepStatus = TaskStepStatus.PENDING,
    val history: List<TaskStep> = emptyList()
) {
    fun record(step: TaskStep): AgentLoopState {
        return copy(
            currentStepNumber = step.stepNumber,
            lastUpdatedAtMillis = step.createdAtMillis,
            lastScreenSnapshotId = step.screenSnapshotId ?: lastScreenSnapshotId,
            lastScreenSummary = step.screenSummary ?: lastScreenSummary,
            lastBrainAction = step.brainAction ?: lastBrainAction,
            lastSafetyDecision = step.safetyDecision ?: lastSafetyDecision,
            lastCommandResult = step.commandResult ?: lastCommandResult,
            lastVerification = step.verification ?: lastVerification,
            stuckCount = if (step.verification?.screenVerification?.changed == false) {
                stuckCount + 1
            } else {
                stuckCount
            },
            userFacingStatus = step.message ?: userFacingStatus,
            stopReason = step.stopReason ?: stopReason,
            completionStatus = step.status,
            history = history + step
        )
    }

    fun withRetryCount(value: Int, reason: String? = null): AgentLoopState {
        return copy(
            retryCount = value,
            lastUpdatedAtMillis = System.currentTimeMillis(),
            userFacingStatus = reason ?: userFacingStatus
        )
    }

    fun withStop(
        reason: AgentLoopStopReason,
        message: String,
        status: TaskStepStatus = TaskStepStatus.STOPPED
    ): AgentLoopState {
        return copy(
            stopReason = reason,
            completionStatus = status,
            userFacingStatus = message,
            lastUpdatedAtMillis = System.currentTimeMillis()
        )
    }
}
