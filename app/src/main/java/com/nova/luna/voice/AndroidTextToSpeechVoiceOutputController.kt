package com.nova.luna.voice

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.nova.luna.ui.AssistantPersonality
import java.util.Locale

class AndroidTextToSpeechVoiceOutputController(
    private val context: Context
) : VoiceOutputController, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var listener: VoiceOutputController.Listener? = null
    private var isInitialized = false
    private var isSpeaking = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun setListener(listener: VoiceOutputController.Listener) {
        this.listener = listener
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    listener?.onSpeechStarted()
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    listener?.onSpeechCompleted()
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    listener?.onSpeechError(VoiceError.AUDIO_ERROR, "TTS Error")
                }
            })
        } else {
            isInitialized = false
            listener?.onSpeechError(VoiceError.TTS_INIT_FAILED, "TTS Initialization failed")
        }
    }

    override fun speak(text: String, personality: AssistantPersonality) {
        if (!isInitialized) {
            listener?.onSpeechError(VoiceError.TTS_UNAVAILABLE, "TTS not initialized")
            return
        }

        // Apply personality pitch/rate
        val pitch = if (personality == AssistantPersonality.LUNA) 1.2f else 0.8f
        val rate = 1.0f
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)

        val utteranceId = "nova_luna_${System.currentTimeMillis()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    override fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
        isSpeaking = false
    }

    override fun isSpeaking(): Boolean = isSpeaking
}
