package com.nova.luna.voice

import com.nova.luna.ui.AssistantPersonality

enum class VoiceState {
    IDLE,
    PERMISSION_REQUIRED,
    LISTENING,
    PROCESSING,
    RECOGNIZED,
    COMMAND_RUNNING,
    SPEAKING,
    FALLBACK_KEYBOARD,
    ERROR
}

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

enum class VoiceError {
    MICROPHONE_PERMISSION_MISSING,
    SPEECH_RECOGNIZER_UNAVAILABLE,
    NO_SPEECH_MATCH,
    NETWORK_ERROR,
    RECOGNIZER_BUSY,
    AUDIO_ERROR,
    TTS_UNAVAILABLE,
    TTS_INIT_FAILED,
    UNKNOWN
}

data class VoiceInputResult(
    val status: VoiceState,
    val rawTranscript: String = "",
    val cleanedCommand: String = "",
    val wasWakeWordDetected: Boolean = false,
    val shouldSendToBrain: Boolean = false
)

data class VoiceOutputResult(
    val status: VoiceState,
    val text: String = "",
    val success: Boolean = true,
    val error: VoiceError? = null
)

data class VoiceEvent(
    val state: VoiceState,
    val partialText: String? = null,
    val finalText: String? = null,
    val error: VoiceError? = null,
    val errorMessage: String? = null
)
