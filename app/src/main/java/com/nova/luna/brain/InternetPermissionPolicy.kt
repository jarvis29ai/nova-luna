package com.nova.luna.brain

import com.nova.luna.model.InternetPermissionCategory
import com.nova.luna.model.InternetPermissionDecision
import com.nova.luna.util.AssistantTextNormalizer

class InternetPermissionPolicy {
    private val blockedSensitivePatterns = listOf(
        "send money",
        "payment",
        "pay now",
        "pay with",
        "pay to",
        "pay money",
        "bank",
        "banking",
        "upi",
        "password",
        "otp",
        "one time password",
        "captcha",
        "login",
        "sign in",
        "delete",
        "erase",
        "remove account",
        "purchase confirmation",
        "order confirmation",
        "final booking",
        "checkout",
        "confirm booking",
        "book now",
        "book ride",
        "book it without asking",
        "complete payment"
    )

    private val paymentAmountRegex = Regex("""\bpay\b\s+\d+""")

    private val internetRequiredInfoPatterns = listOf(
        "weather",
        "forecast",
        "latest news",
        "news update",
        "current news",
        "current price",
        "stock price",
        "share price",
        "exchange rate",
        "currency rate",
        "route",
        "directions",
        "traffic",
        "map",
        "train status",
        "flight status",
        "current time",
        "time in",
        "latest",
        "search the web",
        "look up",
        "google",
        "wikipedia",
        "translate",
        "definition"
    )

    private val internetOptionalPatterns = listOf(
        "search",
        "find",
        "browse",
        "look up"
    )

    fun classify(rawText: String): InternetPermissionDecision {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return InternetPermissionDecision(
                category = InternetPermissionCategory.LOCAL_ONLY,
                reason = "Empty input stays local."
            )
        }

        if (containsAny(normalized, blockedSensitivePatterns)) {
            return InternetPermissionDecision(
                category = InternetPermissionCategory.BLOCKED_SENSITIVE,
                reason = "Payments, OTP, login, CAPTCHA, delete, and final booking stay manual."
            )
        }

        if (paymentAmountRegex.containsMatchIn(normalized)) {
            return InternetPermissionDecision(
                category = InternetPermissionCategory.BLOCKED_SENSITIVE,
                reason = "Payments, OTP, login, CAPTCHA, delete, and final booking stay manual."
            )
        }

        if (containsAny(normalized, internetRequiredInfoPatterns)) {
            return InternetPermissionDecision(
                category = InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO,
                reason = "This is live information that may need the internet."
            )
        }

        if (containsAny(normalized, internetOptionalPatterns)) {
            return InternetPermissionDecision(
                category = InternetPermissionCategory.INTERNET_OPTIONAL,
                reason = "Internet can help, but the command can still be handled locally."
            )
        }

        return InternetPermissionDecision(
            category = InternetPermissionCategory.LOCAL_ONLY,
            reason = "This can stay on-device."
        )
    }

    private fun containsAny(normalized: String, patterns: List<String>): Boolean {
        return patterns.any { pattern ->
            val target = normalize(pattern)
            if (target.isBlank()) {
                false
            } else {
                Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
            }
        }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }
}
