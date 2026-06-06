package com.nova.luna.grocery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `bare numbered grocery items keep their quantity and item name`() {
        val result = parser.parseInitialGroceryRequest("Luna order 1 litre milk and 1 bread from grocery")

        assertNotNull(result)
        assertEquals(2, result!!.basket.items.size)
        assertEquals("regular", result.brandPreference)
        assertEquals("milk", result.basket.items[0].name)
        assertEquals(1.0, result.basket.items[0].quantityValue ?: 0.0, 0.0)
        assertEquals("1 litre", result.basket.items[0].quantityText)
        assertEquals("bread", result.basket.items[1].name)
        assertEquals(1.0, result.basket.items[1].quantityValue ?: 0.0, 0.0)
        assertEquals("1", result.basket.items[1].quantityText)
    }

    @Test
    fun `provider specific grocery requests keep brand and provider preference`() {
        listOf(
            "Luna order Amul milk and brown bread on Blinkit" to GroceryProvider.BLINKIT,
            "Luna order Amul milk and brown bread on JioMart" to GroceryProvider.JIOMART,
            "Luna order Amul milk and brown bread on Instamart" to GroceryProvider.INSTAMART
        ).forEach { (text, provider) ->
            val result = parser.parseInitialGroceryRequest(text)

            assertNotNull(result)
            assertTrue(result!!.isGroceryBooking)
            assertEquals(provider, result.providerPreference)
            assertEquals(2, result.basket.items.size)
            assertEquals("milk", result.basket.items[0].name)
            assertEquals("brown bread", result.basket.items[1].name)
            assertEquals("Amul", result.basket.items[0].brand)
        }
    }

    @Test
    fun `provider specific prompts stay in grocery without needing a basket`() {
        listOf(
            "order from Blinkit" to GroceryProvider.BLINKIT,
            "use JioMart" to GroceryProvider.JIOMART,
            "use Instamart" to GroceryProvider.INSTAMART
        ).forEach { (text, provider) ->
            val result = parser.parseInitialGroceryRequest(text)

            assertNotNull(result)
            assertTrue(result!!.isGroceryBooking)
            assertEquals(provider, result.providerPreference)
        }
    }

    @Test
    fun `compare and cheapest grocery prompts keep comparison intent`() {
        val compare = parser.parseInitialGroceryRequest("compare prices for milk and bread")
        val cheapest = parser.parseInitialGroceryRequest("which app is cheapest for these groceries")

        assertNotNull(compare)
        assertTrue(compare!!.compareRequested)
        assertEquals(2, compare.basket.items.size)
        assertEquals("milk", compare.basket.items[0].name)
        assertEquals("bread", compare.basket.items[1].name)

        assertNotNull(cheapest)
        assertTrue(cheapest!!.isGroceryBooking)
        assertEquals(GroceryBudgetPreference.CHEAPEST, cheapest.budgetPreference)
        assertTrue(cheapest.wantsCheapest)
    }

    @Test
    fun `reorder prompts stay in grocery reorder mode`() {
        val reorder = parser.parseInitialGroceryRequest("reorder my last grocery order")
        val sameAgain = parser.parseInitialGroceryRequest("buy the same groceries again")
        val previousList = parser.parseInitialGroceryRequest("reorder previous grocery list")

        assertNotNull(reorder)
        assertTrue(reorder!!.reorderMode)
        assertTrue(reorder.previousListMode)
        assertTrue(reorder.basket.items.isEmpty())

        assertNotNull(sameAgain)
        assertTrue(sameAgain!!.reorderMode)
        assertTrue(sameAgain.previousListMode)

        assertNotNull(previousList)
        assertTrue(previousList!!.reorderMode)
        assertTrue(previousList.previousListMode)
        assertTrue(previousList.basket.items.isEmpty())
    }

    @Test
    fun `wallet and coupon prompts are captured as grocery payment helpers`() {
        val wallet = parser.parseInitialGroceryRequest("pay with wallet")
        val balance = parser.parseInitialGroceryRequest("use wallet balance")
        val coupon = parser.parseInitialGroceryRequest("apply coupon")

        assertNotNull(wallet)
        assertEquals(GroceryPaymentMethod.WALLET, wallet!!.paymentPreference)

        assertNotNull(balance)
        assertEquals(GroceryPaymentMethod.WALLET, balance!!.paymentPreference)

        assertNotNull(coupon)
        assertTrue(coupon!!.applyCouponRequested)
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
    fun `food and restaurant phrases stay out of grocery`() {
        listOf(
            "order pizza from Domino's",
            "order burger from Zomato",
            "book a table at restaurant"
        ).forEach { text ->
            assertNull(parser.parseInitialGroceryRequest(text))
        }
    }

    @Test
    fun `informational grocery questions stay out of grocery booking`() {
        assertNull(parser.parseInitialGroceryRequest("what is the price of rice today?"))
        assertNull(parser.parseInitialGroceryRequest("tell me about groceries"))
        assertNull(parser.parseInitialGroceryRequest("Luna what is the weather?"))
    }
}
