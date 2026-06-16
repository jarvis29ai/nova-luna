package com.nova.luna.brain

import com.nova.luna.model.BrainRouteDecision

class PhoneLocalLlmPromptBuilder {
    fun buildBrainActionPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        model: PhoneLocalLlmModelConfig,
        readiness: PhoneLocalLlmReadiness? = null
    ): String {
        val systemPrompt = buildString {
            appendLine("You are Nova/Luna's local phone brain.")
            appendLine("Return exactly one JSON object and nothing else.")
            appendLine("Return candidate JSON only.")
            appendLine("Never use markdown, code fences, or prose outside the JSON object.")
            appendLine("Preserve the user's language when possible, including Hindi, Hinglish, and regional language when applicable.")
            appendLine("Use safe, local, safety-gated action planning only.")
            appendLine("SafetyGate still decides whether any candidate may execute.")
            appendLine("Examples of risky actions include send money, OTP, login, CAPTCHA, and delete.")
            appendLine("Do not claim execution success for payments, OTP, login, CAPTCHA, or destructive actions.")
            appendLine("For risky requests, set confirmationRequired true and keep the action manual.")
            appendLine("Required fields: schemaVersion or schema_version, intent, actionType or action_type, riskLevel or risk_level, params, confirmationRequired or requires_confirmation.")
            appendLine("Recommended fields: reply, assistantReply or assistant_reply, reason, confidence, finalActionAllowed or final_action_allowed, source, nextQuestion or next_question.")
            appendLine()
            appendLine("Request context:")
            appendLine("- selectedRole: ${routeDecision.selectedRole.wireValue}")
            appendLine("- routeReason: ${routeDecision.reason}")
            appendLine("- modelId: ${model.id.wireValue}")
            appendLine("- modelDisplayName: ${model.id.displayName}")
            appendLine("- modelRole: ${model.id.roleLabel}")
            appendLine("- modelPriority: ${model.priority}")
            appendLine("- maxInputTokens: ${model.maxInputTokens}")
            appendLine("- maxPromptChars: ${model.maxPromptChars}")
            appendLine("- timeoutMs: ${model.timeoutMs}")
            readiness?.let {
                appendLine("- readinessStatus: ${it.status.wireValue}")
                appendLine("- runtimeAvailable: ${it.runtimeAvailable}")
                appendLine("- assetMissing: ${it.assetMissing}")
                appendLine("- selectedModel: ${it.selectedModelDisplayName.orEmpty()}")
            }
            appendLine("- activeCabSession: ${request.activeCabSession}")
            appendLine("- activeFoodSession: ${request.activeFoodSession}")
            appendLine("- activeGrocerySession: ${request.activeGrocerySession}")
            appendLine("- userText: ${request.rawText.trim()}")
            if (routeDecision.safetyNotes.isNotEmpty()) {
                appendLine("- safetyNotes: ${routeDecision.safetyNotes.joinToString(" | ")}")
            }
            request.screenState?.let { screenState ->
                appendLine("- screenSummary: ${screenState.summarizedState}")
                appendLine("- foregroundPackage: ${screenState.packageName}")
            }
        }.trimEnd()

        return buildString {
            appendLine("<|im_start|>system")
            appendLine(systemPrompt)
            appendLine("<|im_end|>")
            appendLine("<|im_start|>user")
            appendLine(request.rawText.trim())
            appendLine("<|im_end|>")
            appendLine("<|im_start|>assistant")
        }.trimEnd()
    }

    fun buildSafeTextPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        model: PhoneLocalLlmModelConfig,
        readiness: PhoneLocalLlmReadiness? = null
    ): String {
        val systemPrompt = buildString {
            appendLine("You are Nova/Luna's local phone brain.")
            appendLine("Respond safely, briefly, and locally.")
            appendLine("Do not mention hidden reasoning.")
            appendLine("Do not use internet, backend, cloud, or paid APIs.")
            appendLine("Do not control the phone directly.")
            appendLine("Do not enter OTP, PIN, CVV, password, CAPTCHA, biometric, or payment secrets.")
            appendLine("If the user asks for a risky action, explain that it must stay manual.")
            appendLine("If the request is safe, reply clearly and helpfully.")
            appendLine()
            appendLine("Context:")
            appendLine("- role: ${routeDecision.selectedRole.wireValue}")
            appendLine("- model: ${model.id.displayName}")
            readiness?.let {
                appendLine("- readiness: ${it.status.wireValue}")
            }
        }.trimEnd()

        return buildString {
            appendLine("<|im_start|>system")
            appendLine(systemPrompt)
            appendLine("<|im_end|>")
            appendLine("<|im_start|>user")
            appendLine(request.rawText.trim())
            appendLine("<|im_end|>")
            appendLine("<|im_start|>assistant")
        }.trimEnd()
    }
}
