package com.nova.luna.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.ui.AssistantPersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoiceInputControllerTest {

    private lateinit var context: Context
    private lateinit var controller: AndroidSpeechRecognizerVoiceInputController
    private lateinit var listener: RecordingListener

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = AndroidSpeechRecognizerVoiceInputController(
            context = context,
            normalizer = VoiceCommandNormalizer()
        )
        listener = RecordingListener()
        controller.setListener(listener)
    }

    @Test
    fun `startListening without microphone permission reports permission required`() {
        controller.startListening(AssistantPersonality.LUNA)

        assertEquals(VoiceState.PERMISSION_REQUIRED, listener.lastState)
        assertEquals(VoiceError.MICROPHONE_PERMISSION_MISSING, listener.lastError)
        assertFalse(controller.isListening())
    }

    @Test
    fun `cancelListening without active session is a no-op`() {
        controller.cancelListening()

        assertEquals(VoiceState.IDLE, listener.lastState)
        assertFalse(controller.isListening())
    }

    private class RecordingListener : VoiceInputController.Listener {
        var lastState: VoiceState = VoiceState.IDLE
        var lastError: VoiceError? = null

        override fun onListeningStarted() {
            lastState = VoiceState.LISTENING
        }

        override fun onPartialText(text: String) {
            // No-op
        }

        override fun onFinalText(result: VoiceInputResult) {
            lastState = result.status
        }

        override fun onError(error: VoiceError, message: String) {
            lastError = error
        }

        override fun onListeningStopped() {
            if (lastState == VoiceState.LISTENING) {
                lastState = VoiceState.PROCESSING
            }
        }

        override fun onStateChanged(state: VoiceState) {
            lastState = state
        }
    }
}
