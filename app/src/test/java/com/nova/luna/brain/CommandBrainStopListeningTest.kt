package com.nova.luna.brain

import android.content.Context
import android.content.pm.PackageManager
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class CommandBrainStopListeningTest {
    @Test
    fun `stop listening is parsed as control and allowed by safety gate`() {
        val parser = RuleBasedCommandParser()

        val parsed = parser.parse("Luna stop listening")
        val decision = SafetyGate().evaluate(parsed)

        assertEquals(IntentType.CONTROL, parsed.intentType)
        assertEquals(ActionType.STOP_SERVICE, parsed.actionType)
        assertTrue(decision.allowed)
        assertFalse(decision.requiresBiometric)
        assertEquals("Stop command accepted.", decision.message)
    }

    @Test
    fun `command brain routes stop listening to shutdown result`() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.packageManager).thenReturn(packageManager)
        Mockito.`when`(context.filesDir).thenReturn(java.io.File("."))

        val brain = CommandBrain(context, personalMemoryStore = com.nova.luna.memory.FakePersonalMemoryStore())
        val result = brain.process("Luna stop listening")

        assertTrue(result.success)
        assertTrue(result.shouldStopListening)
        assertEquals(IntentType.CONTROL, result.intentType)
        assertEquals(ActionType.STOP_SERVICE, result.actionType)
        assertEquals("Stopping listening.", result.message)
    }

    @Test
    fun `shutdown phrases all route through the same stop service path`() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.packageManager).thenReturn(packageManager)
        Mockito.`when`(context.filesDir).thenReturn(java.io.File("."))

        val brain = CommandBrain(context, personalMemoryStore = com.nova.luna.memory.FakePersonalMemoryStore())
        val phrases = listOf(
            "cancel listening",
            "stop voice",
            "stop speaking",
            "stop service",
            "quiet",
            "be quiet"
        )

        phrases.forEach { phrase ->
            val result = brain.process(phrase)
            assertTrue("Expected shutdown for phrase: $phrase", result.success)
            assertTrue("Expected stop listening flag for phrase: $phrase", result.shouldStopListening)
            assertEquals("Expected control intent for phrase: $phrase", IntentType.CONTROL, result.intentType)
            assertEquals("Expected stop action for phrase: $phrase", ActionType.STOP_SERVICE, result.actionType)
            assertEquals("Stopping listening.", result.message)
        }
    }
}
