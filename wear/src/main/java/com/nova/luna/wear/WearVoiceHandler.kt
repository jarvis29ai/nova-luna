package com.nova.luna.wear

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class WearVoiceHandler(private val context: Context) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var commandCallback: ((String) -> Unit)? = null

    fun canRecognizeSpeech(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(onCommand: (String) -> Unit): Boolean {
        commandCallback = onCommand
        if (!canRecognizeSpeech()) {
            return false
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@WearVoiceHandler)
            }
        }

        return try {
            recognizer?.cancel()
            recognizer?.startListening(buildIntent())
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "Wear speech start failed", throwable)
            false
        }
    }

    fun release() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        Log.w(TAG, "Wear speech error: $error")
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val spokenText = matches.firstOrNull { it.isNotBlank() }.orEmpty()
        if (spokenText.isNotBlank()) {
            commandCallback?.invoke(spokenText)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun buildIntent() = RecognizerIntent.ACTION_RECOGNIZE_SPEECH.let { action ->
        android.content.Intent(action).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
    }

    companion object {
        private const val TAG = "WearVoiceHandler"
    }
}
