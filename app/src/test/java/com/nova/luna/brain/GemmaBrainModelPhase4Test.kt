package com.nova.luna.brain

import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaBrainModelPhase4Test {
    @Test
    fun `gemma brain model delegates to the local runtime and returns a structured candidate`() {
        val runtime = PhoneGemmaRuntime(
            config = gemmaPhoneConfig(gemmaModelAssetPath = tempModelFilePath()),
            backend = StaticGemmaBackend(
                response = BrainActionJsonCodec().encode(
                    com.nova.luna.model.BrainAction(
                        intent = "explain",
                        reply = "Gemma reasoning placeholder.",
                        actionType = BrainActionType.READ_ONLY,
                        riskLevel = BrainRiskLevel.SAFE,
                        requiresConfirmation = false,
                        finalActionAllowed = false,
                        params = mapOf("rawText" to "please explain this note")
                    )
                )
            )
        )

        val model = GemmaBrainModel(runtime)
        val result = model.generate(
            request = BrainRequest("please explain this note"),
            routeDecision = BrainRouteDecision(
                selectedRole = BrainModelRole.GEMMA_REASONING,
                reason = "Local reasoning request.",
                requiresInternet = false,
                requiresScreenContext = false,
                fallbackAllowed = true,
                safetyNotes = listOf("Strict JSON only.")
            )
        )

        assertTrue(model.available)
        assertTrue(result.available)
        assertNotNull(result.candidateAction)
        assertEquals("explain", result.candidateAction?.intent)
        assertEquals(PhoneLocalLlmStatus.READY, result.localModelStatus)
        assertEquals(PhoneLocalLlmModelId.GEMMA_3N, result.localModelId)
        assertTrue(result.promptBuilt)
        assertTrue(result.jsonParsed)
    }
}
