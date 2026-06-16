package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import java.util.Locale

class BrainActionValidator {
    private val dangerousFinalPatterns = listOf(
        "send money",
        "transfer money",
        "money transfer",
        "make payment",
        "confirm payment",
        "pay now",
        "pay with",
        "payment",
        "checkout",
        "otp",
        "one time password",
        "pin",
        "upi pin",
        "card pin",
        "cvv",
        "cvc",
        "captcha",
        "login",
        "sign in",
        "login bypass",
        "password",
        "passcode",
        "biometric",
        "fingerprint",
        "face unlock",
        "purchase confirmation",
        "order confirmation",
        "place order",
        "confirm order",
        "send message",
        "send email",
        "post",
        "share",
        "follow",
        "subscribe",
        "comment",
        "book it without asking me",
        "without asking me",
        "without asking",
        "final booking",
        "confirm booking",
        "book ride",
        "book now",
        "request ride",
        "request now",
        "complete payment",
        "delete account",
        "delete",
        "erase",
        "remove account"
    )

    private val directExecutionHints = listOf(
        "actionexecutor",
        "direct execution",
        "directly execute",
        "execute immediately",
        "bypass safetygate",
        "bypass validator",
        "call executor",
        "run actionexecutor"
    )

    fun isAcceptable(action: BrainAction): Boolean {
        val effectiveReply = action.reply.ifBlank { action.assistantReply }
        if (action.intent.isBlank() || effectiveReply.isBlank()) {
            return false
        }

        if (action.schemaVersion != 1) {
            return false
        }

        if (!action.confidence.isFinite() || action.confidence !in 0.0..1.0) {
            return false
        }

        if (containsDirectExecutionHint(action)) {
            return false
        }

        if (action.actionType == BrainActionType.ASK_CLARIFICATION) {
            return true
        }

        if (action.actionType == BrainActionType.UNKNOWN || action.riskLevel == BrainRiskLevel.UNKNOWN) {
            return false
        }

        // Phase 25/Fix: Planning/Drafting is always acceptable as it stops before execution
        if (!action.finalActionAllowed) {
            return true
        }

        if (action.actionType == BrainActionType.HUMAN_ONLY || action.riskLevel == BrainRiskLevel.BLOCKED) {
            return false
        }

        if (isDangerousFinalAction(action)) {
            return false
        }

        return true
    }

    private fun isDangerousFinalAction(action: BrainAction): Boolean {
        if (action.intent.equals("cab_booking", ignoreCase = true)) {
            return true
        }

        if (action.intent.startsWith("shopping", ignoreCase = true)) {
            return true
        }

        if (action.intent.startsWith("grocery", ignoreCase = true)) {
            return true
        }

        if (action.intent.startsWith("food", ignoreCase = true)) {
            return true
        }

        val text = buildString {
            append(action.intent)
            append(' ')
            append(action.reply)
            append(' ')
            append(action.assistantReply)
            append(' ')
            append(action.nextQuestion.orEmpty())
            action.params.forEach { (key, value) ->
                append(' ')
                append(key)
                append(' ')
                append(value)
            }
        }.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return dangerousFinalPatterns.any { pattern ->
            Regex("""\b${Regex.escape(pattern)}\b""").containsMatchIn(text)
        }
    }

    private fun containsDirectExecutionHint(action: BrainAction): Boolean {
        val text = buildString {
            append(action.intent)
            append(' ')
            append(action.reply)
            append(' ')
            append(action.nextQuestion.orEmpty())
            action.params.forEach { (key, value) ->
                append(' ')
                append(key)
                append(' ')
                append(value)
            }
        }.lowercase(Locale.US)

        return directExecutionHints.any { text.contains(it) }
    }
}
