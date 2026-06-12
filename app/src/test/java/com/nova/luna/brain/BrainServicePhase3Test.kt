package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase3Test {
    private val codec = BrainActionJsonCodec()

    @Test
    fun `dangerous model output is rejected and falls back to mock provider`() {
        val dangerousAction = BrainAction(
            intent = "cab_booking",
            reply = "Book and pay now.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "book cheapest auto to DB Mall",
                "dropLocation" to "DB Mall"
            ),
            nextQuestion = "Confirm booking now?"
        )

        val service = BrainService(
            provider = StaticBrainProvider(codec.encode(dangerousAction))
        )

        val diagnostics = service.diagnose("book cheapest auto to DB Mall")

        assertFalse(diagnostics.validatorResult)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertEquals("cab_booking", diagnostics.finalBrainAction.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, diagnostics.finalBrainAction.actionType)
        assertEquals(BrainRiskLevel.SAFE, diagnostics.finalBrainAction.riskLevel)
        assertFalse(diagnostics.finalBrainAction.requiresConfirmation)
        assertFalse(diagnostics.finalBrainAction.finalActionAllowed)
        assertEquals(SafetyStatus.CONFIRMATION_REQUIRED, diagnostics.finalSafetyDecision.status)
        assertTrue(diagnostics.finalSafetyDecision.requiresConfirmation)
        assertFalse(diagnostics.finalSafetyDecision.allowed)
        assertNotNull(diagnostics.rawModelResponse)
    }

    @Test
    fun `missing required fields fall back to mock provider`() {
        val service = BrainService(
            provider = StaticBrainProvider(
                """{"intent":"prepare_message","reply":"I can help with that."}"""
            )
        )

        val diagnostics = service.diagnose("open WhatsApp and prepare message to mom")

        assertFalse(diagnostics.validatorResult)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("prepare_message", diagnostics.finalBrainAction.intent)
        assertEquals("What should I say to Mom?", diagnostics.finalBrainAction.nextQuestion)
        assertTrue(diagnostics.finalSafetyDecision.requiresConfirmation)
    }

    @Test
    fun `cab command with missing destination asks nextQuestion`() {
        val service = BrainService()

        val action = service.process("book cab from home")

        assertEquals("cab_booking", action.intent)
        assertEquals("Where should I pick you up from?", action.nextQuestion)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertFalse(action.requiresConfirmation)
    }

    private class StaticBrainProvider(
        private val response: String
    ) : BrainProvider {
        override fun analyze(request: BrainRequest): String {
            return response
        }
    }
}
