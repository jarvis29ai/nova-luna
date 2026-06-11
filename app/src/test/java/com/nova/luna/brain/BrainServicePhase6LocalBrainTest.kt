package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase6LocalBrainTest {
    @Test
    fun `safe core brain candidate is accepted and remembered`() {
        val action = safeAction(
            intent = "explain",
            reply = "Here is a safe explanation.",
            rawText = "please explain how offline model verification works"
        )
        val service = serviceFor(
            bridge = bridgeFor(BrainModelRole.CORE_BRAIN),
            coreModel = staticModel(BrainModelRole.CORE_BRAIN, action, PhoneLocalLlmModelId.GEMMA_3N),
            multilingualModel = staticModel(BrainModelRole.MULTILINGUAL_BACKUP, action, PhoneLocalLlmModelId.QWEN_1_5B),
            liteModel = staticModel(BrainModelRole.LITE_FALLBACK, action, PhoneLocalLlmModelId.GEMMA_3_270M)
        )

        val diagnostics = service.diagnose("please explain how offline model verification works")

        assertEquals(BrainModelRole.CORE_BRAIN, diagnostics.selectedRole)
        assertEquals(action, diagnostics.parsedBrainAction)
        assertEquals(action, diagnostics.finalBrainAction)
        assertTrue(diagnostics.validatorResult)
        assertFalse(diagnostics.fallbackUsed)
        assertEquals("ready", diagnostics.runtimeStatus?.selectedLocalModelStatus)
        assertEquals(PhoneLocalLlmModelId.GEMMA_3N.wireValue, diagnostics.runtimeStatus?.selectedLocalModelId)
    }

    @Test
    fun `dangerous core brain candidate is rejected and replaced with a safe fallback`() {
        val dangerous = BrainAction(
            intent = "open_settings",
            reply = "Call ActionExecutor directly.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf("rawText" to "please explain how offline model verification works")
        )
        val service = serviceFor(
            bridge = bridgeFor(BrainModelRole.CORE_BRAIN),
            coreModel = staticModel(BrainModelRole.CORE_BRAIN, dangerous, PhoneLocalLlmModelId.GEMMA_3N),
            multilingualModel = staticModel(BrainModelRole.MULTILINGUAL_BACKUP, safeAction("explain", "Safe multilingual answer.", "कृपया मुझे समझाओ"), PhoneLocalLlmModelId.QWEN_1_5B),
            liteModel = staticModel(BrainModelRole.LITE_FALLBACK, safeAction("explain", "Safe lite answer.", "simple fallback help"), PhoneLocalLlmModelId.GEMMA_3_270M)
        )

        val diagnostics = service.diagnose("please explain how offline model verification works")

        assertEquals(BrainModelRole.CORE_BRAIN, diagnostics.selectedRole)
        assertNotNull(diagnostics.parsedBrainAction)
        assertFalse(diagnostics.validatorResult)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("local_model_unavailable", diagnostics.finalBrainAction.intent)
        assertTrue(diagnostics.finalSafetyDecision.allowed)
        assertTrue(diagnostics.runtimeStatus?.fallbackActive == true)
    }

    @Test
    fun `multilingual backup route is accepted when selected by the bridge`() {
        val multilingualAction = safeAction(
            intent = "translate",
            reply = "मैं मदद कर सकता हूं.",
            rawText = "कृपया मुझे समझाओ"
        )
        val service = serviceFor(
            bridge = bridgeFor(BrainModelRole.MULTILINGUAL_BACKUP),
            coreModel = staticModel(BrainModelRole.CORE_BRAIN, multilingualAction, PhoneLocalLlmModelId.GEMMA_3N),
            multilingualModel = staticModel(BrainModelRole.MULTILINGUAL_BACKUP, multilingualAction, PhoneLocalLlmModelId.QWEN_1_5B),
            liteModel = staticModel(BrainModelRole.LITE_FALLBACK, multilingualAction, PhoneLocalLlmModelId.GEMMA_3_270M)
        )

        val diagnostics = service.diagnose("कृपया मुझे समझाओ")

        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, diagnostics.selectedRole)
        assertEquals(multilingualAction, diagnostics.finalBrainAction)
        assertTrue(diagnostics.validatorResult)
        assertEquals(PhoneLocalLlmModelId.QWEN_1_5B.wireValue, diagnostics.runtimeStatus?.selectedLocalModelId)
        assertEquals("ready", diagnostics.runtimeStatus?.selectedLocalModelStatus)
    }

    private fun serviceFor(
        bridge: BrainRouterBridge,
        coreModel: PhoneBrainModel,
        multilingualModel: PhoneBrainModel,
        liteModel: PhoneBrainModel
    ): BrainService {
        return BrainService(
            localBrainRouterBridge = bridge,
            coreBrainModel = coreModel,
            multilingualBackupModel = multilingualModel,
            liteFallbackModel = liteModel
        )
    }

    private fun bridgeFor(role: BrainModelRole): BrainRouterBridge {
        return object : BrainRouterBridge {
            override fun selectLocalRoute(
                request: BrainRequest,
                allowOnlineHelper: Boolean
            ): BrainRouteDecision? {
                return BrainRouteDecision(
                    selectedRole = role,
                    reason = "${role.wireValue} is runtime-ready.",
                    requiresInternet = false,
                    requiresScreenContext = false,
                    fallbackAllowed = true,
                    safetyNotes = listOf("Safe local route.")
                )
            }
        }
    }

    private fun staticModel(
        role: BrainModelRole,
        action: BrainAction,
        modelId: PhoneLocalLlmModelId
    ): PhoneBrainModel {
        val response = BrainActionJsonCodec().encode(action)
        return object : PhoneBrainModel {
            override val role: BrainModelRole = role
            override val available: Boolean = true

            override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
                return BrainModelResult.available(
                    role = role,
                    candidateAction = action,
                    rawResponse = response,
                    reason = "Safe local candidate.",
                    safetyNotes = routeDecision.safetyNotes,
                    localModelId = modelId,
                    localModelDisplayName = modelId.displayName,
                    localModelStatus = PhoneLocalLlmStatus.READY,
                    promptBuilt = true,
                    jsonParsed = true
                )
            }
        }
    }

    private fun safeAction(
        intent: String,
        reply: String,
        rawText: String
    ): BrainAction {
        return BrainAction(
            intent = intent,
            reply = reply,
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to rawText)
        )
    }
}
