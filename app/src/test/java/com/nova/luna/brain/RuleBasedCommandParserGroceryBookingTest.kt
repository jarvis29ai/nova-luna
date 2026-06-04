package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RuleBasedCommandParserGroceryBookingTest {
    private val parser = RuleBasedCommandParser()

    @Test
    fun `grocery phrases route into the grocery booking branch`() {
        val result = parser.parse("Luna order milk and bread")

        assertNotNull(result)
        assertEquals(IntentType.GROCERY_BOOKING, result.intentType)
        assertEquals(ActionType.GROCERY_BOOKING, result.actionType)
        assertEquals("2", result.entities["itemCount"])
        assertEquals("milk", result.entities["item1Name"])
        assertEquals("bread", result.entities["item2Name"])
    }
}
