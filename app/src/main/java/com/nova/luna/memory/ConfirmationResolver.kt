package com.nova.luna.memory

data class ConfirmationResolution(
    val pendingConfirmation: PendingConfirmation,
    val outcome: ConfirmationOutcome,
    val normalizedText: String,
    val resolvedAtMillis: Long = System.currentTimeMillis()
) {
    val confirmed: Boolean
        get() = outcome == ConfirmationOutcome.CONFIRMED

    val denied: Boolean
        get() = outcome == ConfirmationOutcome.DENIED

    val expired: Boolean
        get() = outcome == ConfirmationOutcome.EXPIRED
}

enum class ConfirmationOutcome {
    CONFIRMED,
    DENIED,
    EXPIRED,
    NO_MATCH
}

class ConfirmationResolver(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun resolve(rawText: String, pendingConfirmation: PendingConfirmation?): ConfirmationResolution? {
        val pending = pendingConfirmation ?: return null
        val normalized = normalize(rawText)

        if (pending.isExpired(clock())) {
            return ConfirmationResolution(
                pendingConfirmation = pending,
                outcome = ConfirmationOutcome.EXPIRED,
                normalizedText = normalized
            )
        }

        val outcome = when {
            pending.isConfirmationPhrase(normalized) || isAffirmative(normalized) -> ConfirmationOutcome.CONFIRMED
            pending.isDenialPhrase(normalized) || isNegative(normalized) -> ConfirmationOutcome.DENIED
            else -> ConfirmationOutcome.NO_MATCH
        }

        return if (outcome == ConfirmationOutcome.NO_MATCH) {
            null
        } else {
            ConfirmationResolution(
                pendingConfirmation = pending,
                outcome = outcome,
                normalizedText = normalized
            )
        }
    }

    private fun isAffirmative(value: String): Boolean {
        return value in affirmativePhrases
    }

    private fun isNegative(value: String): Boolean {
        return value in negativePhrases
    }

    private fun normalize(value: String): String {
        return value.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    private companion object {
        val affirmativePhrases = setOf(
            "yes",
            "yeah",
            "yep",
            "sure",
            "ok",
            "okay",
            "proceed",
            "yes please",
            "sure please",
            "please do it"
        )

        val negativePhrases = setOf(
            "no",
            "nope",
            "not now",
            "not yet",
            "cancel",
            "stop"
        )
    }
}
