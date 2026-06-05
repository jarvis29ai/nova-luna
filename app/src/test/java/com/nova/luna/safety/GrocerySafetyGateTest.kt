package com.nova.luna.safety

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
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
}
