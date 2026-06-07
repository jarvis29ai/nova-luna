package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase1Test {
    private val service = BrainService()
    private val codec = BrainActionJsonCodec()

    @Test
    fun `cab booking phrases become safe external session starters`() {
        val action = service.process("book cheapest auto to DB Mall")

        assertEquals("cab_booking", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
        assertEquals("DB Mall", action.params["dropLocation"])
        assertEquals("AUTO", action.params["rideType"])
        assertEquals("true", action.params["wantsCheapest"])
        assertEquals("Where should I pick you up from?", action.nextQuestion)
        assertTrue(action.reply.contains("pickup location"))
    }

    @Test
    fun `cab comparison phrases keep providers and destination structured`() {
        val action = service.process("compare Ola and Rapido to home")

        assertEquals("cab_compare", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertEquals("OLA,RAPIDO", action.params["providers"])
        assertEquals("home", action.params["destination"])
        assertFalse(action.requiresConfirmation)
        assertTrue(action.reply.contains("Ola"))
        assertTrue(action.reply.contains("Rapido"))
        assertNotNull(action.nextQuestion)
    }

    @Test
    fun `grocery order phrases stay in the grocery booking flow`() {
        val action = service.process("Luna order milk and bread")

        assertEquals("grocery_booking", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
        assertEquals("Luna order milk and bread", action.params["rawText"])
        assertTrue(action.reply.contains("grocery", ignoreCase = true))
    }

    @Test
    fun `message preparation phrases keep app and contact structured`() {
        val action = service.process("open WhatsApp and prepare message to mom")

        assertEquals("prepare_message", action.intent)
        assertEquals(BrainActionType.PREPARE, action.actionType)
        assertEquals(BrainRiskLevel.CONFIRMATION_REQUIRED, action.riskLevel)
        assertEquals("whatsapp", action.params["appName"])
        assertEquals("mom", action.params["contact"])
        assertEquals("What should I say to Mom?", action.nextQuestion)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
        assertTrue(action.reply.contains("whatsapp", ignoreCase = true))
        assertTrue(action.reply.contains("prepare") || action.reply.contains("draft") || action.reply.contains("message"))
    }

    @Test
    fun `send money stays human only`() {
        val action = service.process("send money to mom")

        assertEquals("human_only", action.intent)
        assertEquals(BrainActionType.HUMAN_ONLY, action.actionType)
        assertEquals(BrainRiskLevel.BLOCKED, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
    }

    @Test
    fun `brain action codec round trips valid structured output`() {
        val original = BrainAction(
            intent = "open_app",
            reply = "Opening WhatsApp.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "open whatsapp",
                "appName" to "whatsapp"
            ),
            nextQuestion = null
        )

        val decoded = codec.decode(codec.encode(original))

        assertEquals(original, decoded)
    }
}
