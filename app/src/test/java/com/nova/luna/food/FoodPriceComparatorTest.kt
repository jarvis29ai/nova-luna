package com.nova.luna.food

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodPriceComparatorTest {
    private val comparator = FoodPriceComparator()

    @Test
    fun `discounted quote is normalized and quotes sort lowest to highest`() {
        val quotes = listOf(
            FoodPlatformQuote(
                provider = FoodProvider.SWIGGY,
                foodItem = "burger",
                visiblePriceText = "\u20B9260",
                finalPayableText = "\u20B9240 after coupon",
                etaText = "ETA 28 min"
            ),
            FoodPlatformQuote(
                provider = FoodProvider.ZOMATO,
                foodItem = "burger",
                visiblePriceText = "Rs. 220",
                finalPayableText = "Rs. 220",
                etaText = "ETA 31 min"
            ),
            FoodPlatformQuote(
                provider = FoodProvider.TOINGS,
                foodItem = "burger",
                visiblePriceText = "\u20B9250",
                finalPayableText = "\u20B9250",
                etaText = "ETA 25 min"
            )
        )

        val normalized = comparator.normalize(quotes[0])
        val sorted = comparator.sortLowestToHighest(quotes)

        assertEquals(240L, normalized.finalPayableAmount)
        assertEquals(260L, normalized.visiblePriceAmount)
        assertEquals(listOf(FoodProvider.ZOMATO, FoodProvider.SWIGGY, FoodProvider.TOINGS), sorted.map { it.provider })
    }

    @Test
    fun `extractAmount handles rupee and rupees text`() {
        assertEquals(220L, comparator.extractAmount("\u20B9220"))
        assertEquals(220L, comparator.extractAmount("Rs. 220"))
        assertEquals(240L, comparator.extractAmount("\u20B9240 after coupon"))
        assertEquals(220L, comparator.extractAmount("Final payable 220"))
        assertEquals(240L, comparator.extractAmount("Total 240 save 20"))
    }

    @Test
    fun `extractEtaMinutes handles ranges and in-phrases`() {
        assertEquals(35, comparator.extractEtaMinutes("ETA 30-35 min"))
        assertEquals(28, comparator.extractEtaMinutes("arrives in 28 mins"))
    }
}
