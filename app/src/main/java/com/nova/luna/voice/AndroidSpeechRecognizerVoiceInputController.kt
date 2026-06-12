package com.nova.luna.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.nova.luna.ui.AssistantPersonality
import java.util.Locale

class AndroidSpeechRecognizerVoiceInputController(
    private val context: Context,
    private val normalizer: VoiceCommandNormalizer = VoiceCommandNormalizer()
) : VoiceInputController {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: VoiceInputController.Listener? = null
    private var isListening = false
    private var currentState = VoiceState.IDLE

    override fun setListener(listener: VoiceInputController.Listener) {
        this.listener = listener
    }

    override fun startListening(personality: AssistantPersonality) {
        if (isListening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateState(VoiceState.PERMISSION_REQUIRED)
            listener?.onError(VoiceError.MICROPHONE_PERMISSION_MISSING, "Microphone permission is needed.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            updateState(VoiceState.ERROR)
            listener?.onError(VoiceError.SPEECH_RECOGNIZER_UNAVAILABLE, "Speech recognition is not available.")
            return
        }

        try {
            ensureRecognizerCreated()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
            updateState(VoiceState.LISTENING)
            listener?.onListeningStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            updateState(VoiceState.ERROR)
            listener?.onError(VoiceError.UNKNOWN, e.message ?: "Unknown error")
        }
    }

    override fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()
        isListening = false
        updateState(VoiceState.PROCESSING)
        listener?.onListeningStopped()
    }

    override fun cancelListening() {
        if (!isListening) return
        speechRecognizer?.cancel()
        isListening = false
        updateState(VoiceState.IDLE)
        listener?.onListeningStopped()
    }

    override fun isListening(): Boolean = isListening

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        updateState(VoiceState.IDLE)
    }

    private fun ensureRecognizerCreated() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(InternalRecognitionListener())
            }
        }
    }

    private fun updateState(state: VoiceState) {
        currentState = state
        listener?.onStateChanged(state)
    }

    private inner class InternalRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            updateState(VoiceState.LISTENING)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            updateState(VoiceState.PROCESSING)
        }

        override fun onError(error: Int) {
            isListening = false
            val voiceError = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> VoiceError.AUDIO_ERROR
                SpeechRecognizer.ERROR_CLIENT -> VoiceError.UNKNOWN
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.MICROPHONE_PERMISSION_MISSING
                SpeechRecognizer.ERROR_NETWORK -> VoiceError.NETWORK_ERROR
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NETWORK_ERROR
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_SPEECH_MATCH
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.RECOGNIZER_BUSY
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_SPEECH_MATCH
                SpeechRecognizer.ERROR_SERVER -> VoiceError.NETWORK_ERROR
                else -> VoiceError.UNKNOWN
            }
            updateState(VoiceState.ERROR)
            listener?.onError(voiceError, "Speech recognition error: $error")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val rawTranscript = matches?.get(0) ?: ""
            
            if (rawTranscript.isBlank()) {
                updateState(VoiceState.ERROR)
                listener?.onError(VoiceError.NO_SPEECH_MATCH, "No speech detected.")
                return
            }

            val cleanedCommand = normalizer.normalize(rawTranscript)
            val wasWakeWordDetected = normalizer.isWakeWordDetected(rawTranscript)
            val isValid = normalizer.isValidCommand(cleanedCommand)

            val result = VoiceInputResult(
                status = VoiceState.RECOGNIZED,
                rawTranscript = rawTranscript,
                cleanedCommand = cleanedCommand,
                wasWakeWordDetected = wasWakeWordDetected,
                shouldSendToBrain = isValid
            )

            updateState(VoiceState.RECOGNIZED)
            listener?.onFinalText(result)
            }


        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.get(0) ?: ""
            if (text.isNotBlank()) {
                listener?.onPartialText(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "AndroidVoiceInput"
    }
}
