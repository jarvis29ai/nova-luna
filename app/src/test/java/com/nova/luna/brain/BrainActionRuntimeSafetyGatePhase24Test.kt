package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.SafetyStatus
import com.nova.luna.safety.SafetyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class BrainActionRuntimeSafetyGatePhase24Test {

    private val router = mock(CommandRouter::class.java)
    private val safetyGate = SafetyGate()
    private val validator = BrainActionValidator()
    private val runtime = BrainActionRuntime(router, safetyGate, null, validator)

    @Test
    fun `test bypass prevention - payment action blocked in runtime`() {
        val action = BrainAction(
            intent = "pay",
            actionType = BrainActionType.PAYMENT_REQUEST,
            rawCommand = "pay 500",
            reply = "Processing payment...",
            riskLevel = BrainRiskLevel.LOW, // Try to bypass with low risk
            requiresConfirmation = false
        )
        val parsed = CommandIntent(rawText = "pay 500")
        
        val result = runtime.execute(action, "pay 500", parsed)
        
        println("Result status: ${result?.status}")
        println("Safety status: ${result?.safetyDecision?.status}")
        println("Safety reason: ${result?.safetyDecision?.reason}")
        
        assertNotNull(result)
        assertEquals(ActionResultStatus.BLOCKED, result?.status)
        assertEquals(SafetyStatus.BLOCKED, result?.safetyDecision?.status)
    }

    @Test
    fun `test bypass prevention - confirmation required in runtime`() {
        val action = BrainAction(
            intent = "book_cab",
            actionType = BrainActionType.CAB_SEARCH,
            rawCommand = "book cab to airport",
            reply = "Booking a cab...",
            riskLevel = BrainRiskLevel.LOW, // Try to bypass
            requiresConfirmation = false
        )
        val parsed = CommandIntent(rawText = "book cab to airport")
        
        val result = runtime.execute(action, "book cab to airport", parsed)
        
        println("Result status (cab): ${result?.status}")
        println("Safety status (cab): ${result?.safetyDecision?.status}")
        
        assertNotNull(result)
        assertEquals(ActionResultStatus.NEEDS_CONFIRMATION, result?.status)
        assertEquals(SafetyStatus.CONFIRMATION_REQUIRED, result?.safetyDecision?.status)
    }
}
