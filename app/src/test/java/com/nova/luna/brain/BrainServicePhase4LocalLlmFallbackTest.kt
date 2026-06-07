package com.nova.luna.brain

import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase4LocalLlmFallbackTest {
    @Test
    fun `when the local model is unavailable the service returns a helpful safe fallback`() {
        val service = BrainService(
            gemmaRuntime = PhoneGemmaRuntime(
                config = gemmaPhoneConfig(gemmaModelAssetPath = tempModelFilePath()),
                backend = StaticGemmaBackend(runtimeAvailable = false)
            )
        )

        val action = service.process("please help me rewrite this note")

        assertEquals("local_model_unavailable", action.intent)
        assertEquals(BrainActionType.NONE, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertFalse(action.finalActionAllowed)
        assertTrue(action.reply.contains("Local AI model", ignoreCase = true))
        assertNotNull(action.nextQuestion)
        assertTrue(action.params["routeRole"]?.contains("gemma", ignoreCase = true) == true)
    }
}
