package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLocalLlmPromptBuilderTest {
    @Test
    fun `brain action prompt keeps the strict json contract visible`() {
        val model = PhoneLocalLlmConfig.defaultModelStack().first()
        val readiness = PhoneLocalLlmReadiness(
            status = PhoneLocalLlmStatus.READY,
            selectedModel = model,
            runtimeAvailable = true,
            assetMissing = false,
            reason = "Ready.",
            availableModelIds = listOf(model.id),
            timeoutMs = 5_000,
            maxInputTokens = 4_096,
            maxPromptChars = 8_192
        )

        val prompt = PhoneLocalLlmPromptBuilder().buildBrainActionPrompt(
            request = BrainRequest("please help me rewrite this note"),
            routeDecision = BrainRouteDecision(
                selectedRole = BrainModelRole.GEMMA_REASONING,
                reason = "Flexible local reasoning request.",
                requiresInternet = false,
                requiresScreenContext = false,
                fallbackAllowed = true,
                safetyNotes = listOf("Strict JSON only.")
            ),
            model = model,
            readiness = readiness
        )

        assertTrue(prompt.contains("strict JSON only", ignoreCase = true))
        assertTrue(prompt.contains("Hinglish", ignoreCase = true))
        assertTrue(prompt.contains("schemaVersion or schema_version"))
        assertTrue(prompt.contains("nextQuestion or next_question"))
        assertTrue(prompt.contains("selectedRole: gemma_reasoning"))
        assertTrue(prompt.contains("userText: please help me rewrite this note"))
    }
}
