package com.nova.luna.voice

import com.nova.luna.ui.AssistantPersonality

interface VoiceInputController {
    interface Listener {
        fun onListeningStarted()
        fun onPartialText(text: String)
        fun onFinalText(result: VoiceInputResult)
        fun onError(error: VoiceError, message: String)
        fun onListeningStopped()
        fun onStateChanged(state: VoiceState)
    }

    fun setListener(listener: Listener)
    fun startListening(personality: AssistantPersonality)
    fun stopListening()
    fun cancelListening()
    fun isListening(): Boolean
    fun destroy()
}
