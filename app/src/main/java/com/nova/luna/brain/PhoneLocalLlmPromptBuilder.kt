package com.nova.luna.brain

import com.nova.luna.model.BrainRouteDecision

class PhoneLocalLlmPromptBuilder {
    fun buildBrainActionPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        model: PhoneLocalLlmModelConfig,
        readiness: PhoneLocalLlmReadiness? = null
    ): String {
        return buildString {
            appendLine("You are Nova/Luna's local brain.")
            appendLine("You are Nova/Luna's local phone brain.")
            appendLine("You must preserve the user's language, including mixed Hindi-English or Hinglish when applicable.")
            appendLine("You cannot control the phone directly.")
            appendLine("You must output strict JSON only when asked for BrainAction.")
            appendLine("Never output markdown, code fences, or explanation outside the JSON object.")
            appendLine("SafetyGate must still block risky final actions.")
            appendLine("Never enter OTP, PIN, CVV, password, passcode, CAPTCHA, biometric, payment, send money, login-bypass, delete-account, share, follow, subscribe, or post actions directly.")
            appendLine("You cannot complete payment or send money.")
            appendLine("You cannot place a final booking, order, send, share, follow, subscribe, or post without confirmation.")
            appendLine("You must ask the user when unsure.")
            appendLine("You must use installed or user-approved apps only.")
            appendLine("You must keep final execution local and safety-gated.")
            appendLine("You must return human_only/manual_handoff or blocked for sensitive actions.")
            appendLine("You may produce safe summarize, draft, rewrite, explain, or planning BrainAction JSON when the request is non-sensitive.")
            appendLine()
            appendLine("Required JSON schema:")
            appendLine("{")
            appendLine("""  "intent": "string",""")
            appendLine("""  "reply": "string",""")
            appendLine("""  "actionType": "none|read_only|prepare|external_action|human_only",""")
            appendLine("""  "riskLevel": "safe|confirmation_required|blocked",""")
            appendLine("""  "requiresConfirmation": true|false,""")
            appendLine("""  "finalActionAllowed": true|false,""")
            appendLine("""  "params": { "any_key": "string" },""")
            appendLine("  \"nextQuestion\": \"string or null\"")
            appendLine("}")
            appendLine()
            appendLine("Reply in the same language as the user whenever possible.")
            appendLine("When a final step is risky, keep finalActionAllowed false and ask for confirmation.")
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
            appendLine("- userText: ${request.rawText}")
            request.screenState?.let { screenState ->
                appendLine("- screenSummary: ${screenState.summarizedState}")
                appendLine("- foregroundPackage: ${screenState.packageName}")
            }
        }.trimIndent()
    }

    fun buildSafeTextPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        model: PhoneLocalLlmModelConfig,
        readiness: PhoneLocalLlmReadiness? = null
    ): String {
        return buildString {
            appendLine("You are Nova/Luna's local phone brain.")
            appendLine("Respond in the user's language and keep the reply safe, short, and local.")
            appendLine("Do not mention hidden reasoning.")
            appendLine("Do not use internet, backend, cloud, or paid APIs.")
            appendLine("Do not control the phone directly.")
            appendLine("Do not enter OTP, PIN, CVV, password, CAPTCHA, biometric, or payment secrets.")
            appendLine("If the user asks for a risky action, ask for manual confirmation or say it must stay manual.")
            appendLine("If the request is safe, reply clearly and helpfully.")
            appendLine()
            appendLine("Context:")
            appendLine("- role: ${routeDecision.selectedRole.wireValue}")
            appendLine("- model: ${model.id.displayName}")
            readiness?.let {
                appendLine("- readiness: ${it.status.wireValue}")
            }
            appendLine("- userText: ${request.rawText}")
        }.trimIndent()
    }
}
