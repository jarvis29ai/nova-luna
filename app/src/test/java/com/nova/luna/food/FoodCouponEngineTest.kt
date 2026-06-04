package com.nova.luna.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodCouponEngineTest {
    private val engine = FoodCouponEngine()

    @Test
    fun `extractCouponCandidates finds visible codes and discount offers`() {
        val candidates = engine.extractCouponCandidates(
            listOf(
                "SAVE50",
                "Promo code WELCOME10",
                "Offer flat \u20B970 off"
            )
        )

        assertEquals(3, candidates.size)
        assertEquals("SAVE50", candidates[0].code)
        assertEquals("WELCOME10", candidates[1].code)
        assertNull(candidates[2].code)
        assertEquals(70L, candidates[2].discountAmount)
    }

    @Test
    fun `selectBestCoupon respects preference, skips none, and falls back to the best savings`() {
        val candidates = engine.extractCouponCandidates(
            listOf(
                "SAVE50",
                "Promo code WELCOME10",
                "Offer flat \u20B970 off"
            )
        )

        assertEquals("WELCOME10", engine.selectBestCoupon(candidates, "WELCOME10")?.code)
        assertNull(engine.selectBestCoupon(candidates, "none"))
        assertEquals(70L, engine.selectBestCoupon(candidates, "any")?.discountAmount)
    }
}
