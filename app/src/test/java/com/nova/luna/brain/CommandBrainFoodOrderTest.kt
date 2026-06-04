package com.nova.luna.brain

import androidx.test.core.app.ApplicationProvider
import com.nova.luna.food.FoodProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainFoodOrderTest {
    private val brain = CommandBrain(ApplicationProvider.getApplicationContext())

    @Test
    fun `food command routes into the food flow and asks for restaurant when missing`() {
        val result = brain.process("Order burger")

        assertEquals(IntentType.FOOD_ORDER, result.intentType)
        assertEquals(ActionType.FOOD_ORDER, result.actionType)
        assertEquals("Which restaurant should I search for?", result.message)
        assertTrue(result.success)
    }
}
