package com.nova.luna.ui

import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandResult
import com.nova.luna.voice.VoiceInputState
import com.nova.luna.voice.VoiceResponseState

class AssistantPopupStateMapper {

    fun mapFromVoiceInput(state: VoiceInputState, partialTranscript: String = ""): AssistantPopupUiModel {
        return when (state) {
            VoiceInputState.IDLE -> AssistantPopupUiModel(state = AssistantPopupState.IDLE)
            VoiceInputState.PERMISSION_REQUIRED -> AssistantPopupUiModel(
                state = AssistantPopupState.PERMISSION_REQUIRED,
                title = "Microphone Required",
                errorMessage = "Microphone permission is needed so I can listen."
            )
            VoiceInputState.READY -> AssistantPopupUiModel(
                state = AssistantPopupState.LISTENING,
                title = "Listening",
                subtitle = "I am ready"
            )
            VoiceInputState.LISTENING -> AssistantPopupUiModel(
                state = AssistantPopupState.LISTENING,
                title = "Listening",
                transcript = partialTranscript,
                showTranscript = partialTranscript.isNotBlank(),
                showMicButton = true
            )
            VoiceInputState.PROCESSING -> AssistantPopupUiModel(
                state = AssistantPopupState.THINKING,
                title = "Thinking",
                showLoader = true
            )
            VoiceInputState.TRANSCRIPT_READY -> AssistantPopupUiModel(
                state = AssistantPopupState.THINKING,
                title = "Thinking",
                transcript = partialTranscript,
                showTranscript = true,
                showLoader = true
            )
            VoiceInputState.NO_SPEECH -> AssistantPopupUiModel(
                state = AssistantPopupState.FAILED,
                title = "No Speech Detected",
                errorMessage = "I didn't hear that. Please try again."
            )
            VoiceInputState.ERROR -> AssistantPopupUiModel(
                state = AssistantPopupState.FAILED,
                title = "Recognition Error",
                errorMessage = "Something went wrong with speech recognition."
            )
            VoiceInputState.CANCELLED -> AssistantPopupUiModel(
                state = AssistantPopupState.CANCELLED,
                title = "Cancelled"
            )
        }
    }

    fun mapFromCommandResult(result: CommandResult): AssistantPopupUiModel {
        return when (result.status) {
            ActionResultStatus.SUCCESS -> AssistantPopupUiModel(
                state = AssistantPopupState.COMPLETED,
                title = "Done",
                resultSummary = result.message,
                showResultSummary = true
            )
            ActionResultStatus.FAILED -> AssistantPopupUiModel(
                state = AssistantPopupState.FAILED,
                title = "Failed",
                errorMessage = result.message
            )
            ActionResultStatus.BLOCKED -> AssistantPopupUiModel(
                state = AssistantPopupState.BLOCKED,
                title = "Blocked",
                blockedReason = result.message,
                showSafetyWarning = true
            )
            ActionResultStatus.NEEDS_CONFIRMATION -> AssistantPopupUiModel(
                state = AssistantPopupState.NEED_CONFIRMATION,
                confirmationTitle = "Confirm Action",
                confirmationMessage = result.message,
                showContinueButton = true,
                showCancelButton = true,
                showSafetyWarning = true
            )
            ActionResultStatus.NOT_FOUND -> AssistantPopupUiModel(
                state = AssistantPopupState.FAILED,
                title = "Not Found",
                errorMessage = result.message
            )
            ActionResultStatus.TIMEOUT -> AssistantPopupUiModel(
                state = AssistantPopupState.FAILED,
                title = "Timeout",
                errorMessage = "The app did not respond."
            )
            ActionResultStatus.UNSUPPORTED -> AssistantPopupUiModel(
                state = AssistantPopupState.FAILED,
                title = "Unsupported",
                errorMessage = "I cannot do that yet."
            )
            ActionResultStatus.PERMISSION_REQUIRED -> AssistantPopupUiModel(
                state = AssistantPopupState.PERMISSION_REQUIRED,
                title = "Permission Needed",
                errorMessage = result.message
            )
        }
    }
}
