package com.nova.luna.ui

import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandResult

enum class AssistantUiStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    BLOCKED,
    NEEDS_CONFIRMATION,
    FAILED
}

data class AssistantUiState(
    val personality: AssistantPersonality = AssistantPersonality.LUNA,
    val status: AssistantUiStatus = AssistantUiStatus.IDLE,
    val progressMessage: String? = null,
    val lastCommand: String? = null,
    val lastResult: AssistantUiResult? = null
)

data class AssistantUiResult(
    val requestId: String,
    val personality: AssistantPersonality,
    val commandText: String,
    val status: AssistantUiStatus,
    val progressMessage: String?,
    val resultTitle: String?,
    val resultMessage: String?,
    val actionType: String? = null,
    val riskLevel: String? = null,
    val safetyDecision: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

fun CommandResult.toAssistantUiResult(
    requestId: String,
    personality: AssistantPersonality,
    commandText: String
): AssistantUiResult {
    val uiStatus = when (this.status) {
        ActionResultStatus.SUCCESS -> AssistantUiStatus.COMPLETED
        ActionResultStatus.FAILED -> AssistantUiStatus.FAILED
        ActionResultStatus.BLOCKED -> AssistantUiStatus.BLOCKED
        ActionResultStatus.NEEDS_CONFIRMATION -> AssistantUiStatus.NEEDS_CONFIRMATION
        ActionResultStatus.PERMISSION_REQUIRED -> AssistantUiStatus.FAILED
        else -> AssistantUiStatus.FAILED
    }

    return AssistantUiResult(
        requestId = requestId,
        personality = personality,
        commandText = commandText,
        status = uiStatus,
        progressMessage = null,
        resultTitle = if (this.success) "Success" else "Action Required",
        resultMessage = this.message,
        actionType = this.actionType.name,
        riskLevel = this.safetyDecision.level.name,
        safetyDecision = this.safetyDecision.status.name,
        errorCode = if (!this.success) this.status.name else null,
        errorMessage = this.technicalReason,
        timestampMs = this.timestamp
    )
}
