package com.nova.luna.ui

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.BrainRiskLevel

enum class AssistantPopupState {
    IDLE,
    WAKE_DETECTED,
    LISTENING,
    THINKING,
    ACTION_READY,
    RUNNING_ACTION,
    RUNNING_MOCK_ACTION,
    SUCCESS,
    ERROR,
    CONFIRMATION_REQUIRED,
    LOCK_REQUIRED,
    PRIVACY_BLOCKED,
    DOING_ACTION, // Legacy/Internal
    NEED_CONFIRMATION, // Legacy/Internal
    COMPLETED, // Legacy/Internal
    FAILED, // Legacy/Internal
    BLOCKED,
    PERMISSION_REQUIRED,
    CANCELLED,
    HIDDEN
}

data class AssistantPopupUiModel(
    val state: AssistantPopupState = AssistantPopupState.IDLE,
    val personality: AssistantPersonality = AssistantPersonality.LUNA,
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
    val showMicButton: Boolean = true,
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
    CONFIRMATION_VOICE_REJECTED,
    PERSONALITY_CHANGED,
    WAKE_LUNA_MOCK,
    WAKE_NOVA_MOCK
}
