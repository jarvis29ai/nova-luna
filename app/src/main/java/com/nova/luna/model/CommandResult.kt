package com.nova.luna.model

import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.PendingConfirmationType
import com.nova.luna.memory.RecoveryState

data class CommandResult(
    val success: Boolean,
    val status: ActionResultStatus = if (success) ActionResultStatus.SUCCESS else ActionResultStatus.FAILED,
    val message: String,
    val domain: com.nova.luna.brain.UnifiedDomain = com.nova.luna.brain.UnifiedDomain.UNKNOWN,
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
    val memoryMetadata: Map<String, String> = emptyMap(),
    val technicalReason: String? = null,
    val retryAttempted: Boolean = false,
    val nextSuggestedAction: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val screenSnapshotSummary: String? = entities["screenSummary"]
) {
    companion object {
        fun success(
            message: String,
            technicalReason: String? = null,
            domain: com.nova.luna.brain.UnifiedDomain = com.nova.luna.brain.UnifiedDomain.UNKNOWN,
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
                status = ActionResultStatus.SUCCESS,
                message = message,
                technicalReason = technicalReason,
                domain = domain,
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
            status: ActionResultStatus = ActionResultStatus.FAILED,
            technicalReason: String? = null,
            retryAttempted: Boolean = false,
            domain: com.nova.luna.brain.UnifiedDomain = com.nova.luna.brain.UnifiedDomain.UNKNOWN,
            memorySessionType: BrainSessionType? = null,
            memorySessionId: String? = null,
            pendingConfirmationType: PendingConfirmationType? = null,
            screenMemorySnapshotId: String? = null,
            recoveryState: RecoveryState? = null,
            memoryMetadata: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                status = status,
                message = message,
                technicalReason = technicalReason,
                retryAttempted = retryAttempted,
                domain = domain,
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
            domain: com.nova.luna.brain.UnifiedDomain = com.nova.luna.brain.UnifiedDomain.UNKNOWN,
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
                status = ActionResultStatus.BLOCKED,
                message = message,
                domain = domain,
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
            domain: com.nova.luna.brain.UnifiedDomain = com.nova.luna.brain.UnifiedDomain.UNKNOWN,
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
                status = ActionResultStatus.NEEDS_CONFIRMATION,
                message = message,
                domain = domain,
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
                status = ActionResultStatus.NEEDS_CONFIRMATION,
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

        fun permissionRequired(
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
                status = ActionResultStatus.PERMISSION_REQUIRED,
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
    }
}
