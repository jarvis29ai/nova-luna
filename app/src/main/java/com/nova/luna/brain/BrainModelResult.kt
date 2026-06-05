package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelRole

data class BrainModelResult(
    val role: BrainModelRole,
    val available: Boolean,
    val candidateAction: BrainAction? = null,
    val rawResponse: String? = null,
    val reason: String,
    val safetyNotes: List<String> = emptyList()
) {
    val hasCandidate: Boolean
        get() = available && candidateAction != null

    companion object {
        fun unavailable(
            role: BrainModelRole,
            reason: String,
            safetyNotes: List<String> = emptyList()
        ): BrainModelResult {
            return BrainModelResult(
                role = role,
                available = false,
                candidateAction = null,
                rawResponse = null,
                reason = reason,
                safetyNotes = safetyNotes
            )
        }

        fun available(
            role: BrainModelRole,
            candidateAction: BrainAction,
            rawResponse: String? = null,
            reason: String = "Phone model candidate ready.",
            safetyNotes: List<String> = emptyList()
        ): BrainModelResult {
            return BrainModelResult(
                role = role,
                available = true,
                candidateAction = candidateAction,
                rawResponse = rawResponse,
                reason = reason,
                safetyNotes = safetyNotes
            )
        }
    }
}
