package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(124L, comparator.extractFareAmount("₹124 coupon applied"))
        assertEquals(150L, comparator.extractFareAmount("₹180 ₹150 after coupon"))
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

    @Test
    fun `rank top options respects fastest preference`() {
        val options = listOf(
            CabFareOption(
                provider = CabProvider.UBER,
                rideType = RideType.SEDAN,
                visibleFareText = "₹140",
                etaText = "ETA 8 min"
            ),
            CabFareOption(
                provider = CabProvider.RAPIDO,
                rideType = RideType.BIKE,
                visibleFareText = "₹90",
                etaText = "ETA 3 min"
            ),
            CabFareOption(
                provider = CabProvider.OLA,
                rideType = RideType.MINI,
                visibleFareText = "₹110",
                etaText = "ETA 5 min"
            )
        )

        val comparison = comparator.rankTopOptions(
            options = options,
            profile = CabRequirementProfile(preference = CabRidePreference.FASTEST)
        )

        assertEquals(3, comparison.rankedTop3.size)
        assertEquals(CabProvider.RAPIDO, comparison.recommendedOption?.provider)
        assertTrue(comparison.rankingReasons[CabProvider.RAPIDO]?.any { it.code == "fastest_preference" } == true)
    }
}
