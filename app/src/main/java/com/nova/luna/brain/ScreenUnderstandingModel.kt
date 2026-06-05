package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class ScreenUnderstandingModel : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.SCREEN_UNDERSTANDING
    override val available: Boolean = false

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        return BrainModelResult.unavailable(
            role = role,
            reason = "Screen understanding is a future read-only model and is not wired yet.",
            safetyNotes = routeDecision.safetyNotes + listOf(
                "Screen understanding stays read-only.",
                "It must never execute phone actions directly."
            )
        )
    }
}
