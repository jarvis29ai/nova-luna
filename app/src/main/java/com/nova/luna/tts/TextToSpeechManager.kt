package com.nova.luna.tts

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.nova.luna.model.VoiceProfile
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

open class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {
    private data class PendingSpeech(
        val text: String,
        val onDone: (() -> Unit)?
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackMap = ConcurrentHashMap<String, () -> Unit>()
    private val pendingQueue = ArrayDeque<PendingSpeech>()
    private var engine: TextToSpeech? = null
    @Volatile
    private var ready = false
    @Volatile
    private var currentProfile: VoiceProfile = VoiceProfile.NOVA

    fun prepare(profile: VoiceProfile) {
        currentProfile = profile
        if (engine == null) {
            engine = TextToSpeech(context.applicationContext, this)
            return
        }
        applyProfile(profile)
    }

    fun applyProfile(profile: VoiceProfile) {
        currentProfile = profile
        if (!ready) return
        val tts = engine ?: return
        tts.setPitch(profile.pitch)
        tts.setSpeechRate(profile.speechRate)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            Log.w(TAG, "TextToSpeech initialization failed with status=$status")
            return
        }

        val tts = engine ?: return
        tts.language = Locale.getDefault()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                dispatchCallback(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                dispatchCallback(utteranceId)
            }
        })
        applyProfile(currentProfile)
        flushPendingQueue()
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        if (!ready || engine == null) {
            synchronized(pendingQueue) {
                pendingQueue.addLast(PendingSpeech(text, onDone))
            }
            return
        }

        speakNow(text, onDone)
    }

    fun stop() {
        callbackMap.clear()
        engine?.stop()
    }

    fun release() {
        callbackMap.clear()
        synchronized(pendingQueue) {
            pendingQueue.clear()
        }
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun speakNow(text: String, onDone: (() -> Unit)?) {
        val tts = engine ?: return
        val utteranceId = "nova_luna_${System.currentTimeMillis()}"
        if (onDone != null) {
            callbackMap[utteranceId] = onDone
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            if (onDone != null) {
                mainHandler.postDelayed(onDone, 300L)
            }
        }
    }

    private fun flushPendingQueue() {
        val items = mutableListOf<PendingSpeech>()
        synchronized(pendingQueue) {
            while (pendingQueue.isNotEmpty()) {
                items.add(pendingQueue.removeFirst())
            }
        }
        items.forEach { pending ->
            speakNow(pending.text, pending.onDone)
        }
    }

    private fun dispatchCallback(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = callbackMap.remove(utteranceId) ?: return
        mainHandler.post { callback.invoke() }
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
    }
}

