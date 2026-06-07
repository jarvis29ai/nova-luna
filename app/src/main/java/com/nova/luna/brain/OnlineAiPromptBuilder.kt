package com.nova.luna.brain

import com.nova.luna.model.BrainRouteDecision

class OnlineAiPromptBuilder {
    fun buildBrainActionPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        config: OnlineAiConfig,
        policyResult: OnlineAiPolicyResult,
        privacyResult: OnlineAiPrivacyFilterResult
    ): String {
        val sanitizedText = privacyResult.sanitizedText.trim()
        val safeSanitizedText = reinforcePromptSafety(sanitizedText)
        return buildString {
            appendLine("You are an optional helper for Nova/Luna.")
            appendLine("You do not control the phone.")
            appendLine("You only provide safe research, content, summaries, drafts, or suggestions.")
            appendLine("You must not ask for OTP, PIN, CVV, password, CAPTCHA, biometric, or payment secrets.")
            appendLine("You must not instruct direct payment or unsafe automation.")
            appendLine("You must not claim you completed phone actions.")
            appendLine("Return one strict BrainAction JSON object only.")
            appendLine("Keep finalActionAllowed false.")
            appendLine("If the task is sensitive, reply with a safe draft, a summary, or a manual-handoff suggestion.")
            appendLine("If the task is better handled locally, still return a safe READ_ONLY or PREPARE BrainAction candidate.")
            appendLine("Selected role: ${routeDecision.selectedRole.wireValue}")
            appendLine("Route reason: ${routeDecision.reason}")
            appendLine("Policy decision: ${policyResult.decision.wireValue}")
            appendLine("Policy reason: ${policyResult.reason}")
            appendLine("Provider enabled: ${config.enabled}")
            appendLine("Require confirmation: ${config.requireConfirmation}")
            appendLine("Sanitized input:")
            appendLine(safeSanitizedText)
            if (request.activeCabSession || request.activeFoodSession || request.activeGrocerySession) {
                appendLine("Active session context is present. Keep the answer safe and local-first.")
            }
        }
    }

    private fun reinforcePromptSafety(text: String): String {
        var current = text
        val patterns = listOf(
            Regex("""(?i)\bsecret\d+\b"""),
            Regex("""(?i)\b(password|passcode|otp|one time password|verification code|cvv|cvc|upi pin|card pin|pin|token|api key|access token|bearer token)\b(?:\s*(?:is|:|=)\s*)?[^\s,.;]+""")
        )

        patterns.forEach { pattern ->
            current = pattern.replace(current, "[REDACTED]")
        }

        return current
    }
}
