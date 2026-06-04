package com.nova.luna.safety

import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.SafetyLevel
import java.util.Locale

class SafetyGate {
    private val blockedPatterns = listOf(
        "send money",
        "payment",
        "pay",
        "pay now",
        "pay with",
        "buy",
        "checkout",
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
        "transfer money"
    )

    private val dangerousFinalPatterns = listOf(
        "send money",
        "payment",
        "pay now",
        "pay with",
        "bank",
        "banking",
        "upi",
        "password",
        "otp",
        "captcha",
        "login",
        "sign in",
        "delete",
        "erase",
        "remove account",
        "confirm booking",
        "book now",
        "book ride",
        "submit",
        "request ride",
        "request now",
        "final booking",
        "complete payment"
    )

    private val grocerySensitivePatterns = listOf(
        "send money",
        "transfer money",
        "bank",
        "banking",
        "upi",
        "payment",
        "pay",
        "password",
        "otp",
        "one time password",
        "captcha",
        "login",
        "sign in",
        "complete payment",
        "pay now",
        "pay with",
        "card",
        "cvv",
        "pin",
        "delete",
        "erase",
        "remove account"
    )

    private val foodUnsafePatterns = listOf(
        "beer",
        "wine",
        "whiskey",
        "whisky",
        "vodka",
        "rum",
        "gin",
        "scotch",
        "alcohol",
        "liquor",
        "cocktail",
        "tobacco",
        "cigarette",
        "cigarettes",
        "cigar",
        "cannabis",
        "weed",
        "drugs",
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
        "restricted item",
        "restricted items",
        "unsafe item",
        "unsafe items"
    )

    private val foodSensitivePatterns = listOf(
        "send money",
        "transfer money",
        "payment",
        "pay",
        "pay now",
        "pay with",
        "bank",
        "banking",
        "upi",
        "password",
        "otp",
        "one time password",
        "captcha",
        "login",
        "sign in",
        "card",
        "cvv",
        "pin",
        "complete payment",
        "delete",
        "erase",
        "remove account"
    )

    fun evaluate(commandIntent: CommandIntent): SafetyDecision {
        val normalized = commandIntent.normalizedText.lowercase(Locale.US)

        if (commandIntent.actionType == ActionType.STOP_SERVICE) {
            return SafetyDecision.allow("Stop command accepted.")
        }

        if (commandIntent.actionType == ActionType.FOOD_ORDER) {
            if (containsUnsafeFoodKeyword(normalized, commandIntent.entities)) {
                return SafetyDecision.block("Blocked: unsafe food orders must stay manual.")
            }

            if (containsFoodSensitiveKeyword(normalized, commandIntent.entities)) {
                return SafetyDecision.humanOnly(
                    "That food step includes payment, login, OTP, or CAPTCHA details, so it must stay manual."
                )
            }

            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = "Food ordering flow allowed. Final payment, OTP, login, and CAPTCHA steps must stay manual.",
                requiresBiometric = false,
                finalActionAllowed = false
            )
        }

        if (commandIntent.actionType == ActionType.GROCERY_BOOKING) {
            if (containsGrocerySensitiveKeyword(normalized)) {
                return SafetyDecision.block(
                    "Blocked: payments, banking, passwords, OTPs, CAPTCHAs, and card flows must stay manual."
                )
            }

            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = "Grocery booking flow allowed. Final payment, OTP, login, and CAPTCHA steps must stay manual.",
                requiresBiometric = false,
                finalActionAllowed = false
            )
        }

        if (commandIntent.actionType == ActionType.CAB_BOOKING) {
            if (containsBlockedKeyword(normalized)) {
                return SafetyDecision.block(
                    "Blocked: payments, banking, passwords, OTPs, CAPTCHAs, and checkout flows must stay manual."
                )
            }

            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = "Cab booking flow allowed. Final booking, payment, OTP, and CAPTCHA steps must stay manual.",
                requiresBiometric = false,
                finalActionAllowed = false
            )
        }

        if (containsBlockedKeyword(normalized)) {
            return SafetyDecision.block(
                "Blocked: payments, banking, passwords, OTPs, CAPTCHAs, and checkout flows must stay manual."
            )
        }

        if ((commandIntent.actionType == ActionType.CLICK_TEXT ||
                commandIntent.actionType == ActionType.TYPE_TEXT) &&
            containsDangerousFinalKeyword(normalized)
        ) {
            return SafetyDecision.humanOnly(
                "That looks like a final step, so it must stay manual."
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
                    requiresBiometric = false,
                    finalActionAllowed = true
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
                        requiresBiometric = false,
                        finalActionAllowed = true
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
                requiresBiometric = false,
                finalActionAllowed = true
            )
        }
    }

    fun evaluate(brainAction: BrainAction, userConfirmed: Boolean = false): SafetyDecision {
        val normalized = buildNormalizedBrainActionText(brainAction)
        val requestText = buildBrainActionRequestText(brainAction)

        if (isGroceryBrainAction(brainAction)) {
            if (brainAction.actionType == BrainActionType.HUMAN_ONLY ||
                brainAction.riskLevel == BrainRiskLevel.BLOCKED
            ) {
                return SafetyDecision.humanOnly(
                    brainAction.reply.ifBlank {
                        "That needs to stay manual for your safety."
                    }
                )
            }

            if (containsGrocerySensitiveKeyword(requestText)) {
                return SafetyDecision.humanOnly(
                    "That grocery step includes a sensitive action, so it must stay manual."
                )
            }

            if ((brainAction.riskLevel == BrainRiskLevel.CONFIRMATION_REQUIRED ||
                    brainAction.requiresConfirmation ||
                    brainAction.actionType == BrainActionType.PREPARE) &&
                !userConfirmed
            ) {
                return SafetyDecision.requireConfirmation(
                    brainAction.nextQuestion?.takeIf { it.isNotBlank() }
                        ?: brainAction.reply.ifBlank {
                            "Please confirm to continue."
                        }
                )
            }

            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = brainAction.reply.ifBlank { "Grocery flow allowed." },
                requiresBiometric = false,
                finalActionAllowed = brainAction.finalActionAllowed
            )
        }

        if (isFoodBrainAction(brainAction)) {
            if (brainAction.actionType == BrainActionType.HUMAN_ONLY ||
                brainAction.riskLevel == BrainRiskLevel.BLOCKED
            ) {
                return SafetyDecision.humanOnly(
                    brainAction.reply.ifBlank {
                        "That needs to stay manual for your safety."
                    }
                )
            }

            if (containsUnsafeFoodKeyword(requestText, brainAction.params)) {
                return SafetyDecision.humanOnly(
                    "That food order must stay manual."
                )
            }

            if ((brainAction.riskLevel == BrainRiskLevel.CONFIRMATION_REQUIRED ||
                    brainAction.requiresConfirmation ||
                    brainAction.actionType == BrainActionType.PREPARE) &&
                !userConfirmed
            ) {
                return SafetyDecision.requireConfirmation(
                    brainAction.nextQuestion?.takeIf { it.isNotBlank() }
                        ?: brainAction.reply.ifBlank {
                            "Please confirm to continue."
                        }
                )
            }

            return SafetyDecision(
                level = SafetyLevel.SAFE,
                allowed = true,
                message = brainAction.reply.ifBlank { "Food ordering flow allowed." },
                requiresBiometric = false,
                finalActionAllowed = brainAction.finalActionAllowed
            )
        }

        if (brainAction.actionType == BrainActionType.HUMAN_ONLY ||
            brainAction.riskLevel == BrainRiskLevel.BLOCKED ||
            containsBlockedKeyword(normalized)
        ) {
            return SafetyDecision.humanOnly(
                brainAction.reply.ifBlank {
                    "That needs to stay manual for your safety."
                }
            )
        }

        if (brainAction.riskLevel == BrainRiskLevel.CONFIRMATION_REQUIRED ||
            brainAction.requiresConfirmation ||
            brainAction.actionType == BrainActionType.PREPARE
        ) {
            if (!userConfirmed) {
                return SafetyDecision.requireConfirmation(
                    brainAction.nextQuestion?.takeIf { it.isNotBlank() }
                        ?: brainAction.reply.ifBlank {
                            "Please confirm to continue."
                        }
                )
            }
        }

        if (containsDangerousFinalKeyword(normalized) &&
            !brainAction.finalActionAllowed
        ) {
            return SafetyDecision.humanOnly(
                "That final step must stay manual."
            )
        }

        return SafetyDecision(
            level = SafetyLevel.SAFE,
            allowed = true,
            message = brainAction.reply.ifBlank { "Command allowed." },
            requiresBiometric = false,
            requiresConfirmation = false,
            finalActionAllowed = brainAction.finalActionAllowed
        )
    }

    private fun buildNormalizedBrainActionText(brainAction: BrainAction): String {
        val paramsText = brainAction.params.entries.joinToString(separator = " ") { (key, value) ->
            "$key $value"
        }
        return listOf(
            brainAction.intent,
            paramsText
        ).joinToString(separator = " ").lowercase(Locale.US)
    }

    private fun buildBrainActionRequestText(brainAction: BrainAction): String {
        val paramsText = brainAction.params.entries.joinToString(separator = " ") { (key, value) ->
            "$key $value"
        }
        return listOf(
            brainAction.intent,
            paramsText,
            brainAction.nextQuestion.orEmpty()
        ).joinToString(separator = " ").lowercase(Locale.US)
    }

    private fun containsBlockedKeyword(normalized: String): Boolean {
        return blockedPatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(normalized)
        }
    }

    private fun containsGrocerySensitiveKeyword(normalized: String): Boolean {
        return grocerySensitivePatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(normalized)
        }
    }

    private fun containsUnsafeFoodKeyword(normalized: String, params: Map<String, String>): Boolean {
        val merged = buildString {
            append(normalized)
            append(' ')
            append(params["foodItem"].orEmpty())
            append(' ')
            append(params["restaurantName"].orEmpty())
        }.lowercase(Locale.US)

        return foodUnsafePatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(merged)
        }
    }

    private fun containsFoodSensitiveKeyword(normalized: String, params: Map<String, String>): Boolean {
        val merged = buildString {
            append(normalized)
            append(' ')
            append(params["foodItem"].orEmpty())
            append(' ')
            append(params["restaurantName"].orEmpty())
        }.lowercase(Locale.US)

        return foodSensitivePatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(merged)
        }
    }

    private fun isGroceryBrainAction(brainAction: BrainAction): Boolean {
        return brainAction.intent.startsWith("grocery", ignoreCase = true)
    }

    private fun isFoodBrainAction(brainAction: BrainAction): Boolean {
        return brainAction.intent.startsWith("food", ignoreCase = true)
    }

    private fun containsDangerousFinalKeyword(normalized: String): Boolean {
        return dangerousFinalPatterns.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(normalized)
        }
    }
}
