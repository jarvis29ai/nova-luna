package com.nova.luna.grocery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryIntentParserTest {
    private val parser = GroceryIntentParser()

    @Test
    fun `initial grocery requests parse items and comparison flags`() {
        val result = parser.parseInitialGroceryRequest("compare blinkit for milk and bread")

        assertNotNull(result)
        assertTrue(result!!.compareRequested)
        assertEquals(GroceryProvider.BLINKIT, result.providerPreference)
        assertEquals(2, result.basket.items.size)
    }

    @Test
    fun `wake-word grocery requests parse the full basket`() {
        val result = parser.parseInitialGroceryRequest("Luna order milk and bread")

        assertNotNull(result)
        assertTrue(result!!.isGroceryBooking)
        assertEquals(2, result.basket.items.size)
        assertEquals("milk", result.basket.items[0].name)
        assertEquals("bread", result.basket.items[1].name)
    }

    @Test
    fun `follow up add command parses a grocery item`() {
        val followUp = parser.parseFollowUpCommand("add 2 bread packets")

        assertNotNull(followUp)
        assertEquals(GroceryFollowUpType.ADD_ITEM, followUp!!.type)
        assertEquals("bread", followUp.item?.name)
        assertEquals(2.0, followUp.item?.quantityValue ?: 0.0, 0.0)
    }

    @Test
    fun `initial grocery requests capture quantities and wake words`() {
        val result = parser.parseInitialGroceryRequest("Luna, I want 1 kg sugar, brown bread, and milk")

        assertNotNull(result)
        assertEquals(3, result!!.basket.items.size)
        assertEquals("sugar", result.basket.items[0].name)
        assertEquals(1.0, result.basket.items[0].quantityValue ?: 0.0, 0.0)
        assertEquals("kg", result.basket.items[0].unit)
        assertEquals("brown bread", result.basket.items[1].name)
        assertEquals("milk", result.basket.items[2].name)
    }

    @Test
    fun `follow up proceed command confirms the selected grocery option`() {
        val followUp = parser.parseFollowUpCommand("Proceed with this one")

        assertNotNull(followUp)
        assertEquals(GroceryFollowUpType.CONFIRM, followUp!!.type)
        assertTrue(followUp.finalUserConfirmed)
    }

    @Test
    fun `follow up back and dismiss commands cancel the grocery flow`() {
        listOf("go back", "back", "dismiss").forEach { text ->
            val followUp = parser.parseFollowUpCommand(text)

            assertNotNull(followUp)
            assertEquals(GroceryFollowUpType.CANCEL, followUp!!.type)
        }
    }

    @Test
    fun `compare requests keep all grocery items structured`() {
        val result = parser.parseInitialGroceryRequest("compare sugar, bread, and atta on Blinkit, JioMart, and Instamart")

        assertNotNull(result)
        assertTrue(result!!.compareRequested)
        assertEquals(GroceryProvider.BLINKIT, result.providerPreference)
        assertEquals(3, result.basket.items.size)
        assertEquals("sugar", result.basket.items[0].name)
        assertEquals("bread", result.basket.items[1].name)
        assertEquals("atta", result.basket.items[2].name)
    }

    @Test
    fun `normal questions are not misclassified as grocery requests`() {
        assertEquals(null, parser.parseInitialGroceryRequest("Luna what is the weather?"))
    }
}
