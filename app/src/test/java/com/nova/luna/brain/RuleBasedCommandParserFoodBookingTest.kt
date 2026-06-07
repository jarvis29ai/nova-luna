package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedCommandParserFoodBookingTest {
    private val parser = RuleBasedCommandParser()

    @Test
    fun `provider specific food order stays in the food booking branch`() {
        val result = parser.parse("Luna order paneer pizza from Domino's on Swiggy")

        assertEquals(IntentType.FOOD_ORDER, result.intentType)
        assertEquals(ActionType.FOOD_ORDER, result.actionType)
        assertEquals("paneer pizza", result.entities["foodItem"])
        assertEquals("Domino's", result.entities["restaurantName"])
        assertEquals("SWIGGY", result.entities["preferredProvider"])
    }

    @Test
    fun `grocery orders on swiggy still route to grocery booking`() {
        val result = parser.parse("Luna order milk and bread on Swiggy")

        assertEquals(IntentType.GROCERY_BOOKING, result.intentType)
        assertEquals(ActionType.GROCERY_BOOKING, result.actionType)
        assertEquals("INSTAMART", result.entities["preferredProvider"])
        assertEquals("2", result.entities["itemCount"])
    }

    @Test
    fun `restaurant style food phrases stay in the food booking branch`() {
        listOf(
            "order pizza from Domino's",
            "order burger from Zomato"
        ).forEach { text ->
            val result = parser.parse(text)

            assertEquals(IntentType.FOOD_ORDER, result.intentType)
            assertEquals(ActionType.FOOD_ORDER, result.actionType)
        }
    }

    @Test
    fun `table booking phrases stay out of grocery routing`() {
        val result = parser.parse("book a table at restaurant")

        assertTrue(result.intentType != IntentType.GROCERY_BOOKING)
        assertTrue(result.actionType != ActionType.GROCERY_BOOKING)
    }
}
