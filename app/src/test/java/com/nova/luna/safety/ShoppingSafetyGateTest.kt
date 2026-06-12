package com.nova.luna.safety

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingSafetyGateTest {
    private val safetyGate = SafetyGate()

    @Test
    fun `shopping planning requires confirmation while secrets stay manual`() {
        val allowed = safetyGate.evaluate(
            CommandIntent(
                rawText = "buy a phone with budget 30000",
                intentType = IntentType.SHOPPING,
                actionType = ActionType.SHOPPING
            )
        )

        val blocked = safetyGate.evaluate(
            CommandIntent(
                rawText = "buy a phone and enter OTP",
                intentType = IntentType.SHOPPING,
                actionType = ActionType.SHOPPING
            )
        )

        // Phase 24: "buy" requires confirmation
        assertFalse(allowed.allowed)
        assertEquals(com.nova.luna.model.SafetyStatus.CONFIRMATION_REQUIRED, allowed.status)
        
        assertFalse(blocked.allowed)
        assertEquals(com.nova.luna.model.SafetyStatus.BLOCKED, blocked.status)
    }
}
