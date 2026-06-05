package com.nova.luna.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
class FoodAccessibilityServiceSelectorTest {
    private val recordingService = RecordingFoodAccessibilityService()
    private val tapSearchField: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("tapSearchField", String::class.java)
        .apply { isAccessible = true }
    private val tapCartOrCheckout: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("tapCartOrCheckout")
        .apply { isAccessible = true }
    private val findVisiblePriceText: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("findVisiblePriceText", List::class.java)
        .apply { isAccessible = true }
    private val findFinalPayableText: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("findFinalPayableText", List::class.java)
        .apply { isAccessible = true }
    private val findCouponText: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("findCouponText", List::class.java)
        .apply { isAccessible = true }
    private val findRestaurantText: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("findRestaurantText", List::class.java)
        .apply { isAccessible = true }
    private val findFoodItemText: Method = FoodAccessibilityService::class.java
        .getDeclaredMethod("findFoodItemText", List::class.java)
        .apply { isAccessible = true }

    @Test
    fun `search and cart selectors include common provider labels`() {
        assertFalse(tapSearchField.invoke(recordingService, "search") as Boolean)
        assertFalse(tapCartOrCheckout.invoke(recordingService) as Boolean)

        assertTrue(recordingService.fieldQueries.contains("search restaurants"))
        assertTrue(recordingService.fieldQueries.contains("search items"))
        assertTrue(recordingService.fieldQueries.contains("find restaurant"))
        assertTrue(recordingService.fieldQueries.contains("menu"))
        assertTrue(recordingService.buttonQueries.contains("view basket"))
        assertTrue(recordingService.buttonQueries.contains("cart summary"))
        assertTrue(recordingService.buttonQueries.contains("shopping bag"))
        assertFalse(recordingService.buttonQueries.any { it.contains("pay", ignoreCase = true) || it.contains("payment", ignoreCase = true) })
    }

    @Test
    fun `price helpers capture visible and final payable amounts`() {
        assertEquals(
            "₹149",
            findVisiblePriceText.invoke(recordingService, listOf("Order total ₹179", "₹149", "Delivery fee ₹30", "Coupon applied"))
        )
        assertEquals(
            "Order total ₹179",
            findFinalPayableText.invoke(recordingService, listOf("Order total ₹179", "Subtotal ₹149"))
        )
    }

    @Test
    fun `restaurant coupon and item helpers recognize common labels`() {
        assertEquals(
            "Food Court",
            findRestaurantText.invoke(recordingService, listOf("Food Court", "Paneer pizza"))
        )
        assertEquals(
            "Voucher applied - SAVE20",
            findCouponText.invoke(recordingService, listOf("Voucher applied - SAVE20", "₹149"))
        )
        assertEquals(
            "Paneer pizza",
            findFoodItemText.invoke(recordingService, listOf("Paneer pizza", "Order summary", "₹149"))
        )
    }

    private class RecordingFoodAccessibilityService : FoodAccessibilityService() {
        val fieldQueries = mutableListOf<String>()
        val buttonQueries = mutableListOf<String>()

        override fun tapSafeField(query: String): Boolean {
            fieldQueries += query
            return false
        }

        override fun tapSafeButton(query: String, finalUserConfirmed: Boolean): Boolean {
            buttonQueries += query
            return false
        }
    }
}
