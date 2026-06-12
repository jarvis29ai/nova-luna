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
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
        assertEquals("DB Mall", action.params["dropLocation"])
        assertEquals("AUTO", action.params["rideType"])
        assertEquals("true", action.params["wantsCheapest"])
        assertEquals("Where should I pick you up from?", action.nextQuestion)
    }

    @Test
    fun `cab comparison phrases keep providers and destination structured`() {
        val action = service.process("compare Ola and Rapido to home")

        assertEquals("cab_compare", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
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
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
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
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
        assertEquals("whatsapp", action.params["appName"])
        assertEquals("mom", action.params["contact"])
        assertEquals("What should I say to Mom?", action.nextQuestion)
        assertFalse(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
    }

    @Test
    fun `send money stays human only`() {
        val action = service.process("send money to Rahul")

        assertEquals("human_only", action.intent)
        assertEquals(BrainActionType.HUMAN_ONLY, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
        assertTrue(action.requiresConfirmation)
        assertFalse(action.finalActionAllowed)
    }

    @Test
    fun `open app phrases route correctly to lite command model`() {
        val action = service.process("open WhatsApp")

        assertEquals("open_app", action.intent)
        assertEquals(BrainActionType.OPEN_APP, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue(action.finalActionAllowed)
        assertEquals("whatsapp", action.params["appName"])
    }

    @Test
    fun `brain action codec round trips valid structured output`() {
        val original = BrainAction(
            intent = "open_app",
            reply = "Opening WhatsApp.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.LOW,
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
