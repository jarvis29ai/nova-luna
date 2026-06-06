package com.nova.luna.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSafetyTest {
    private val detector = MusicSafetyDetector()

    @Test
    fun testExplicitSafety() {
        // Result marked as explicit should be detected
        val explicitResult = MusicSearchResult("Song", isExplicit = true, provider = MusicProvider.SPOTIFY)
        assertTrue(detector.isExplicit(explicitResult))
        
        val cleanResult = MusicSearchResult("Song", isExplicit = false, provider = MusicProvider.SPOTIFY)
        assertFalse(detector.isExplicit(cleanResult))
    }

    @Test
    fun testPaymentBlocking() {
        assertTrue(detector.needsManualAction("Please pay $9.99"))
        assertTrue(detector.needsManualAction("Subscribe to Premium"))
        assertTrue(detector.needsManualAction("Enter credit card details"))
    }
}
