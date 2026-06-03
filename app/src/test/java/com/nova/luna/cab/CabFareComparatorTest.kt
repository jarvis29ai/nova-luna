package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabFareComparatorTest {
    private val comparator = CabFareComparator()

    @Test
    fun `discounted fare is preferred and options sort lowest to highest`() {
        val options = listOf(
            CabFareOption(
                provider = CabProvider.UBER,
                rideType = RideType.AUTO,
                visibleFareText = "₹139",
                finalFareText = "₹124 after discount",
                etaText = "ETA 5 min"
            ),
            CabFareOption(
                provider = CabProvider.RAPIDO,
                rideType = RideType.AUTO,
                visibleFareText = "Rs. 128",
                etaText = "ETA 4 min"
            ),
            CabFareOption(
                provider = CabProvider.OLA,
                rideType = RideType.AUTO,
                visibleFareText = "₹188",
                etaText = "ETA 6 min"
            )
        )

        val normalizedUber = comparator.normalize(options[0])
        val sorted = comparator.sortLowestToHighest(options)

        assertEquals(124L, normalizedUber.finalFareAmount)
        assertEquals(139L, normalizedUber.visibleFareAmount)
        assertEquals(listOf(CabProvider.UBER, CabProvider.RAPIDO, CabProvider.OLA), sorted.map { it.provider })
    }

    @Test
    fun `extractFareAmount handles rupee and rupees text`() {
        assertEquals(124L, comparator.extractFareAmount("₹124"))
        assertEquals(124L, comparator.extractFareAmount("Rs. 124"))
        assertEquals(124L, comparator.extractFareAmount("₹124 after discount"))
    }
}
