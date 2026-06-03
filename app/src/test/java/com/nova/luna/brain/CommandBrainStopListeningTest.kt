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

        val parsed = parser.parse("stop listening")
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

        val brain = CommandBrain(context)
        val result = brain.process("stop listening")

        assertTrue(result.success)
        assertTrue(result.shouldStopListening)
        assertEquals(IntentType.CONTROL, result.intentType)
        assertEquals(ActionType.STOP_SERVICE, result.actionType)
        assertEquals("Stopping listening.", result.message)
    }
}
