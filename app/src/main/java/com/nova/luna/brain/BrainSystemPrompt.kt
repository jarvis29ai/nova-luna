package com.nova.luna.brain

object BrainSystemPrompt {
    fun build(request: BrainRequest): String {
        val routeDecision = com.nova.luna.model.BrainRouteDecision(
            selectedRole = com.nova.luna.model.BrainModelRole.GEMMA_REASONING,
            reason = "Compatibility prompt for the local phone brain.",
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf("Strict JSON output contract is required.")
        )

        return PhoneLocalLlmPromptBuilder().buildBrainActionPrompt(
            request = request,
            routeDecision = routeDecision,
            model = PhoneLocalLlmConfig.defaultModelStack().first(),
            readiness = PhoneLocalLlmReadiness(
                status = PhoneLocalLlmStatus.READY,
                selectedModel = PhoneLocalLlmConfig.defaultModelStack().first(),
                runtimeAvailable = true,
                assetMissing = false,
                reason = "Compatibility prompt is ready.",
                availableModelIds = listOf(PhoneLocalLlmModelId.GEMMA_3N),
                timeoutMs = 5_000,
                maxInputTokens = 4_096,
                maxPromptChars = 8_192
            )
        )
    }
}
