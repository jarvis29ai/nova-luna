package com.nova.luna.brain

import com.nova.luna.screen.ScreenState
import com.nova.luna.util.AssistantTextNormalizer

data class OnlineAiPrivacyFilterResult(
    val sanitizedText: String,
    val redactionCount: Int,
    val blocked: Boolean,
    val reason: String,
    val blockedDecision: OnlineAiPolicyDecision? = null
)

class OnlineAiPrivacyFilter {
    fun filter(request: BrainRequest, config: OnlineAiConfig): OnlineAiPrivacyFilterResult {
        val rawText = request.rawText.trim()
        if (rawText.isBlank()) {
            return OnlineAiPrivacyFilterResult(
                sanitizedText = rawText,
                redactionCount = 0,
                blocked = false,
                reason = "Empty input."
            )
        }

        if (request.screenState != null) {
            return if (config.sendScreenText) {
                val safeSummary = request.screenState.safeSummary()
                OnlineAiPrivacyFilterResult(
                    sanitizedText = safeSummary,
                    redactionCount = request.screenState.riskSignals.size,
                    blocked = false,
                    reason = "Screen text was converted into a safe summary."
                )
            } else {
                OnlineAiPrivacyFilterResult(
                    sanitizedText = request.screenState.safeSummary(),
                    redactionCount = request.screenState.riskSignals.size,
                    blocked = true,
                    reason = "Screen text is not sent online by default.",
                    blockedDecision = OnlineAiPolicyDecision.DENY_PRIVACY
                )
            }
        }

        if (!config.sendPrivateMessages && looksLikePrivateMessage(rawText)) {
            return OnlineAiPrivacyFilterResult(
                sanitizedText = rawText,
                redactionCount = 0,
                blocked = true,
                reason = "Private messages stay local by default.",
                blockedDecision = OnlineAiPolicyDecision.DENY_PRIVACY
            )
        }

        val redactedSecrets = redactSensitiveText(rawText)
        if (redactedSecrets.redactionCount > 0 && containsCriticalSensitiveContent(rawText)) {
            return OnlineAiPrivacyFilterResult(
                sanitizedText = redactedSecrets.text,
                redactionCount = redactedSecrets.redactionCount,
                blocked = true,
                reason = "Sensitive payment or login secrets must stay local.",
                blockedDecision = OnlineAiPolicyDecision.DENY_SENSITIVE
            )
        }

        return OnlineAiPrivacyFilterResult(
            sanitizedText = redactedSecrets.text,
            redactionCount = redactedSecrets.redactionCount,
            blocked = false,
            reason = if (redactedSecrets.redactionCount > 0) {
                "Sensitive fields were redacted before any online use."
            } else {
                "No sensitive content detected."
            }
        )
    }

    private fun redactSensitiveText(rawText: String): RedactionResult {
        var current = rawText
        var redactionCount = 0

        fun replaceAll(pattern: Regex, replacement: String): Boolean {
            val matches = pattern.findAll(current).toList()
            if (matches.isEmpty()) return false
            current = pattern.replace(current, replacement)
            redactionCount += matches.size
            return true
        }

        replaceAll(
            pattern = Regex("""\b(otp|one time password|verification code|email verification code)\b(?:\s*(?:is|:|=)\s*)?\b\d{4,8}\b""", RegexOption.IGNORE_CASE),
            replacement = "$1 [REDACTED_OTP]"
        )
        replaceAll(
            pattern = Regex("""\b(password|passcode)\b(?:\s*(?:is|:|=)\s*)?[^\s,.;]+""", RegexOption.IGNORE_CASE),
            replacement = "password [REDACTED_PASSWORD]"
        )
        replaceAll(
            pattern = Regex("""\b(upi pin|card pin|pin)\b(?:\s*(?:is|:|=)\s*)?\b\d{3,6}\b""", RegexOption.IGNORE_CASE),
            replacement = "$1 [REDACTED_PIN]"
        )
        replaceAll(
            pattern = Regex("""\b(cvv|cvc)\b(?:\s*(?:is|:|=)\s*)?\b\d{3,4}\b""", RegexOption.IGNORE_CASE),
            replacement = "$1 [REDACTED_CVV]"
        )
        replaceAll(
            pattern = Regex("""\b(?:\d[ -]*?){13,19}\b"""),
            replacement = "[REDACTED_CARD]"
        )
        replaceAll(
            pattern = Regex("""\b(api key|access token|bearer token|token)\b(?:\s*(?:is|:|=)\s*)?\S+""", RegexOption.IGNORE_CASE),
            replacement = "$1 [REDACTED_TOKEN]"
        )
        replaceAll(
            pattern = Regex("""\b(account number|bank id|upi id)\b(?:\s*(?:is|:|=)\s*)?\S+""", RegexOption.IGNORE_CASE),
            replacement = "$1 [REDACTED_ID]"
        )
        replaceAll(
            pattern = Regex("""\b(password|otp|cvv|cvc|pin|upi pin|card pin)\b""", RegexOption.IGNORE_CASE),
            replacement = "[REDACTED]"
        )

        return RedactionResult(current, redactionCount)
    }

    private fun containsCriticalSensitiveContent(rawText: String): Boolean {
        val normalized = normalize(rawText)
        val criticalPatterns = listOf(
            "otp",
            "one time password",
            "password",
            "passcode",
            "cvv",
            "cvc",
            "upi pin",
            "card pin",
            "bank",
            "banking",
            "payment",
            "pay now",
            "pay with",
            "login",
            "sign in",
            "captcha",
            "biometric",
            "fingerprint",
            "face unlock",
            "access token",
            "api key"
        )

        return criticalPatterns.any { keyword ->
            normalized.contains(normalize(keyword))
        }
    }

    private fun looksLikePrivateMessage(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return normalized.contains("message to") ||
            normalized.contains("reply to") ||
            normalized.contains("text message") ||
            normalized.contains("send message") ||
            normalized.contains("whatsapp message") ||
            normalized.contains("dm ")
    }

    private fun ScreenState.safeSummary(): String {
        val visible = visibleText.take(12)
        val buttons = possibleButtons.mapNotNull { it.primaryLabel() }.take(6)
        val fields = possibleSearchFields.mapNotNull { it.primaryLabel() }.take(4)
        return buildString {
            append("App: ").append(appName ?: packageName)
            append(". Summary: ").append(summarizedState)
            if (visible.isNotEmpty()) {
                append(". Visible text: ").append(visible.joinToString(separator = ", "))
            }
            if (buttons.isNotEmpty()) {
                append(". Buttons: ").append(buttons.joinToString(separator = ", "))
            }
            if (fields.isNotEmpty()) {
                append(". Fields: ").append(fields.joinToString(separator = ", "))
            }
        }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private data class RedactionResult(
        val text: String,
        val redactionCount: Int
    )
}
