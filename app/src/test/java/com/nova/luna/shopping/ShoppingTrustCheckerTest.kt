package com.nova.luna.shopping

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingTrustCheckerTest {

    private val trustChecker = ShoppingTrustChecker()

    @Test
    fun testAmazonIsSafe() {
        val result = trustChecker.check("https://www.amazon.in/p/123", "Appario")
        assertEquals(ShoppingTrustLevel.SAFE, result.level)
    }

    @Test
    fun testInsecureUrlIsRisky() {
        val result = trustChecker.check("http://scam-site.com", "Unknown")
        assertEquals(ShoppingTrustLevel.RISKY, result.level)
    }

    @Test
    fun testScamDomainIsRisky() {
        val result = trustChecker.check("https://free-phones.com/p/1", "Scammer")
        assertEquals(ShoppingTrustLevel.RISKY, result.level)
    }
}
