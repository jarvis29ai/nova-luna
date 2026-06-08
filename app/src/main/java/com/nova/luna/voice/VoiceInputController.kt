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
import java.util.Locale

class VoiceInputController(
    private val context: Context,
    private val normalizer: VoiceCommandNormalizer = VoiceCommandNormalizer(),
    private val availabilityCheck: (Context) -> Boolean = { ctx -> SpeechRecognizer.isRecognitionAvailable(ctx) },
    private val recognizerProvider: (Context, RecognitionListener) -> SpeechRecognizerWrapper = { ctx, listener ->
        AndroidSpeechRecognizerWrapper(ctx, listener)
    }
) {
    interface VoiceInputListener {
        fun onStateChanged(state: VoiceInputState)
        fun onPartialTranscript(text: String)
        fun onFinalResult(result: VoiceInputResult)
        fun onError(error: VoiceInputError, message: String)
    }

    interface SpeechRecognizerWrapper {
        fun startListening(intent: Intent)
        fun stopListening()
        fun cancel()
        fun destroy()
    }

    private class AndroidSpeechRecognizerWrapper(
        context: Context,
        listener: RecognitionListener
    ) : SpeechRecognizerWrapper {
        private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }

        override fun startListening(intent: Intent) = recognizer.startListening(intent)
        override fun stopListening() = recognizer.stopListening()
        override fun cancel() = recognizer.cancel()
        override fun destroy() = recognizer.destroy()
    }

    private var speechRecognizer: SpeechRecognizerWrapper? = null
    private var listener: VoiceInputListener? = null
    private var isListening = false
    private var currentState = VoiceInputState.IDLE

    fun setVoiceInputListener(listener: VoiceInputListener) {
        this.listener = listener
    }

    fun startListening(languageCode: String = Locale.getDefault().toLanguageTag()) {
        if (isListening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateState(VoiceInputState.PERMISSION_REQUIRED)
            listener?.onError(VoiceInputError.MICROPHONE_PERMISSION_MISSING, "Microphone permission is needed.")
            return
        }

        if (!availabilityCheck(context)) {
            updateState(VoiceInputState.ERROR)
            listener?.onError(VoiceInputError.SPEECH_RECOGNIZER_UNAVAILABLE, "Speech recognition is not available.")
            return
        }

        try {
            ensureRecognizerCreated()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
            updateState(VoiceInputState.READY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            updateState(VoiceInputState.ERROR)
            listener?.onError(VoiceInputError.UNKNOWN_ERROR, e.message ?: "Unknown error")
        }
    }

    fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()
        isListening = false
        updateState(VoiceInputState.PROCESSING)
    }

    fun cancelListening() {
        if (!isListening) return
        speechRecognizer?.cancel()
        isListening = false
        updateState(VoiceInputState.CANCELLED)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        updateState(VoiceInputState.IDLE)
    }

    private fun ensureRecognizerCreated() {
        if (speechRecognizer == null) {
            speechRecognizer = recognizerProvider(context, InternalRecognitionListener())
        }
    }

    private fun updateState(state: VoiceInputState) {
        currentState = state
        listener?.onStateChanged(state)
    }

    private inner class InternalRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            updateState(VoiceInputState.LISTENING)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            updateState(VoiceInputState.PROCESSING)
        }

        override fun onError(error: Int) {
            isListening = false
            val voiceError = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> VoiceInputError.NETWORK_OR_RECOGNIZER_ERROR
                SpeechRecognizer.ERROR_CLIENT -> VoiceInputError.NETWORK_OR_RECOGNIZER_ERROR
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputError.MICROPHONE_PERMISSION_MISSING
                SpeechRecognizer.ERROR_NETWORK -> VoiceInputError.NETWORK_OR_RECOGNIZER_ERROR
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceInputError.AUDIO_TIMEOUT
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceInputError.NO_SPEECH_DETECTED
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceInputError.SPEECH_RECOGNIZER_UNAVAILABLE
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputError.AUDIO_TIMEOUT
                else -> VoiceInputError.UNKNOWN_ERROR
            }
            updateState(VoiceInputState.ERROR)
            listener?.onError(voiceError, "Speech recognition error: $error")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val rawTranscript = matches?.get(0) ?: ""
            
            if (rawTranscript.isBlank()) {
                updateState(VoiceInputState.NO_SPEECH)
                listener?.onError(VoiceInputError.EMPTY_TRANSCRIPT, "No speech detected.")
                return
            }

            val cleanedCommand = normalizer.normalize(rawTranscript)
            val wasWakeWordDetected = normalizer.isWakeWordDetected(rawTranscript)
            val isValid = normalizer.isValidCommand(cleanedCommand)

            val result = VoiceInputResult(
                status = VoiceInputState.TRANSCRIPT_READY,
                rawTranscript = rawTranscript,
                cleanedCommand = cleanedCommand,
                wasWakeWordDetected = wasWakeWordDetected,
                shouldSendToBrain = isValid
            )

            updateState(VoiceInputState.TRANSCRIPT_READY)
            listener?.onFinalResult(result)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.get(0) ?: ""
            if (text.isNotBlank()) {
                listener?.onPartialTranscript(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "VoiceInputController"
    }
}
