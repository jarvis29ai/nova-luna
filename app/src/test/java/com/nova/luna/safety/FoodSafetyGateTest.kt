package com.nova.luna.safety

import com.nova.luna.food.FoodProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodSafetyGateTest {
    private val safetyGate = SafetyGate()

    @Test
    fun `unsafe food items are blocked`() {
        val decision = safetyGate.evaluate(
            CommandIntent(
                rawText = "order beer",
                intentType = IntentType.FOOD_ORDER,
                actionType = ActionType.FOOD_ORDER,
                entities = mapOf("foodItem" to "beer")
            )
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("unsafe food orders"))
    }

    @Test
    fun `safe food orders require confirmation`() {
        val decision = safetyGate.evaluate(
            CommandIntent(
                rawText = "order sandwich",
                intentType = IntentType.FOOD_ORDER,
                actionType = ActionType.FOOD_ORDER,
                entities = mapOf("foodItem" to "sandwich")
            )
        )

        // Phase 24: "order" requires confirmation
        assertFalse(decision.allowed)
        assertEquals(com.nova.luna.model.SafetyStatus.CONFIRMATION_REQUIRED, decision.status)
        assertTrue(decision.reason.contains("Food ordering flow allowed"))
    }
}
