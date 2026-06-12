package com.nova.luna.safety

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.SafetyCategory
import com.nova.luna.model.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGatePhase24Test {

    private val safetyGate = SafetyGate()

    @Test
    fun `test blocked payment action`() {
        val action = createAction(
            intent = "pay_money",
            actionType = BrainActionType.PAYMENT_REQUEST,
            rawCommand = "pay 500 rupees to Rahul"
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.BLOCKED, decision.status)
        assertEquals(SafetyCategory.PAYMENT_OR_FINANCIAL, decision.category)
        assertTrue(decision.reason.contains("Payment", ignoreCase = true))
    }

    @Test
    fun `test blocked OTP keyword in params`() {
        val action = createAction(
            intent = "read_screen",
            actionType = BrainActionType.OPEN_APP,
            rawCommand = "read my screen",
            params = mapOf("text" to "your otp is 123456")
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.BLOCKED, decision.status)
        assertEquals(SafetyCategory.OTP_OR_VERIFICATION_CODE, decision.category)
    }

    @Test
    fun `test blocked login action`() {
        val action = createAction(
            intent = "login",
            actionType = BrainActionType.LOGIN_REQUEST,
            rawCommand = "login to my bank app"
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.BLOCKED, decision.status)
        assertEquals(SafetyCategory.LOGIN_OR_AUTHENTICATION, decision.category)
    }

    @Test
    fun `test blocked destructive action`() {
        val action = createAction(
            intent = "factory_reset",
            actionType = BrainActionType.DESTRUCTIVE_REQUEST,
            rawCommand = "factory reset my phone"
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.BLOCKED, decision.status)
        assertEquals(SafetyCategory.DESTRUCTIVE_ACTION, decision.category)
    }

    @Test
    fun `test confirmation required for booking`() {
        val action = createAction(
            intent = "book_cab",
            actionType = BrainActionType.CAB_SEARCH, // Initially search but could be booking
            rawCommand = "book a cab to airport"
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.CONFIRMATION_REQUIRED, decision.status)
        assertEquals(SafetyCategory.BOOKING_OR_ORDER_FINALIZATION, decision.category)
        assertTrue(decision.requiresUserConfirmation)
    }

    @Test
    fun `test confirmation required for sending message`() {
        val action = createAction(
            intent = "send_message",
            actionType = BrainActionType.SEND_MESSAGE_DRAFT,
            rawCommand = "send message to Rahul saying I am late"
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.CONFIRMATION_REQUIRED, decision.status)
        assertEquals(SafetyCategory.MESSAGE_OR_CALL_SENSITIVE, decision.category)
    }

    @Test
    fun `test allowed safe low risk action`() {
        val action = createAction(
            intent = "open_camera",
            actionType = BrainActionType.OPEN_APP,
            rawCommand = "open camera",
            riskLevel = BrainRiskLevel.LOW
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.ALLOWED, decision.status)
        assertEquals(SafetyCategory.SAFE_LOW_RISK, decision.category)
        assertFalse(decision.requiresUserConfirmation)
    }

    @Test
    fun `test allowed draft action`() {
        val action = createAction(
            intent = "draft_message",
            actionType = BrainActionType.SEND_MESSAGE_DRAFT,
            rawCommand = "draft a message to Rahul"
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.ALLOWED, decision.status)
        assertEquals(SafetyCategory.SAFE_LOW_RISK, decision.category)
    }

    @Test
    fun `test blocked suspicious params with payment word`() {
        val action = createAction(
            intent = "open_app",
            actionType = BrainActionType.OPEN_APP,
            rawCommand = "open my wallet",
            params = mapOf("action" to "confirm payment")
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.BLOCKED, decision.status)
        assertEquals(SafetyCategory.PAYMENT_OR_FINANCIAL, decision.category)
    }

    @Test
    fun `test override model low risk with rule-based block`() {
        val action = createAction(
            intent = "pay",
            actionType = BrainActionType.PAYMENT_REQUEST,
            rawCommand = "pay rahul",
            riskLevel = BrainRiskLevel.LOW // Model lies
        )
        val decision = safetyGate.evaluate(action)
        assertEquals(SafetyStatus.BLOCKED, decision.status)
    }

    private fun createAction(
        intent: String,
        actionType: BrainActionType,
        rawCommand: String,
        riskLevel: BrainRiskLevel = BrainRiskLevel.LOW,
        requiresConfirmation: Boolean = false,
        params: Map<String, String> = emptyMap()
    ): BrainAction {
        return BrainAction(
            intent = intent,
            actionType = actionType,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            params = params,
            rawCommand = rawCommand,
            normalizedCommand = rawCommand.lowercase()
        )
    }
}
