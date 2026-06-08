package com.nova.luna.ui

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.BrainRiskLevel

enum class AssistantPopupState {
    IDLE,
    LISTENING,
    THINKING,
    DOING_ACTION,
    NEED_CONFIRMATION,
    COMPLETED,
    FAILED,
    BLOCKED,
    PERMISSION_REQUIRED,
    CANCELLED,
    HIDDEN
}

data class AssistantPopupUiModel(
    val state: AssistantPopupState = AssistantPopupState.IDLE,
    val title: String? = null,
    val subtitle: String? = null,
    val transcript: String? = null,
    val spokenText: String? = null,
    val detectedIntent: String? = null,
    val detectedDomain: String? = null,
    val detectedModelName: String? = null,
    val currentActionLabel: String? = null,
    val currentAppName: String? = null,
    val progressText: String? = null,
    val confirmationTitle: String? = null,
    val confirmationMessage: String? = null,
    val confirmationRiskLevel: BrainRiskLevel = BrainRiskLevel.SAFE,
    val confirmationActionSummary: String? = null,
    val primaryButtonText: String? = "Continue",
    val secondaryButtonText: String? = "Cancel",
    val showMicButton: Boolean = false,
    val showCancelButton: Boolean = false,
    val showContinueButton: Boolean = false,
    val showLoader: Boolean = false,
    val showTranscript: Boolean = false,
    val showSafetyWarning: Boolean = false,
    val showResultSummary: Boolean = false,
    val resultSummary: String? = null,
    val errorMessage: String? = null,
    val blockedReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AssistantPopupEvent {
    MIC_TAPPED,
    CANCEL_TAPPED,
    CONTINUE_TAPPED,
    CLOSE_TAPPED,
    TEXT_COMMAND_SUBMITTED,
    CONFIRMATION_VOICE_ACCEPTED,
    CONFIRMATION_VOICE_REJECTED
}
