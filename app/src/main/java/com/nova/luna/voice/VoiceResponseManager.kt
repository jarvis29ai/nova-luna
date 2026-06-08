package com.nova.luna.voice

import android.content.Context
import com.nova.luna.tts.TextToSpeechManager
import com.nova.luna.model.VoiceProfile
import android.util.Log

class VoiceResponseManager(
    private val context: Context,
    private val ttsManager: TextToSpeechManager = TextToSpeechManager(context),
    private val templates: VoiceResponseTemplates = VoiceResponseTemplates(),
    private val sanitizer: VoiceResponseSanitizer = VoiceResponseSanitizer()
) {
    private var voiceResponsesEnabled = true
    private var lastSpokenMessage: String? = null
    private var lastSpokenTime: Long = 0

    fun setEnabled(enabled: Boolean) {
        this.voiceResponsesEnabled = enabled
    }

    fun prepare(profile: VoiceProfile) {
        ttsManager.prepare(profile)
    }

    fun speak(request: VoiceResponseRequest, onDone: (() -> Unit)? = null): VoiceResponseResult {
        if (!voiceResponsesEnabled) {
            return VoiceResponseResult(VoiceResponseState.MUTED, userMessage = request.message)
        }

        if (request.message.isBlank()) {
            return VoiceResponseResult(
                VoiceResponseState.ERROR,
                errorCode = VoiceResponseError.EMPTY_MESSAGE,
                technicalReason = "Empty message provided"
            )
        }

        // Avoid repeating the same message too quickly (e.g. within 2 seconds)
        if (request.message == lastSpokenMessage && System.currentTimeMillis() - lastSpokenTime < 2000) {
            return VoiceResponseResult(VoiceResponseState.STOPPED, technicalReason = "Duplicate message suppressed")
        }

        if (request.interruptCurrent || request.priority == VoiceResponsePriority.URGENT || request.priority == VoiceResponsePriority.HIGH) {
            ttsManager.stop()
        }

        val sanitizedMessage = sanitizer.sanitize(request.message, request.allowSensitiveSpeech)
        
        ttsManager.speak(sanitizedMessage) {
            onDone?.invoke()
        }

        lastSpokenMessage = request.message
        lastSpokenTime = System.currentTimeMillis()

        return VoiceResponseResult(
            VoiceResponseState.SPEAKING,
            spokenText = sanitizedMessage,
            userMessage = request.message
        )
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun release() {
        ttsManager.release()
    }
}
