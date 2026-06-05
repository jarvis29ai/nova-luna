package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class GemmaBrainModel(
    private val runtime: PhoneGemmaRuntime = PhoneGemmaRuntime(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.GEMMA_REASONING
    override val available: Boolean
        get() = runtime.isReady()

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        val readiness = runtime.readinessStatus(selectedBrainRole = role)
        if (!readiness.modelLoaded) {
            return BrainModelResult.unavailable(
                role = role,
                reason = readiness.reason,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Gemma is intended to be the final on-device reasoning model.",
                    "Fallback to LocalMockBrainProvider remains available."
                )
            )
        }

        val rawResponse = runtime.generate(request, routeDecision)
            ?: return BrainModelResult.unavailable(
                role = role,
                reason = "Gemma runtime did not return a response.",
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Gemma is intended to be the final on-device reasoning model.",
                    "Fallback to LocalMockBrainProvider remains available."
                )
            )
        val candidateAction = codec.decode(rawResponse)

        if (candidateAction == null) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "Gemma runtime returned output that was not strict BrainAction JSON.",
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Gemma is intended to be the final on-device reasoning model.",
                    "Any action it suggests must still pass BrainActionValidator.",
                    "Fallback to LocalMockBrainProvider remains available."
                )
            )
        }

        return BrainModelResult.available(
            role = role,
            candidateAction = candidateAction,
            rawResponse = rawResponse,
            reason = "Gemma phone runtime produced a structured candidate.",
            safetyNotes = routeDecision.safetyNotes + listOf(
                "Gemma is intended to be the final on-device reasoning model.",
                "Any action it suggests must still pass BrainActionValidator."
            )
        )
    }
}
