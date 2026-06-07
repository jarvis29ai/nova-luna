package com.nova.luna.model

import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.PendingConfirmationType
import com.nova.luna.memory.RecoveryState

data class CommandResult(
    val success: Boolean,
    val message: String,
    val intentType: IntentType = IntentType.UNKNOWN,
    val actionType: ActionType = ActionType.UNKNOWN,
    val safetyDecision: SafetyDecision = SafetyDecision.allow(),
    val shouldStopListening: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val entities: Map<String, String> = emptyMap(),
    val memorySessionType: BrainSessionType? = null,
    val memorySessionId: String? = null,
    val pendingConfirmationType: PendingConfirmationType? = null,
    val screenMemorySnapshotId: String? = null,
    val recoveryState: RecoveryState? = null,
    val memoryMetadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun success(
            message: String,
            intentType: IntentType = IntentType.UNKNOWN,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap(),
            shouldStopListening: Boolean = false,
            memorySessionType: BrainSessionType? = null,
            memorySessionId: String? = null,
            pendingConfirmationType: PendingConfirmationType? = null,
            screenMemorySnapshotId: String? = null,
            recoveryState: RecoveryState? = null,
            memoryMetadata: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = true,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.allow(),
                shouldStopListening = shouldStopListening,
                entities = entities,
                memorySessionType = memorySessionType,
                memorySessionId = memorySessionId,
                pendingConfirmationType = pendingConfirmationType,
                screenMemorySnapshotId = screenMemorySnapshotId,
                recoveryState = recoveryState,
                memoryMetadata = memoryMetadata
            )
        }

        fun failure(
            message: String,
            intentType: IntentType = IntentType.UNKNOWN,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap(),
            memorySessionType: BrainSessionType? = null,
            memorySessionId: String? = null,
            pendingConfirmationType: PendingConfirmationType? = null,
            screenMemorySnapshotId: String? = null,
            recoveryState: RecoveryState? = null,
            memoryMetadata: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.allow(),
                entities = entities,
                memorySessionType = memorySessionType,
                memorySessionId = memorySessionId,
                pendingConfirmationType = pendingConfirmationType,
                screenMemorySnapshotId = screenMemorySnapshotId,
                recoveryState = recoveryState,
                memoryMetadata = memoryMetadata
            )
        }

        fun blocked(
            message: String,
            intentType: IntentType = IntentType.BLOCKED,
            actionType: ActionType = ActionType.BLOCKED,
            entities: Map<String, String> = emptyMap(),
            memorySessionType: BrainSessionType? = null,
            memorySessionId: String? = null,
            pendingConfirmationType: PendingConfirmationType? = null,
            screenMemorySnapshotId: String? = null,
            recoveryState: RecoveryState? = null,
            memoryMetadata: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.block(message),
                entities = entities,
                memorySessionType = memorySessionType,
                memorySessionId = memorySessionId,
                pendingConfirmationType = pendingConfirmationType,
                screenMemorySnapshotId = screenMemorySnapshotId,
                recoveryState = recoveryState,
                memoryMetadata = memoryMetadata
            )
        }

        fun confirmationRequired(
            message: String,
            intentType: IntentType = IntentType.UNKNOWN,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap(),
            memorySessionType: BrainSessionType? = null,
            memorySessionId: String? = null,
            pendingConfirmationType: PendingConfirmationType? = null,
            screenMemorySnapshotId: String? = null,
            recoveryState: RecoveryState? = null,
            memoryMetadata: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.requireConfirmation(message),
                awaitingConfirmation = true,
                entities = entities,
                memorySessionType = memorySessionType,
                memorySessionId = memorySessionId,
                pendingConfirmationType = pendingConfirmationType,
                screenMemorySnapshotId = screenMemorySnapshotId,
                recoveryState = recoveryState,
                memoryMetadata = memoryMetadata
            )
        }

        fun biometricRequired(
            message: String,
            intentType: IntentType = IntentType.SENSITIVE,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap(),
            memorySessionType: BrainSessionType? = null,
            memorySessionId: String? = null,
            pendingConfirmationType: PendingConfirmationType? = null,
            screenMemorySnapshotId: String? = null,
            recoveryState: RecoveryState? = null,
            memoryMetadata: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.requireBiometric(message),
                entities = entities,
                memorySessionType = memorySessionType,
                memorySessionId = memorySessionId,
                pendingConfirmationType = pendingConfirmationType,
                screenMemorySnapshotId = screenMemorySnapshotId,
                recoveryState = recoveryState,
                memoryMetadata = memoryMetadata
            )
        }
    }
}
