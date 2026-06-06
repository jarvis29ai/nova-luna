package com.nova.luna.content

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentCreationOrchestratorTest {
    private lateinit var orchestrator: ContentCreationOrchestrator

    @Before
    fun setup() {
        orchestrator = ContentCreationOrchestrator(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testFullFlowPpt() {
        // 1. Initial request
        var result = orchestrator.handleRequest("Create a PPT about AI")
        assertEquals(ContentCreationStatus.NEEDS_USER_INPUT, result.status)

        // 2. Provide details
        orchestrator.handleRequest("For business report")
        orchestrator.handleRequest("For investors")
        orchestrator.handleRequest("Modern style")
        result = orchestrator.handleRequest("10 slides")
        
        assertEquals(ContentCreationStatus.NEEDS_CONFIRMATION, result.status)
        assertTrue("Draft not prepared: ${result.popupText}", result.popupText.contains("prepared a draft"))

        // 3. Approve
        result = orchestrator.handleRequest("Approve")
        assertTrue("Finalize failed: ${result.status}", result.status == ContentCreationStatus.SUCCESS || result.status == ContentCreationStatus.MANUAL_ACTION_REQUIRED)
    }

    @Test
    fun testSafetySharingConfirmation() {
        // 1. Start with enough details to reach draft quickly
        orchestrator.handleRequest("Create an image about nature for personal use in creative style")
        orchestrator.handleRequest("Approve")
        
        // 2. Request share
        var result = orchestrator.handleRequest("Share on WhatsApp")
        assertEquals(ContentCreationStatus.NEEDS_CONFIRMATION, result.status)
        assertTrue("Popup text wrong: ${result.popupText}", result.popupText.lowercase().contains("share") && result.popupText.contains("WhatsApp"))

        // 3. Deny sharing
        result = orchestrator.handleRequest("No")
        assertEquals(ContentCreationStatus.SUCCESS, result.status)
        assertTrue(result.popupText.lowercase().contains("cancelled"))
        
        // 4. Request share again
        orchestrator.handleRequest("Share on WhatsApp")
        
        // 5. Approve sharing
        result = orchestrator.handleRequest("Yes")
        assertEquals(ContentCreationStatus.SUCCESS, result.status)
        assertTrue(result.popupText.lowercase().contains("shared") || result.popupText.lowercase().contains("failed"))
    }

    @Test
    fun testCancelSession() {
        orchestrator.handleRequest("Create a video")
        var result = orchestrator.handleRequest("Cancel")
        assertEquals(ContentCreationStatus.CANCELLED, result.status)
        assertFalse(orchestrator.isActive())
    }
}
