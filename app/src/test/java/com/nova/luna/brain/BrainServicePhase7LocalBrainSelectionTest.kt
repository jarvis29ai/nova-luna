package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.modelinstall.ModelInstallBrainRouterBridge
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.singleFilePack
import com.nova.luna.modelinstall.seedReadyPack
import com.nova.luna.modelinstall.withLocalRuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase7LocalBrainSelectionTest {
    @Test
    fun `failed core model is suppressed and the next request falls back to lite`() {
        val corePayload = ByteArray(100_000_001)
        val litePayload = ByteArray(100_000_001)
        val corePack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            relativePath = "core",
            payload = corePayload
        )
        val litePack = singleFilePack(
            packId = ModelPackId.LITE,
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            relativePath = "lite",
            payload = litePayload
        )

        withLocalRuntimeEnvironment(catalog = listOf(corePack, litePack)) { env ->
            seedReadyPack(
                env = env,
                pack = corePack,
                payloads = mapOf("core/qwen2.5-0.5b-instruct-q4_k_m.gguf" to corePayload)
            )
            seedReadyPack(
                env = env,
                pack = litePack,
                payloads = mapOf("lite/qwen2.5-0.5b-instruct-q4_k_m.gguf" to litePayload)
            )

            val bridge = ModelInstallBrainRouterBridge(env.modelInstallService)
            val service = BrainService(
                localBrainRouterBridge = bridge,
                coreBrainModel = failingModel(BrainModelRole.CORE_BRAIN),
                multilingualBackupModel = safeModel(
                    role = BrainModelRole.MULTILINGUAL_BACKUP,
                    reply = "मैं मदद कर सकता हूं.",
                    modelId = PhoneLocalLlmModelId.QWEN_1_5B
                ),
                liteFallbackModel = safeModel(
                    role = BrainModelRole.LITE_FALLBACK,
                    reply = "Lite fallback is ready.",
                    modelId = PhoneLocalLlmModelId.GEMMA_3_270M
                ),
                modelInstallService = env.modelInstallService
            )

            val initialSelection = bridge.selectLocalRoute(
                BrainRequest("please explain how offline model verification works")
            )
            assertEquals(BrainModelRole.CORE_BRAIN, initialSelection?.selectedRole)

            val firstAction = service.process("please explain how offline model verification works")
            assertEquals("local_model_unavailable", firstAction.intent)
            assertTrue(firstAction.reply.contains("basic commands", ignoreCase = true))

            val fallbackSelection = bridge.selectLocalRoute(
                BrainRequest("please explain how offline model verification works")
            )
            assertEquals(BrainModelRole.LITE_FALLBACK, fallbackSelection?.selectedRole)

            val secondAction = service.process("please explain how offline model verification works")
            assertEquals("explain", secondAction.intent)
            assertEquals("Lite fallback is ready.", secondAction.reply)
        }
    }

    private fun failingModel(role: BrainModelRole): PhoneBrainModel {
        return object : PhoneBrainModel {
            override val role: BrainModelRole = role
            override val available: Boolean = true

            override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
                return BrainModelResult.unavailable(
                    role = role,
                    reason = "Local model runtime failed for ${role.wireValue}.",
                    safetyNotes = routeDecision.safetyNotes,
                    localModelId = PhoneLocalLlmModelId.GEMMA_3N,
                    localModelDisplayName = "Broken ${role.wireValue}",
                    localModelStatus = PhoneLocalLlmStatus.RUNTIME_UNAVAILABLE
                )
            }
        }
    }

    private fun safeModel(
        role: BrainModelRole,
        reply: String,
        modelId: PhoneLocalLlmModelId
    ): PhoneBrainModel {
        val action = BrainAction(
            intent = "explain",
            reply = reply,
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "please explain how offline model verification works")
        )
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
}
