package com.nova.luna.cab

import com.nova.luna.brain.RuleBasedCommandParser
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CabSafetyGateTest {
    private val safetyGate = SafetyGate()
    private val parser = RuleBasedCommandParser()

    @Test
    fun `sensitive commands are blocked`() {
        listOf("otp", "payment", "password", "captcha").forEach { phrase ->
            val decision = safetyGate.evaluate(
                CommandIntent(
                    rawText = phrase,
                    intentType = IntentType.UNKNOWN,
                    actionType = ActionType.UNKNOWN
                )
            )

            assertFalse("Expected block for phrase: $phrase", decision.allowed)
        }
    }

    @Test
    fun `cab booking preparation is allowed`() {
        val parsed = parser.parse("book cab to DB Mall")
        val decision = safetyGate.evaluate(parsed)

        assertTrue(decision.allowed)
        assertFalse(decision.requiresBiometric)
    }
}
