package com.nova.luna.brain

import com.nova.luna.model.InternetPermissionCategory
import com.nova.luna.util.AssistantTextNormalizer

data class OnlineAiPolicyResult(
    val decision: OnlineAiPolicyDecision,
    val reason: String,
    val shouldUseOnline: Boolean,
    val shouldFallbackLocal: Boolean,
    val requiresUserConsent: Boolean,
    val sanitizedText: String,
    val redactionCount: Int,
    val privacyBlocked: Boolean,
    val privacyDecision: OnlineAiPolicyDecision? = null
)

class OnlineAiPolicy(
    private val internetPermissionPolicy: InternetPermissionPolicy = InternetPermissionPolicy(),
    private val privacyFilter: OnlineAiPrivacyFilter = OnlineAiPrivacyFilter()
) {
    fun isPotentialCandidate(request: BrainRequest): Boolean {
        val normalized = normalize(request.rawText)
        if (normalized.isBlank()) return false
        if (normalized == "compare") return false
        if (request.activeCabSession || request.activeFoodSession || request.activeGrocerySession) return false
        if (request.screenState != null) return true

        val internetDecision = internetPermissionPolicy.classify(request.rawText)
        if (internetDecision.category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO ||
            internetDecision.category == InternetPermissionCategory.INTERNET_OPTIONAL
        ) {
            return true
        }

        val researchSignals = listOf(
            "latest",
            "current best",
            "current",
            "recent",
            "compare",
            "comparison",
            "research",
            "review",
            "article",
            "public article",
            "news",
            "price",
            "prices",
            "offer",
            "offers",
            "spec",
            "specs",
            "best laptop",
            "best phone",
            "best washing machine",
            "best product"
        )

        val contentSignals = listOf(
            "write a long",
            "write a detailed",
            "create a detailed",
            "professional email",
            "email draft",
            "draft email",
            "blog",
            "long blog",
            "ppt",
            "presentation",
            "canva prompt",
            "prompt for",
            "summary",
            "summarize",
            "translate",
            "report",
            "proposal",
            "outline"
        )

        return researchSignals.any { containsPhrase(normalized, it) } ||
            contentSignals.any { containsPhrase(normalized, it) }
    }

    fun evaluate(
        request: BrainRequest,
        config: OnlineAiConfig,
        networkStatusProvider: NetworkStatusProvider,
        userConsentGiven: Boolean
    ): OnlineAiPolicyResult {
        val sanitizedConfig = config.sanitized()
        val privacyResult = privacyFilter.filter(request, sanitizedConfig)

        if (!sanitizedConfig.enabled) {
            return buildResult(
                decision = OnlineAiPolicyDecision.DENY_USER_DISABLED,
                reason = "Online helper is disabled by config.",
                shouldUseOnline = false,
                shouldFallbackLocal = true,
                requiresUserConsent = false,
                privacyResult = privacyResult
            )
        }

        if (!networkStatusProvider.isInternetAvailable()) {
            return buildResult(
                decision = OnlineAiPolicyDecision.DENY_NO_INTERNET,
                reason = "Online helper needs internet and none is currently available.",
                shouldUseOnline = false,
                shouldFallbackLocal = true,
                requiresUserConsent = false,
                privacyResult = privacyResult
            )
        }

        if (privacyResult.blocked) {
            return buildResult(
                decision = privacyResult.blockedDecision ?: OnlineAiPolicyDecision.DENY_PRIVACY,
                reason = privacyResult.reason,
                shouldUseOnline = false,
                shouldFallbackLocal = true,
                requiresUserConsent = false,
                privacyResult = privacyResult
            )
        }

        if (!isPotentialCandidate(request)) {
            return buildResult(
                decision = OnlineAiPolicyDecision.FALLBACK_LOCAL,
                reason = "This request is better handled by the local brain.",
                shouldUseOnline = false,
                shouldFallbackLocal = true,
                requiresUserConsent = false,
                privacyResult = privacyResult
            )
        }

        if (isDirectSensitiveRequest(request.rawText)) {
            return buildResult(
                decision = OnlineAiPolicyDecision.DENY_SENSITIVE,
                reason = "Sensitive finance, login, or biometric content must stay local.",
                shouldUseOnline = false,
                shouldFallbackLocal = true,
                requiresUserConsent = false,
                privacyResult = privacyResult
            )
        }

        if (sanitizedConfig.requireConfirmation && !userConsentGiven) {
            return buildResult(
                decision = OnlineAiPolicyDecision.ASK_USER_PERMISSION,
                reason = "This needs online help. Say yes to use online AI for this task.",
                shouldUseOnline = false,
                shouldFallbackLocal = false,
                requiresUserConsent = true,
                privacyResult = privacyResult
            )
        }

        return buildResult(
            decision = OnlineAiPolicyDecision.ALLOW,
            reason = "Online helper is allowed for this request.",
            shouldUseOnline = true,
            shouldFallbackLocal = false,
            requiresUserConsent = false,
            privacyResult = privacyResult
        )
    }

    private fun buildResult(
        decision: OnlineAiPolicyDecision,
        reason: String,
        shouldUseOnline: Boolean,
        shouldFallbackLocal: Boolean,
        requiresUserConsent: Boolean,
        privacyResult: OnlineAiPrivacyFilterResult
    ): OnlineAiPolicyResult {
        return OnlineAiPolicyResult(
            decision = decision,
            reason = reason,
            shouldUseOnline = shouldUseOnline,
            shouldFallbackLocal = shouldFallbackLocal,
            requiresUserConsent = requiresUserConsent,
            sanitizedText = privacyResult.sanitizedText,
            redactionCount = privacyResult.redactionCount,
            privacyBlocked = privacyResult.blocked,
            privacyDecision = privacyResult.blockedDecision
        )
    }

    private fun isDirectSensitiveRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        val sensitiveKeywords = listOf(
            "otp",
            "one time password",
            "password",
            "passcode",
            "cvv",
            "cvc",
            "upi pin",
            "card pin",
            "captcha",
            "biometric",
            "fingerprint",
            "face unlock",
            "bank",
            "banking",
            "payment",
            "pay now",
            "pay with",
            "send money",
            "transfer money"
        )

        return sensitiveKeywords.any { keyword -> containsPhrase(normalized, keyword) }
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return normalized.contains(target)
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }
}
