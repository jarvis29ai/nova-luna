package com.nova.luna.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowApplication
import java.util.*

@RunWith(RobolectricTestRunner::class)
class VoiceInputControllerTest {

    private lateinit var context: Context
    private lateinit var controller: VoiceInputController
    private lateinit var fakeRecognizer: FakeSpeechRecognizer
    private var lastState: VoiceInputState = VoiceInputState.IDLE
    private var lastResult: VoiceInputResult? = null
    private var lastError: VoiceInputError? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeRecognizer = FakeSpeechRecognizer()
        controller = VoiceInputController(
            context,
            VoiceCommandNormalizer(),
            availabilityCheck = { true }
        ) { _, listener ->
            fakeRecognizer.listener = listener
            fakeRecognizer
        }
        controller.setVoiceInputListener(object : VoiceInputController.VoiceInputListener {
            override fun onStateChanged(state: VoiceInputState) { lastState = state }
            override fun onPartialTranscript(text: String) {}
            override fun onFinalResult(result: VoiceInputResult) { lastResult = result }
            override fun onError(error: VoiceInputError, message: String) { lastError = error }
        })
    }

    @Test
    fun `startListening returns PERMISSION_REQUIRED when permission missing`() {
        // Robolectric by default doesn't grant permissions
        controller.startListening()
        assertEquals(VoiceInputState.PERMISSION_REQUIRED, lastState)
        assertEquals(VoiceInputError.MICROPHONE_PERMISSION_MISSING, lastError)
    }

    @Test
    fun `startListening moves to READY and then LISTENING when speech starts`() {
        grantPermission(Manifest.permission.RECORD_AUDIO)
        controller.startListening()
        
        assertEquals(VoiceInputState.READY, lastState)
        
        fakeRecognizer.listener?.onReadyForSpeech(null)
        assertEquals(VoiceInputState.LISTENING, lastState)
    }

    @Test
    fun `controller handles valid result from recognizer`() {
        grantPermission(Manifest.permission.RECORD_AUDIO)
        controller.startListening()
        fakeRecognizer.listener?.onReadyForSpeech(null)
        
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("Luna open settings"))
        }
        fakeRecognizer.listener?.onResults(bundle)
        
        assertEquals(VoiceInputState.TRANSCRIPT_READY, lastState)
        assertNotNull(lastResult)
        assertEquals("open settings", lastResult?.cleanedCommand)
        assertTrue(lastResult?.shouldSendToBrain == true)
    }

    @Test
    fun `controller handles empty result`() {
        grantPermission(Manifest.permission.RECORD_AUDIO)
        controller.startListening()
        
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(""))
        }
        fakeRecognizer.listener?.onResults(bundle)
        
        assertEquals(VoiceInputState.NO_SPEECH, lastState)
    }

    @Test
    fun `cancelListening moves to CANCELLED state`() {
        grantPermission(Manifest.permission.RECORD_AUDIO)
        controller.startListening()
        controller.cancelListening()
        
        assertEquals(VoiceInputState.CANCELLED, lastState)
        assertTrue(fakeRecognizer.wasCancelled)
    }

    private fun grantPermission(permission: String) {
        val appShadow = Shadows.shadowOf(context as android.app.Application)
        appShadow.grantPermissions(permission)
    }

    class FakeSpeechRecognizer : VoiceInputController.SpeechRecognizerWrapper {
        var listener: RecognitionListener? = null
        var wasStarted = false
        var wasStopped = false
        var wasCancelled = false
        var wasDestroyed = false

        override fun startListening(intent: Intent) { wasStarted = true }
        override fun stopListening() { wasStopped = true }
        override fun cancel() { wasCancelled = true }
        override fun destroy() { wasDestroyed = true }
    }
}
