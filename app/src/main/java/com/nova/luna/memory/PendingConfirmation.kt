package com.nova.luna.memory

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainRiskLevel

data class PendingConfirmation(
    val confirmationId: String,
    val type: PendingConfirmationType,
    val sessionId: String? = null,
    val sessionType: BrainSessionType,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val userFacingSummary: String,
    val actionSummary: String,
    val riskLevel: BrainRiskLevel,
    val requiresBiometric: Boolean = false,
    val requiresManualHandoff: Boolean = false,
    val brainAction: BrainAction? = null,
    val sanitizedMetadata: Map<String, String> = emptyMap(),
    val confirmationPhraseExpected: String = "yes",
    val denialPhraseExpected: String = "no",
    val consumedAtMillis: Long? = null,
    val consumed: Boolean = false
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis >= expiresAtMillis
    }

    fun isPending(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return !consumed && !isExpired(nowMillis)
    }

    fun isConfirmationPhrase(text: String): Boolean {
        return normalize(text) == normalize(confirmationPhraseExpected)
    }

    fun isDenialPhrase(text: String): Boolean {
        return normalize(text) == normalize(denialPhraseExpected)
    }

    fun matchesBrainAction(candidate: BrainAction): Boolean {
        val stored = brainAction ?: return false
        return stored.intent.equals(candidate.intent, ignoreCase = true) &&
            stored.reply == candidate.reply &&
            stored.actionType == candidate.actionType &&
            stored.riskLevel == candidate.riskLevel &&
            stored.requiresConfirmation == candidate.requiresConfirmation &&
            stored.finalActionAllowed == candidate.finalActionAllowed &&
            stored.params == candidate.params &&
            stored.nextQuestion == candidate.nextQuestion
    }

    fun consume(atMillis: Long = System.currentTimeMillis()): PendingConfirmation {
        return copy(consumed = true, consumedAtMillis = atMillis)
    }

    private fun normalize(value: String): String {
        return value.lowercase().trim()
    }
}
