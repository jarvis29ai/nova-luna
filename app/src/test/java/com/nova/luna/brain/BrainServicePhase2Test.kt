package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrainServicePhase2Test {
    private val codec = BrainActionJsonCodec()

    @Test
    fun `valid llm json converts to BrainAction`() {
        val expected = BrainAction(
            intent = "open_app",
            reply = "Opening WhatsApp.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "open whatsapp",
                "appName" to "whatsapp"
            )
        )

        val service = BrainService(
            provider = StaticBrainProvider(codec.encode(expected))
        )

        val action = service.process("open whatsapp")

        assertEquals(expected, action)
    }

    @Test
    fun `invalid json falls back to mock provider`() {
        val service = BrainService(
            provider = StaticBrainProvider("not json at all")
        )

        val action = service.process("open whatsapp")

        assertEquals("open_app", action.intent)
        assertEquals("whatsapp", action.params["appName"])
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
    }

    @Test
    fun `dangerous llm output is rejected and falls back to mock provider`() {
        val dangerous = BrainAction(
            intent = "cab_booking",
            reply = "Ready to book and pay now.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "book cheapest auto to DB Mall",
                "dropLocation" to "DB Mall",
                "rideType" to "AUTO"
            ),
            nextQuestion = "Confirm booking now?"
        )

        val service = BrainService(
            provider = StaticBrainProvider(codec.encode(dangerous))
        )

        val action = service.process("book cheapest auto to DB Mall")

        assertEquals("cab_booking", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
        assertEquals("Where should I pick you up from?", action.nextQuestion)
    }

    private class StaticBrainProvider(
        private val response: String
    ) : BrainProvider {
        override fun analyze(request: BrainRequest): String {
            return response
        }
    }
}
