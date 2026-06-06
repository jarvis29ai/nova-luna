package com.nova.luna.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSafetyDetectorTest {
    private val detector = MusicSafetyDetector()

    @Test
    fun testNeedsManualAction() {
        assertTrue(detector.needsManualAction("Please login to continue"))
        assertTrue(detector.needsManualAction("Enter OTP"))
        assertTrue(detector.needsManualAction("Payment required"))
        assertFalse(detector.needsManualAction("Now playing your favorite song"))
    }

    @Test
    fun testExplicitWarning() {
        assertTrue(detector.isExplicitWarning("This content is explicit"))
        assertFalse(detector.isExplicitWarning("Clean version"))
    }
}
