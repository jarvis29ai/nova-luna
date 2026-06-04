package com.nova.luna.food

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodAccessibilityServiceFinalTapSafetyTest {
    @Test
    fun `final place order tap does not target payment labels`() {
        val service = object : FoodAccessibilityService() {
            val tappedQueries = mutableListOf<String>()

            override fun tapSafeField(query: String): Boolean {
                tappedQueries.add(query)
                return query == "confirm order"
            }
        }

        assertTrue(service.tapFinalPlaceOrderButton(true))
        assertFalse(service.tappedQueries.any { it.contains("pay", ignoreCase = true) || it.contains("payment", ignoreCase = true) })
    }
}
