package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabFareComparatorTest {
    private val comparator = CabFareComparator()

    @Test
    fun `parse rupee formats and discounted fares`() {
        assertEquals(124L, comparator.extractFareAmount("₹124"))
        assertEquals(124L, comparator.extractFareAmount("₹ 124"))
        assertEquals(124L, comparator.extractFareAmount("Rs 124"))
        assertEquals(124L, comparator.extractFareAmount("Rs.124"))
        assertEquals(124L, comparator.extractFareAmount("INR 124"))
        assertEquals(124L, comparator.extractFareAmount("124 rupees"))
        assertEquals(99L, comparator.extractFareAmount("₹124 ₹99 discount case"))
        assertEquals(99L, comparator.extractFareAmount("₹99 after coupon"))
        assertNull(comparator.extractFareAmount("Save ₹30"))
        assertEquals(180L, comparator.extractFareAmount("₹180 ₹150"))
    }

    @Test
    fun `sort low to high and push unavailable fares to the end`() {
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
                visibleFareText = "₹99",
                etaText = "ETA 4 min"
            ),
            CabFareOption(
                provider = CabProvider.OLA,
                rideType = RideType.AUTO,
                visibleFareText = "₹188",
                etaText = "ETA 6 min"
            ),
            CabFareOption(
                provider = CabProvider.INDRIVE,
                rideType = RideType.AUTO,
                visibleFareText = null,
                finalFareText = null,
                etaText = null
            )
        )

        val normalizedUber = comparator.normalize(options[0])
        val sorted = comparator.sortLowestToHighest(options)

        assertEquals(124L, normalizedUber.finalFareAmount)
        assertEquals(139L, normalizedUber.originalFareAmount)
        assertEquals(listOf(
            CabProvider.RAPIDO,
            CabProvider.UBER,
            CabProvider.OLA,
            CabProvider.INDRIVE
        ), sorted.map { it.provider })
    }
}
