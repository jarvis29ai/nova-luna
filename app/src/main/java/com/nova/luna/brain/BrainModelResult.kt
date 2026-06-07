package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelRole

data class BrainModelResult(
    val role: BrainModelRole,
    val available: Boolean,
    val candidateAction: BrainAction? = null,
    val rawResponse: String? = null,
    val reason: String,
    val safetyNotes: List<String> = emptyList(),
    val localModelId: PhoneLocalLlmModelId? = null,
    val localModelDisplayName: String? = null,
    val localModelStatus: PhoneLocalLlmStatus? = null,
    val promptBuilt: Boolean = false,
    val jsonParsed: Boolean = false,
    val latencyMillis: Long? = null
) {
    val hasCandidate: Boolean
        get() = available && candidateAction != null

    companion object {
        fun unavailable(
            role: BrainModelRole,
            reason: String,
            safetyNotes: List<String> = emptyList(),
            candidateAction: BrainAction? = null,
            localModelId: PhoneLocalLlmModelId? = null,
            localModelDisplayName: String? = null,
            localModelStatus: PhoneLocalLlmStatus? = null,
            promptBuilt: Boolean = false,
            jsonParsed: Boolean = false,
            latencyMillis: Long? = null,
            rawResponse: String? = null
        ): BrainModelResult {
            return BrainModelResult(
                role = role,
                available = false,
                candidateAction = candidateAction,
                rawResponse = rawResponse,
                reason = reason,
                safetyNotes = safetyNotes,
                localModelId = localModelId,
                localModelDisplayName = localModelDisplayName,
                localModelStatus = localModelStatus,
                promptBuilt = promptBuilt,
                jsonParsed = jsonParsed,
                latencyMillis = latencyMillis
            )
        }

        fun available(
            role: BrainModelRole,
            candidateAction: BrainAction,
            rawResponse: String? = null,
            reason: String = "Phone model candidate ready.",
            safetyNotes: List<String> = emptyList(),
            localModelId: PhoneLocalLlmModelId? = null,
            localModelDisplayName: String? = null,
            localModelStatus: PhoneLocalLlmStatus? = null,
            promptBuilt: Boolean = false,
            jsonParsed: Boolean = false,
            latencyMillis: Long? = null
        ): BrainModelResult {
            return BrainModelResult(
                role = role,
                available = true,
                candidateAction = candidateAction,
                rawResponse = rawResponse,
                reason = reason,
                safetyNotes = safetyNotes,
                localModelId = localModelId,
                localModelDisplayName = localModelDisplayName,
                localModelStatus = localModelStatus,
                promptBuilt = promptBuilt,
                jsonParsed = jsonParsed,
                latencyMillis = latencyMillis
            )
        }
    }
}
