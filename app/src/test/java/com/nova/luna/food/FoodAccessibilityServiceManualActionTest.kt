package com.nova.luna.food

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodAccessibilityServiceManualActionTest {
    private val service = FoodAccessibilityService()
    private val detectManualActionReason = FoodAccessibilityService::class.java
        .getDeclaredMethod("detectManualActionReason", List::class.java)
        .apply { isAccessible = true }

    @Test
    fun `manual action keywords map to explicit reasons`() {
        assertEquals(FoodManualActionReason.LOGIN, detect(listOf("Log in required")))
        assertEquals(FoodManualActionReason.PAYMENT, detect(listOf("Payment method selection open")))
        assertEquals(FoodManualActionReason.CAPTCHA, detect(listOf("Captcha challenge shown")))
        assertEquals(FoodManualActionReason.OTP, detect(listOf("One-time password verification required")))
        assertEquals(FoodManualActionReason.MANUAL_CONFIRMATION, detect(listOf("Manual action required")))
    }

    @Test
    fun `manual action reason from snapshot is preserved`() {
        assertEquals(
            "OTP",
            service.detectManualActionRequired(
                FoodCartSnapshot(
                    visibleText = listOf("manual action required"),
                    manualActionReason = FoodManualActionReason.OTP
                )
            )
        )
    }

    private fun detect(texts: List<String>): FoodManualActionReason? {
        return detectManualActionReason.invoke(service, texts) as FoodManualActionReason?
    }
}
