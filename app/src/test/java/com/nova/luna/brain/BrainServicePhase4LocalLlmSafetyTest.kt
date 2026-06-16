package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase4LocalLlmSafetyTest {
    @Test
    fun `dangerous local llm output is rejected and replaced with a safe fallback`() {
        val bridge = object : BrainRouterBridge {
            override fun isReady(role: BrainModelRole): Boolean = role == BrainModelRole.GEMMA_REASONING
            override fun selectLocalRoute(request: BrainRequest, allowOnlineHelper: Boolean): BrainRouteDecision? = null
        }
        val service = BrainService(
            localBrainRouterBridge = bridge,
            gemmaRuntime = PhoneGemmaRuntime(
                config = gemmaPhoneConfig(gemmaModelAssetPath = tempModelFilePath()),
                backend = StaticGemmaBackend(response = dangerousGemmaJson())
            )
        )

        val diagnostics = service.diagnose("please help me rewrite this note")

        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.selectedRole)
        assertNotNull(diagnostics.parsedBrainAction)
        assertFalse(diagnostics.validatorResult)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals(LocalDeterministicBrainProvider::class.java.simpleName, diagnostics.finalProvider)
        assertEquals("local_model_unavailable", diagnostics.finalBrainAction.intent)
        assertFalse(diagnostics.finalBrainAction.finalActionAllowed)
        assertTrue(diagnostics.finalSafetyDecision.allowed)
        assertEquals("validation_rejected", diagnostics.runtimeStatus?.selectedLocalModelStatus)
        assertTrue(diagnostics.runtimeStatus?.promptBuilt == true)
        assertFalse(diagnostics.runtimeStatus?.jsonParseSucceeded ?: true)
    }
}
