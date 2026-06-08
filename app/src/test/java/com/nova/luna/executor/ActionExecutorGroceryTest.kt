package com.nova.luna.executor

import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionExecutorGroceryTest {

    @Test
    fun `grocery booking routes through the grocery orchestrator instead of the scaffolded fallback`() {
        val executor = ActionExecutor(ApplicationProvider.getApplicationContext())

        val result = executor.execute(
            CommandIntent(
                rawText = "Luna order milk and bread from grocery",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        )

        assertEquals(IntentType.GROCERY_BOOKING, result.intentType)
        assertEquals(ActionType.GROCERY_BOOKING, result.actionType)
        assertTrue("Grocery executor should not fall back to the scaffolded stub", result.message != "Grocery booking is scaffolded.")
        assertNotNull(result.entities["groceryState"])
    }

    @Test
    fun `grocery conversation bootstrap starts a fresh session for compare style commands`() {
        val executor = ActionExecutor(ApplicationProvider.getApplicationContext())

        val result = executor.handleGroceryBookingText(
            rawText = "Luna compare milk prices",
            commandIntent = CommandIntent(
                rawText = "Luna compare milk prices",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        )

        assertEquals(IntentType.GROCERY_BOOKING, result.intentType)
        assertEquals(ActionType.GROCERY_BOOKING, result.actionType)
        assertTrue("Grocery compare should not fail when no session is active", result.success)
        assertNotEquals(
            "I could not complete the grocery flow: no active grocery booking session.",
            result.message
        )
        assertNotNull(result.entities["groceryState"])
    }
}
