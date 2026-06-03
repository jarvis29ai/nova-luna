package com.nova.luna.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.nova.luna.brain.CommandBrain
import com.nova.luna.data.AppDatabase
import com.nova.luna.data.CommandHistoryEntity
import com.nova.luna.data.buildCommandHistoryEntity
import com.nova.luna.data.PreferencesManager
import com.nova.luna.model.CommandResult
import com.nova.luna.model.VoiceProfile
import com.nova.luna.tts.TextToSpeechManager
import com.nova.luna.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceCommandService : Service(), RecognitionListener {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var commandBrain: CommandBrain
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var historyDao: com.nova.luna.data.CommandHistoryDao

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentVoiceProfile: VoiceProfile = VoiceProfile.NOVA
    private var isListening = false
    private var isStopping = false
    private var restartDelayMs = 400L
    private var pendingRestart: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        commandBrain = CommandBrain(applicationContext)
        ttsManager = TextToSpeechManager(applicationContext)
        historyDao = AppDatabase.getInstance(applicationContext).commandHistoryDao()

        NotificationHelper.ensureChannel(this)
        createSpeechRecognizer()
        observePreferences()
        startForeground(
            NOTIFICATION_ID,
            NotificationHelper.buildServiceNotification(
                this,
                "Voice assistant idle.",
                listening = false
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_LISTENING -> {
                stopListeningAndShutdown(speakMessage = null)
                return START_NOT_STICKY
            }

            ACTION_START_LISTENING, null -> {
                if (!PermissionUtils.hasRecordAudioPermission(this)) {
                    speakThenStop("Microphone permission is missing. Open the app and grant audio access.")
                    return START_NOT_STICKY
                }
                if (speechRecognizer == null) {
                    speakThenStop("Speech recognition is not available on this device.")
                    return START_NOT_STICKY
                }
                isStopping = false
                updateNotification("Listening with ${currentVoiceProfile.displayName}.", true)
                startListeningInternal()
                return START_STICKY
            }

            else -> {
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        isStopping = true
        clearRestart()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        ttsManager.release()
        serviceJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onReadyForSpeech(params: Bundle?) {
        updateNotification("Listening for commands...", true)
    }

    override fun onBeginningOfSpeech() {
        updateNotification("Hearing speech...", true)
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        isListening = false
    }

    override fun onError(error: Int) {
        if (isStopping) return

        Log.w(TAG, "SpeechRecognizer error: $error")
        isListening = false
        restartDelayMs = (restartDelayMs * 2).coerceAtMost(2000L)
        updateNotification("Retrying microphone in ${restartDelayMs}ms.", true)
        scheduleRestart()
    }

    override fun onResults(results: Bundle?) {
        if (isStopping) return

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val spokenText = matches.firstOrNull { it.isNotBlank() }.orEmpty()
        if (spokenText.isBlank()) {
            updateNotification("No speech result captured. Restarting.", true)
            restartDelayMs = (restartDelayMs * 2).coerceAtMost(2000L)
            scheduleRestart()
            return
        }

        handleRecognizedText(spokenText)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun createSpeechRecognizer() {
        speechRecognizer = if (SpeechRecognizer.isRecognitionAvailable(this)) {
            SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(this@VoiceCommandService)
            }
        } else {
            null
        }
    }

    private fun observePreferences() {
        serviceScope.launch {
            preferencesManager.voiceProfileFlow.collectLatest { profile ->
                currentVoiceProfile = profile
                ttsManager.applyProfile(profile)
            }
        }

        ttsManager.prepare(currentVoiceProfile)
    }

    private fun handleRecognizedText(text: String) {
        val normalized = text.trim()

        val result = commandBrain.process(normalized)
        serviceScope.launch(Dispatchers.IO) {
            historyDao.insert(buildCommandHistoryEntity(text, normalized, result))
        }
        speakResult(result)
    }

    private fun speakResult(result: CommandResult) {
        clearRestart()
        isListening = false
        updateNotification(result.message, listening = !result.shouldStopListening)
        ttsManager.speak(result.message) {
            if (!isStopping) {
                if (result.shouldStopListening) {
                    stopListeningAndShutdown(speakMessage = null)
                } else {
                    restartDelayMs = 400L
                    startListeningInternal()
                }
            }
        }
    }

    private fun startListeningInternal() {
        if (isStopping || isListening) return
        val recognizer = speechRecognizer ?: return
        if (!PermissionUtils.hasRecordAudioPermission(this)) {
            speakThenStop("Microphone permission is missing.")
            return
        }

        clearRestart()
        try {
            recognizer.cancel()
            recognizer.startListening(buildRecognizerIntent())
            isListening = true
            updateNotification("Listening with ${currentVoiceProfile.displayName}.", true)
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to start recognition", throwable)
            isListening = false
            restartDelayMs = (restartDelayMs * 2).coerceAtMost(2000L)
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (isStopping) return
        clearRestart()
        pendingRestart = Runnable { startListeningInternal() }
        mainHandler.postDelayed(pendingRestart!!, restartDelayMs)
    }

    private fun clearRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    private fun stopListeningAndShutdown(speakMessage: String?) {
        if (isStopping) return
        isStopping = true
        clearRestart()
        isListening = false
        speechRecognizer?.cancel()

        if (speakMessage == null) {
            updateNotification("Voice assistant stopped.", listening = false)
            finishShutdown()
        } else {
            updateNotification(speakMessage, listening = false)
            ttsManager.speak(speakMessage) {
                finishShutdown()
            }
        }
    }

    private fun speakThenStop(message: String) {
        isStopping = true
        clearRestart()
        isListening = false
        updateNotification(message, listening = false)
        ttsManager.speak(message) {
            finishShutdown()
        }
    }

    private fun finishShutdown() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
    }

    private fun updateNotification(status: String, listening: Boolean) {
        NotificationHelper.notifyStatus(this, status, listening)
    }

    companion object {
        const val ACTION_START_LISTENING = "com.nova.luna.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.nova.luna.action.STOP_LISTENING"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "VoiceCommandService"
    }
}
