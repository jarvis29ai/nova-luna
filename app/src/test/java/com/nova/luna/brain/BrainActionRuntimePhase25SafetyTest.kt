package com.nova.luna.brain

import com.nova.luna.model.*
import com.nova.luna.phone.PhoneActionExecutor
import com.nova.luna.phone.PhoneActionResult
import com.nova.luna.safety.SafetyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BrainActionRuntimePhase25SafetyTest {

    private val router = mock(CommandRouter::class.java)
    private val safetyGate = SafetyGate()
    private val executor = mock(PhoneActionExecutor::class.java)
    private val validator = BrainActionValidator()
    private val runtime = BrainActionRuntime(router, safetyGate, executor, validator)

    @Test
    fun `allowed safe action reaches executor`() {
        val action = BrainAction(
            intent = "open_camera",
            actionType = BrainActionType.OPEN_CAMERA,
            rawCommand = "open camera",
            reply = "Opening camera...",
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        val parsed = CommandIntent(rawText = "open camera")
        val phoneResult = PhoneActionResult("OPEN_CAMERA", true, true, "Opened camera.")
        
        `when`(executor.execute(action)).thenReturn(phoneResult)
        
        val result = runtime.execute(action, "open camera", parsed)
        
        assertNotNull(result)
        assertEquals(ActionResultStatus.SUCCESS, result?.status)
        assertEquals(phoneResult, result?.phoneActionResult)
        verify(executor).execute(action)
    }

    @Test
    fun `blocked payment action never reaches executor`() {
        val action = BrainAction(
            intent = "pay",
            actionType = BrainActionType.PAYMENT_REQUEST,
            rawCommand = "pay 500",
            reply = "Processing payment...",
            riskLevel = BrainRiskLevel.LOW, // Model lies
            requiresConfirmation = false
        )
        val parsed = CommandIntent(rawText = "pay 500")
        
        val result = runtime.execute(action, "pay 500", parsed)
        
        assertNotNull(result)
        assertEquals(ActionResultStatus.BLOCKED, result?.status)
        assertNull(result?.phoneActionResult)
        verify(executor, never()).execute(action)
    }

    @Test
    fun `confirmation required action does not execute before confirmation`() {
        val action = BrainAction(
            intent = "book_cab",
            actionType = BrainActionType.CAB_SEARCH,
            rawCommand = "book cab to airport",
            reply = "Booking cab...",
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        val parsed = CommandIntent(rawText = "book cab to airport")
        
        val result = runtime.execute(action, "book cab to airport", parsed)
        
        assertNotNull(result)
        assertEquals(ActionResultStatus.NEEDS_CONFIRMATION, result?.status)
        assertNull(result?.phoneActionResult)
        verify(executor, never()).execute(action)
    }
}
