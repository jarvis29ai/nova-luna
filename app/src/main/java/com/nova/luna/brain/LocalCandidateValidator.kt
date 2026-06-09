package com.nova.luna.brain

import com.nova.luna.model.BrainAction

data class LocalCandidateValidationResult(
    val accepted: Boolean,
    val candidateAction: BrainAction,
    val reason: String
)

class LocalCandidateValidator(
    private val validator: BrainActionValidator = BrainActionValidator()
) {
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

    fun validate(
        candidateAction: BrainAction,
        rawJson: String? = null
    ): LocalCandidateValidationResult {
        if (containsDirectExecutionHint(candidateAction, rawJson)) {
            return rejected(candidateAction, "Local candidate attempted direct execution.")
        }

        if (!validator.isAcceptable(candidateAction)) {
            return rejected(candidateAction, "BrainActionValidator rejected the local candidate.")
        }

        return LocalCandidateValidationResult(
            accepted = true,
            candidateAction = candidateAction,
            reason = "Local candidate passed validation."
        )
    }

    private fun containsDirectExecutionHint(
        candidateAction: BrainAction,
        rawJson: String?
    ): Boolean {
        val text = buildString {
            append(rawJson.orEmpty())
            append(' ')
            append(candidateAction.intent)
            append(' ')
            append(candidateAction.reply)
            append(' ')
            append(candidateAction.nextQuestion.orEmpty())
            candidateAction.params.forEach { (key, value) ->
                append(' ')
                append(key)
                append(' ')
                append(value)
            }
        }.lowercase()

        return directExecutionHints.any { hint -> text.contains(hint) }
    }

    private fun rejected(candidateAction: BrainAction, reason: String): LocalCandidateValidationResult {
        return LocalCandidateValidationResult(
            accepted = false,
            candidateAction = candidateAction,
            reason = "Local candidate rejected: $reason"
        )
    }
}
