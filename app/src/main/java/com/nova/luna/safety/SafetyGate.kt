package com.nova.luna.safety

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.SafetyLevel
import java.util.Locale

class SafetyGate {
    private val blockedPatterns = listOf(
        "send money",
        "pay",
        "payment",
        "order",
        "buy",
        "checkout",
        "bank",
        "upi",
        "password",
        "otp",
        "captcha"
    )

    fun evaluate(commandIntent: CommandIntent): SafetyDecision {
        val normalized = commandIntent.normalizedText.lowercase(Locale.US)

        if (commandIntent.actionType == ActionType.STOP_SERVICE) {
            return SafetyDecision.allow("Stop command accepted.")
        }

        if (commandIntent.actionType == ActionType.CAB_BOOKING) {
            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = "Cab booking flow allowed. Final booking, payment, OTP, and CAPTCHA steps must stay manual.",
                requiresBiometric = false
            )
        }

        if (commandIntent.actionType == ActionType.FOOD_ORDER) {
            if (containsUnsafeFoodKeyword(normalized) || containsUnsafeFoodKeyword(commandIntent.entities["foodItem"].orEmpty())) {
                return SafetyDecision.block(
                    "Blocked: alcohol, tobacco, medicines, restricted items, and other unsafe food orders must stay manual."
                )
            }

            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = "Food ordering flow allowed. Final order placement, payment, OTP, and CAPTCHA steps must stay manual.",
                requiresBiometric = false
            )
        }

        if (containsBlockedKeyword(normalized)) {
            return SafetyDecision.block(
                "Blocked: payments, banking, passwords, OTPs, CAPTCHAs, and checkout flows must stay manual."
            )
        }

        return when (commandIntent.actionType) {
            ActionType.CALL_CONTACT,
            ActionType.TAKE_SCREENSHOT -> {
                SafetyDecision.requireBiometric(
                    "This is a sensitive command. Use the app to confirm with biometrics before trying again."
                )
            }

            ActionType.OPEN_SETTINGS,
            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> {
                SafetyDecision(
                    level = SafetyLevel.SAFE,
                    allowed = true,
                    message = "Command allowed.",
                    requiresBiometric = false
                )
            }

            ActionType.OPEN_USAGE_ACCESS_SETTINGS -> {
                if (normalized.contains("usage") &&
                    (normalized.contains("settings") || normalized.contains("permission"))
                ) {
                    SafetyDecision(
                        level = SafetyLevel.SAFE,
                        allowed = true,
                        message = "Command allowed.",
                        requiresBiometric = false
                    )
                } else {
                    SafetyDecision.requireBiometric(
                        "This is a sensitive command. Use the app to confirm with biometrics before trying again."
                    )
                }
            }

            ActionType.BLOCKED -> SafetyDecision.block("Blocked command.")
            else -> SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = "Command allowed.",
                requiresBiometric = false
            )
        }
    }

    private fun containsBlockedKeyword(normalized: String): Boolean {
        return blockedPatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(normalized)
        }
    }

    private fun containsUnsafeFoodKeyword(value: String): Boolean {
        val normalized = value.lowercase(Locale.US)
        if (normalized.isBlank()) return false

        val unsafePatterns = listOf(
            "alcohol",
            "beer",
            "wine",
            "whiskey",
            "whisky",
            "vodka",
            "rum",
            "gin",
            "scotch",
            "liquor",
            "cocktail",
            "tobacco",
            "cigarette",
            "cigarettes",
            "vape",
            "weed",
            "cannabis",
            "marijuana",
            "medicine",
            "medicines",
            "pill",
            "pills",
            "tablet",
            "tablets",
            "capsule",
            "capsules",
            "syrup",
            "drug",
            "drugs",
            "restricted item",
            "restricted items",
            "unsafe item",
            "unsafe items"
        )

        return unsafePatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(normalized)
        }
    }
}
