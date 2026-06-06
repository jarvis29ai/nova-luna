package com.nova.luna.safety

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrocerySafetyGateTest {
    private val safetyGate = SafetyGate()

    @Test
    fun `grocery booking allows order and checkout but blocks payment steps`() {
        val allowed = safetyGate.evaluate(
            CommandIntent(
                rawText = "buy milk and bread",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        )
        val checkout = safetyGate.evaluate(
            CommandIntent(
                rawText = "checkout groceries",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        )
        val blocked = safetyGate.evaluate(
            CommandIntent(
                rawText = "buy milk and pay now",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        )

        assertTrue(allowed.allowed)
        assertTrue(checkout.allowed)
        assertFalse(blocked.allowed)
    }

    @Test
    fun `grocery booking blocks final order and payment boundary phrases`() {
        listOf(
            "pay now",
            "enter OTP",
            "complete payment",
            "place final order",
            "bypass login",
            "solve captcha"
        ).forEach { text ->
            val decision = safetyGate.evaluate(
                CommandIntent(
                    rawText = text,
                    intentType = IntentType.GROCERY_BOOKING,
                    actionType = ActionType.GROCERY_BOOKING
                )
            )

            assertFalse("Expected $text to stay manual", decision.allowed)
            assertTrue("Expected $text to be blocked or human-only", decision.level == com.nova.luna.model.SafetyLevel.BLOCKED || decision.level == com.nova.luna.model.SafetyLevel.HUMAN_ONLY)
        }
    }

    @Test
    fun `place final order is treated as a manual grocery boundary`() {
        val decision = safetyGate.evaluate(
            CommandIntent(
                rawText = "place final order",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        )

        assertEquals(com.nova.luna.model.SafetyLevel.BLOCKED, decision.level)
        assertFalse(decision.allowed)
    }

    @Test
    fun `grocery planning phrases that stop before payment stay allowed`() {
        listOf(
            "apply coupon to 1 litre milk and 1 bread but stop before payment",
            "use wallet balance for 1 litre milk but stop at final confirmation/manual payment boundary"
        ).forEach { text ->
            val decision = safetyGate.evaluate(
                CommandIntent(
                    rawText = text,
                    intentType = IntentType.GROCERY_BOOKING,
                    actionType = ActionType.GROCERY_BOOKING
                )
            )

            assertTrue("Expected '$text' to stay in the safe grocery planning path", decision.allowed)
            assertEquals(com.nova.luna.model.SafetyLevel.SAFE, decision.level)
        }
    }
}
