package com.nova.luna.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodIntentParserTest {
    private val parser = FoodIntentParser()

    @Test
    fun `I want to eat sandwich extracts food item`() {
        val request = parser.parse("I want to eat sandwich")

        assertNotNull(request)
        assertTrue(parser.isFoodOrderCommand("I want to eat sandwich"))
        assertEquals("sandwich", request?.foodItem)
        assertNull(request?.restaurantName)
        assertNull(request?.quantity)
        assertTrue(request?.requestedProviders.isNullOrEmpty())
    }

    @Test
    fun `book burger from Subway extracts food item and restaurant`() {
        val request = parser.parse("Book burger from Subway")

        assertNotNull(request)
        assertEquals("burger", request?.foodItem)
        assertEquals("Subway", request?.restaurantName)
    }

    @Test
    fun `wake-word food requests keep the food and restaurant details`() {
        val request = parser.parse("Luna order paneer pizza from Domino's")

        assertNotNull(request)
        assertEquals("paneer pizza", request?.foodItem)
        assertEquals("Domino's", request?.restaurantName)
    }

    @Test
    fun `Get paneer roll from XYZ restaurant extracts restaurant name`() {
        val request = parser.parse("Get paneer roll from XYZ restaurant")

        assertNotNull(request)
        assertEquals("paneer roll", request?.foodItem)
        assertEquals("XYZ restaurant", request?.restaurantName)
        assertNull(request?.preferredProvider)
    }

    @Test
    fun `compare pizza on Swiggy and Zomato captures platform preferences`() {
        val request = parser.parse("Compare pizza on Swiggy and Zomato")

        assertNotNull(request)
        assertEquals("pizza", request?.foodItem)
        assertEquals(listOf(FoodProvider.SWIGGY, FoodProvider.ZOMATO), request?.requestedProviders)
        assertNull(request?.preferredProvider)
    }

    @Test
    fun `Book cheapest is recognized as a cheapest choice reply`() {
        assertTrue(parser.isCheapestChoice("Book cheapest"))
    }

    @Test
    fun `Book cheapest is not misread as a brand new food order`() {
        assertNull(parser.parse("Book cheapest"))
    }

    @Test
    fun `normal questions are not misclassified as food orders`() {
        assertNull(parser.parse("Luna what is the weather?"))
    }

    @Test
    fun `quantity coupon and platform preference are extracted together`() {
        val request = parser.parse("Order 2 burgers from McDonalds with coupon SAVE50 on Swiggy")

        assertNotNull(request)
        assertEquals("burgers", request?.foodItem)
        assertEquals("McDonalds", request?.restaurantName)
        assertEquals(2, request?.quantity)
        assertEquals(FoodProvider.SWIGGY, request?.preferredProvider)
        assertEquals(listOf(FoodProvider.SWIGGY), request?.requestedProviders)
        assertEquals("SAVE50", request?.couponPreference)
    }
}
