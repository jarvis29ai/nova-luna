package com.nova.luna.voice

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus

enum class VoiceResponseState {
    IDLE,
    INITIALIZING,
    READY,
    SPEAKING,
    COMPLETED,
    ERROR,
    MUTED,
    STOPPED,
    TTS_UNAVAILABLE
}

enum class VoiceResponseType {
    LISTENING,
    THINKING,
    NEED_DETAIL,
    CONFIRMATION,
    SUCCESS,
    FAILURE,
    BLOCKED,
    PERMISSION_REQUIRED,
    CANCELLED,
    SUMMARY,
    SAFE_STATUS,
    DEBUG_ONLY
}

data class VoiceResponseRequest(
    val type: VoiceResponseType,
    val message: String,
    val priority: VoiceResponsePriority = VoiceResponsePriority.NORMAL,
    val interruptCurrent: Boolean = false,
    val allowSensitiveSpeech: Boolean = false,
    val source: String? = null,
    val relatedActionType: ActionType? = null,
    val relatedResultStatus: ActionResultStatus? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val languageCode: String? = null
)

enum class VoiceResponsePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

data class VoiceResponseResult(
    val state: VoiceResponseState,
    val spokenText: String = "",
    val userMessage: String = "",
    val technicalReason: String? = null,
    val errorCode: VoiceResponseError? = null,
    val wasInterrupted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class VoiceResponseError {
    TTS_INIT_FAILED,
    TTS_LANGUAGE_UNAVAILABLE,
    TTS_ENGINE_MISSING,
    TTS_SPEAK_FAILED,
    EMPTY_MESSAGE,
    MUTED_BY_USER,
    SENSITIVE_CONTENT_BLOCK,
    UNKNOWN_ERROR
}
