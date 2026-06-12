package com.nova.luna.brain

import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainCapabilityMode
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase4Test {
    @Test
    fun `offline mode still parses common commands`() {
        val service = BrainService(
            runtimeConfig = runtimeConfig(BrainCapabilityMode.OFFLINE_ONLY)
        )

        val diagnostics = service.diagnose("open WhatsApp and prepare message to mom")
        val action = diagnostics.finalBrainAction

        assertEquals("prepare_message", action.intent)
        assertEquals(BrainActionType.SEND_MESSAGE_DRAFT, action.actionType)
        assertEquals(BrainRiskLevel.CONFIRMATION_REQUIRED, action.riskLevel)
        assertEquals("whatsapp", action.params["appName"])
        assertEquals("mom", action.params["contact"])
        assertNotNull(diagnostics.runtimeStatus)
        assertEquals(BrainCapabilityMode.OFFLINE_ONLY, diagnostics.runtimeStatus?.capabilityMode)
        assertEquals("LocalMockBrainProvider", diagnostics.runtimeStatus?.selectedProvider)
        assertTrue(diagnostics.runtimeStatus?.fallbackActive == true)
        assertTrue(diagnostics.runtimeStatus?.safetyChainActive == true)
    }

    @Test
    fun `no internet returns an offline limitation for live info requests`() {
        val service = BrainService(
            runtimeConfig = runtimeConfig(BrainCapabilityMode.ONLINE_ASSISTED),
            internetAvailable = false
        )

        val action = service.process("what is the weather in Delhi?")

        assertEquals("internet_unavailable", action.intent)
        assertEquals(BrainActionType.NONE, action.actionType)
        assertTrue(action.reply.contains("internet", ignoreCase = true))
        assertNotNull(action.nextQuestion)
    }

    @Test
    fun `online mode cannot bypass SafetyGate`() {
        val service = BrainService(
            runtimeConfig = runtimeConfig(BrainCapabilityMode.ONLINE_ASSISTED),
            internetAvailable = true
        )

        val action = service.process("complete the payment")

        assertEquals(BrainActionType.HUMAN_ONLY, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
        assertFalse(action.finalActionAllowed)
    }

    @Test
    fun `dangerous actions remain blocked in every capability mode`() {
        val phrases = listOf(
            "pay 500 rupees to Rahul",
            "enter this OTP automatically",
            "book it without asking me",
            "final booking",
            "delete my files",
            "complete the payment"
        )

        val modes = listOf(
            BrainCapabilityMode.OFFLINE_ONLY,
            BrainCapabilityMode.ONLINE_ASSISTED,
            BrainCapabilityMode.LOCAL_LLM_DEV
        )

        modes.forEach { mode ->
            val service = BrainService(
                runtimeConfig = runtimeConfig(mode),
                internetAvailable = true
            )

            phrases.forEach { phrase ->
                val action = service.process(phrase)
                assertEquals("human_only", action.intent)
                assertEquals(BrainActionType.HUMAN_ONLY, action.actionType)
                assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
            }
        }
    }

    private fun runtimeConfig(mode: BrainCapabilityMode): BrainRuntimeConfig {
        return BrainRuntimeConfig(
            brainProvider = if (mode == BrainCapabilityMode.LOCAL_LLM_DEV) "ollama" else "mock",
            ollamaBaseUrl = "http://127.0.0.1:11434",
            ollamaModel = "qwen2.5:3b",
            llmEnabled = mode == BrainCapabilityMode.LOCAL_LLM_DEV,
            capabilityMode = mode
        )
    }
}
