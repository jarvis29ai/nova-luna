package com.nova.luna.voice

import com.nova.luna.ui.AssistantPersonality

interface VoiceOutputController {
    interface Listener {
        fun onSpeechStarted()
        fun onSpeechCompleted()
        fun onSpeechError(error: VoiceError, message: String)
    }

    fun setListener(listener: Listener)
    fun speak(text: String, personality: AssistantPersonality)
    fun stop()
    fun shutdown()
    fun isSpeaking(): Boolean
}
