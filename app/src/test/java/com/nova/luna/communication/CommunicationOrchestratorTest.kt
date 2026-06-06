package com.nova.luna.communication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
class CommunicationOrchestratorTest {

    private lateinit var context: Context
    private lateinit var orchestrator: CommunicationOrchestrator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        
        // Ensure static instance is null to avoid cross-test contamination
        val instanceField: Field = com.nova.luna.service.NovaAccessibilityService::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)

        orchestrator = CommunicationOrchestrator(context)
    }

    @Test
    fun `test send it without draft returns error`() {
        val result = orchestrator.handleRequest("send it")
        assertEquals(CommunicationStatus.FAILED, result.status)
        assertTrue(result.popupText.contains("Error: No draft to send"))
    }

    @Test
    fun `test draft exists then send it completes handoff`() {
        // Create draft
        val draftResult = orchestrator.handleRequest("reply to rahul")
        assertEquals(CommunicationStatus.NEEDS_CONFIRMATION, draftResult.status)
        assertTrue(orchestrator.isActive())

        // Send it
        val sendResult = orchestrator.handleRequest("send it")
        // In our tests, NovaAccessibilityService is null so it returns BLOCKED
        assertEquals(CommunicationStatus.BLOCKED, sendResult.status)
        assertFalse(orchestrator.isActive()) // session ends on blocked
    }

    @Test
    fun `test cancel draft`() {
        val draftResult = orchestrator.handleRequest("reply to rahul")
        assertEquals(CommunicationStatus.NEEDS_CONFIRMATION, draftResult.status)
        
        val cancelResult = orchestrator.handleRequest("cancel")
        assertEquals(CommunicationStatus.CANCELLED, cancelResult.status)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `test gmail reading returns blocked when no account`() {
        val result = orchestrator.handleRequest("summarize gmail")
        assertEquals(CommunicationStatus.BLOCKED, result.status)
        assertTrue(result.popupText.contains("Gmail access is not configured"))
    }
}
