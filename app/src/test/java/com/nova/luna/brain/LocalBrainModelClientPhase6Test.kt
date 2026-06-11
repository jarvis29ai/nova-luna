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

class LocalBrainModelClientPhase6Test {
    @Test
    fun `multilingual backup prompt is strict and produces a validated candidate`() {
        val action = safeAction(
            intent = "translate",
            reply = "मैं मदद कर सकता हूं.",
            rawText = "कृपया मुझे समझाओ"
        )
        val engine = RecordingEngine(
            response = BrainActionJsonCodec().encode(action),
            reportedModelId = PhoneLocalLlmModelId.QWEN_1_5B,
            reportedModelDisplayName = PhoneLocalLlmModelId.QWEN_1_5B.displayName
        )
        val client = LocalBrainModelClient(
            role = BrainModelRole.MULTILINGUAL_BACKUP,
            roleReadinessProvider = BrainRoleReadinessProvider { true },
            engine = engine
        )

        val result = client.generate(
            request = BrainRequest("कृपया मुझे समझाओ"),
            routeDecision = routeDecision(BrainModelRole.MULTILINGUAL_BACKUP)
        )

        assertTrue(result.available)
        assertEquals(action, result.candidateAction)
        assertEquals(PhoneLocalLlmModelId.QWEN_1_5B, result.localModelId)
        assertEquals(PhoneLocalLlmStatus.READY, result.localModelStatus)
        assertNotNull(engine.lastPrompt)
        assertTrue(engine.lastPrompt!!.contains("Multilingual Backup guidance"))
        assertTrue(engine.lastPrompt!!.contains("candidate JSON only"))
        assertFalse(engine.lastPrompt!!.contains("model_install"))
    }

    @Test
    fun `direct execution hints are rejected by the local candidate validator`() {
        val unsafeAction = BrainAction(
            intent = "explain",
            reply = "Call ActionExecutor directly.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf("rawText" to "please explain how offline model verification works")
        )
        val engine = RecordingEngine(
            response = BrainActionJsonCodec().encode(unsafeAction),
            reportedModelId = PhoneLocalLlmModelId.GEMMA_3N,
            reportedModelDisplayName = PhoneLocalLlmModelId.GEMMA_3N.displayName
        )
        val client = LocalBrainModelClient(
            role = BrainModelRole.CORE_BRAIN,
            roleReadinessProvider = BrainRoleReadinessProvider { true },
            engine = engine
        )

        val result = client.generate(
            request = BrainRequest("please explain how offline model verification works"),
            routeDecision = routeDecision(BrainModelRole.CORE_BRAIN)
        )

        assertFalse(result.available)
        assertEquals(PhoneLocalLlmStatus.VALIDATION_REJECTED, result.localModelStatus)
        assertTrue(result.reason.contains("rejected", ignoreCase = true))
        assertTrue(engine.lastPrompt?.contains("Core Brain guidance") == true)
    }

    private fun routeDecision(role: BrainModelRole): BrainRouteDecision {
        return BrainRouteDecision(
            selectedRole = role,
            reason = "${role.wireValue} is ready.",
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf("Candidate JSON only.")
        )
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

    private class RecordingEngine(
        private val response: String,
        private val reportedModelId: PhoneLocalLlmModelId,
        private val reportedModelDisplayName: String
    ) : PhoneLocalLlmEngine {
        var lastPrompt: String? = null
            private set

        override val engineName: String = "RecordingEngine"

        override fun available(): Boolean = true

        override fun readinessStatus(): PhoneLocalLlmStatus = PhoneLocalLlmStatus.READY

        override fun modelId(): PhoneLocalLlmModelId = reportedModelId

        override fun modelDisplayName(): String = reportedModelDisplayName

        override fun maxInputTokens(): Int = 4_096

        override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
            lastPrompt = prompt
            return PhoneLocalLlmGenerationResult(
                status = PhoneLocalLlmStatus.READY,
                text = response,
                reason = "Generated for test.",
                modelId = reportedModelId,
                modelDisplayName = reportedModelDisplayName,
                latencyMillis = 1L,
                jsonOnly = true
            )
        }

        override fun cancel(): Boolean = false

        override fun unload(): Boolean = true

        override fun diagnostics(): String = "recording"
    }
}
