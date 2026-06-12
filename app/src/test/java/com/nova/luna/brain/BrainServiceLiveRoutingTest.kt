package com.nova.luna.brain

import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServiceLiveRoutingTest {
    private val service = BrainService()

    @Test
    fun `open app routes to the lite command model`() {
        val action = service.process("open YouTube")
        println("DEBUG: open app -> intent=${action.intent}, type=${action.actionType}, risk=${action.riskLevel}, reply=${action.assistantReply}")

        assertEquals("open_app", action.intent)
        assertEquals(BrainActionType.OPEN_APP, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue(action.finalActionAllowed)
        // Note: query entity is added by the parser as an alias for appName in some cases
        assertTrue(action.params.containsKey("appName") || action.params.containsKey("query"))
    }

    @Test
    fun `draft reply routes to the action json model`() {
        val action = service.process("draft reply to Rahul")
        println("DEBUG: draft reply -> intent=${action.intent}, type=${action.actionType}, risk=${action.riskLevel}, reply=${action.assistantReply}")

        assertEquals("prepare_message", action.intent)
        assertEquals(BrainActionType.SEND_MESSAGE_DRAFT, action.actionType)
        assertEquals(BrainRiskLevel.CONFIRMATION_REQUIRED, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
        assertEquals("rahul", action.params["contact"])
    }

    @Test
    fun `create ppt routes to the content creation action json branch`() {
        val action = service.process("create a PPT about AI")

        assertEquals("content_creation", action.intent)
        assertEquals(BrainActionType.CREATE_CONTENT, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue(action.finalActionAllowed)
        assertEquals("ppt", action.params["outputType"])
        assertEquals("ai", action.params["topic"])
    }

    @Test
    fun `call mom stays on the guarded phone path`() {
        val action = service.process("call mom")

        assertEquals("call_contact", action.intent)
        assertEquals(BrainActionType.MAKE_CALL_DRAFT, action.actionType)
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
        assertTrue(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
    }

    @Test
    fun `task planning routes to a safe read only action`() {
        val action = service.process("plan my tasks for today")

        assertEquals("task_planning", action.intent)
        assertEquals(BrainActionType.ASK_QUESTION, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue(action.finalActionAllowed)
    }

    @Test
    fun `bare compare falls back safely`() {
        val action = service.process("compare")

        assertEquals("unknown", action.intent)
        assertEquals(BrainActionType.UNKNOWN, action.actionType)
        assertEquals(BrainRiskLevel.UNKNOWN, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue(action.finalActionAllowed)
    }
}
