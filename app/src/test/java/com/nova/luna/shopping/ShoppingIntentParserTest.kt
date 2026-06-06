package com.nova.luna.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingIntentParserTest {

    private val parser = ShoppingIntentParser()

    @Test
    fun testParseBuyPhone() {
        val text = "Luna I want to buy a new phone"
        val request = parser.parse(text)
        assertEquals(ShoppingCommandType.BUY_PRODUCT, request.commandType)
        assertEquals(ShoppingProductCategory.PHONE, request.category)
        assertTrue(request.buyIntent)
    }

    @Test
    fun testParseFindLaptopWithBudget() {
        val text = "Nova find best laptop under 60000"
        val request = parser.parse(text)
        assertEquals(ShoppingCommandType.SEARCH_PRODUCT, request.commandType)
        assertEquals(ShoppingProductCategory.LAPTOP, request.category)
        assertEquals(60000.0, request.budget!!, 0.1)
    }

    @Test
    fun testParseGamingPhone() {
        val text = "Buy a phone for gaming"
        val request = parser.parse(text)
        assertEquals(ShoppingPurpose.GAMING, request.purpose)
    }

    @Test
    fun testParseCompareDeals() {
        val text = "Compare Amazon and Flipkart for headphones"
        val request = parser.parse(text)
        assertEquals(ShoppingCommandType.COMPARE_PRODUCTS, request.commandType)
        assertEquals(ShoppingProductCategory.HEADPHONES, request.category)
        assertTrue(request.comparisonIntent)
    }

    @Test
    fun testParseCancel() {
        val text = "Cancel shopping"
        val request = parser.parse(text)
        assertEquals(ShoppingCommandType.CANCEL, request.commandType)
    }
}
