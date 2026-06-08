package com.nova.luna.voice

import android.content.Context
import com.nova.luna.tts.TextToSpeechManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
class VoiceResponseManagerTest {
    private lateinit var context: Context
    private lateinit var mockTtsManager: TextToSpeechManager
    private lateinit var manager: VoiceResponseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockTtsManager = mock(TextToSpeechManager::class.java)
        manager = VoiceResponseManager(context, mockTtsManager)
    }

    @Test
    fun `speak calls ttsManager when enabled`() {
        val request = VoiceResponseRequest(VoiceResponseType.SUCCESS, "Done")
        val result = manager.speak(request)
        
        assertEquals(VoiceResponseState.SPEAKING, result.state)
        verify(mockTtsManager).speak(anyString(), anyOrNull())
    }

    @Test
    fun `speak returns MUTED when disabled`() {
        manager.setEnabled(false)
        val request = VoiceResponseRequest(VoiceResponseType.SUCCESS, "Done")
        val result = manager.speak(request)
        
        assertEquals(VoiceResponseState.MUTED, result.state)
        verify(mockTtsManager, never()).speak(anyString(), anyOrNull())
    }

    @Test
    fun `speak suppresses duplicates within 2 seconds`() {
        val request = VoiceResponseRequest(VoiceResponseType.SUCCESS, "Done")
        manager.speak(request)
        val result = manager.speak(request)
        
        assertEquals(VoiceResponseState.STOPPED, result.state)
        verify(mockTtsManager, times(1)).speak(anyString(), anyOrNull())
    }

    @Test
    fun `urgent priority interrupts current speech`() {
        val request = VoiceResponseRequest(VoiceResponseType.BLOCKED, "Blocked", priority = VoiceResponsePriority.URGENT)
        manager.speak(request)
        
        verify(mockTtsManager).stop()
        verify(mockTtsManager).speak(anyString(), anyOrNull())
    }

    private fun <T> anyOrNull(): T {
        any<T>()
        return null as T
    }
}
