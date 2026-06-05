package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabAccessibilityServiceManualActionTest {
    private val service = CabAccessibilityService()
    private val detectManualActionReason = CabAccessibilityService::class.java
        .getDeclaredMethod("detectManualActionReason", List::class.java)
        .apply { isAccessible = true }

    @Test
    fun `manual action keywords map to explicit manual action reasons`() {
        assertEquals("login", detect(listOf("Login required")))
        assertEquals("payment", detect(listOf("Payment screen open")))
        assertEquals("captcha", detect(listOf("Captcha challenge shown")))
        assertEquals("OTP", detect(listOf("OTP verification required")))
        assertEquals("UPI", detect(listOf("UPI screen open")))
        assertEquals("permission", detect(listOf("Permission required")))
    }

    @Test
    fun `location update secure and unavailable screens are treated as manual steps`() {
        assertEquals("permission", detect(listOf("Allow location to continue")))
        assertEquals("app update required", detect(listOf("Please update app to continue")))
        assertEquals("provider unavailable", detect(listOf("Provider unavailable right now")))
        assertEquals("secure or unreadable screen", detect(listOf("Secure screen detected")))
    }

    @Test
    fun `manual action reason from snapshot is preserved`() {
        assertEquals(
            "manual action required",
            service.detectManualActionRequired(
                CabScreenSnapshot(
                    visibleText = listOf("manual action required"),
                    sourcePackageName = CabProviderRegistry.UBER_PACKAGE_NAME,
                    manualActionReason = "manual action required"
                )
            )
        )
    }

    private fun detect(texts: List<String>): String? {
        return detectManualActionReason.invoke(service, texts) as String?
    }
}
