package com.nova.luna.voice

import com.nova.luna.model.ActionResultStatus

enum class VoiceInputState {
    IDLE,
    PERMISSION_REQUIRED,
    READY,
    LISTENING,
    PROCESSING,
    TRANSCRIPT_READY,
    NO_SPEECH,
    ERROR,
    CANCELLED
}

data class VoiceInputResult(
    val status: VoiceInputState,
    val rawTranscript: String = "",
    val cleanedCommand: String = "",
    val confidence: Float = 1.0f,
    val languageCode: String? = null,
    val errorMessage: String? = null,
    val technicalReason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val wasWakeWordDetected: Boolean = false,
    val shouldSendToBrain: Boolean = false
)

enum class VoiceInputError {
    MICROPHONE_PERMISSION_MISSING,
    SPEECH_RECOGNIZER_UNAVAILABLE,
    NETWORK_OR_RECOGNIZER_ERROR,
    NO_SPEECH_DETECTED,
    AUDIO_TIMEOUT,
    USER_CANCELLED,
    EMPTY_TRANSCRIPT,
    UNSUPPORTED_LANGUAGE,
    UNKNOWN_ERROR
}
