package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `real device grocery smoke phrases stay in the grocery booking branch`() {
        val cases = listOf(
            "Luna order milk and bread from grocery" to mapOf(
                "itemCount" to "2",
                "item1Name" to "milk",
                "item2Name" to "bread"
            ),
            "Nova compare prices for atta rice and sugar" to mapOf(
                "compareRequested" to "true",
                "itemCount" to "2",
                "item1Name" to "atta rice",
                "item2Name" to "sugar"
            ),
            "Reorder my previous grocery list" to mapOf(
                "reorderMode" to "true",
                "previousListMode" to "true",
                "itemCount" to "0"
            ),
            "Use Blinkit for grocery" to mapOf("preferredProvider" to "BLINKIT"),
            "Use JioMart for grocery" to mapOf("preferredProvider" to "JIOMART"),
            "Use Instamart for grocery" to mapOf("preferredProvider" to "INSTAMART"),
            "Apply coupon if available" to mapOf("applyCouponRequested" to "true"),
            "Use wallet balance only if it reaches confirmation safely" to mapOf("paymentPreference" to "WALLET")
        )

        cases.forEach { (text, expectedEntities) ->
            val result = parser.parse(text)

            assertNotNull(result)
            assertEquals("Unexpected intent for '$text'", IntentType.GROCERY_BOOKING, result.intentType)
            assertEquals("Unexpected action for '$text'", ActionType.GROCERY_BOOKING, result.actionType)
            expectedEntities.forEach { (key, expectedValue) ->
                assertEquals("Unexpected entity '$key' for '$text'", expectedValue, result.entities[key])
            }
        }
    }

    @Test
    fun `non grocery commands stay out of grocery routing`() {
        val cab = parser.parse("book a cab to DB Mall")
        val phone = parser.parse("call mom")
        val navigation = parser.parse("open notifications")
        val communication = parser.parse("summarize my messages")
        val generic = parser.parse("what can you do")
        val bypassLogin = parser.parse("bypass login")

        assertEquals(IntentType.CAB_BOOKING, cab.intentType)
        assertEquals(ActionType.CAB_BOOKING, cab.actionType)

        assertEquals(IntentType.SENSITIVE, phone.intentType)
        assertEquals(ActionType.CALL_CONTACT, phone.actionType)

        assertEquals(IntentType.NAVIGATION, navigation.intentType)
        assertEquals(ActionType.OPEN_NOTIFICATIONS, navigation.actionType)

        assertEquals(IntentType.COMMUNICATION, communication.intentType)
        assertEquals(ActionType.COMMUNICATION, communication.actionType)

        assertEquals(IntentType.UNKNOWN, generic.intentType)
        assertEquals(ActionType.UNKNOWN, generic.actionType)

        assertEquals(IntentType.BLOCKED, bypassLogin.intentType)
        assertEquals(ActionType.BLOCKED, bypassLogin.actionType)

        listOf(cab, phone, navigation, communication, generic, bypassLogin).forEach { intent ->
            assertTrue(intent.intentType != IntentType.GROCERY_BOOKING)
            assertTrue(intent.actionType != ActionType.GROCERY_BOOKING)
        }
    }
}
