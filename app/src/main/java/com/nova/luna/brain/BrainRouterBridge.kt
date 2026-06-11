package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole

interface BrainRouterBridge {
    fun isReady(role: BrainModelRole): Boolean = false

    fun selectLocalRoute(
        request: BrainRequest,
        allowOnlineHelper: Boolean = true
    ): com.nova.luna.model.BrainRouteDecision?

    fun recordModelOutcome(
        role: BrainModelRole,
        available: Boolean,
        reason: String? = null
    ) {
    }
}

object NoOpBrainRouterBridge : BrainRouterBridge {
    override fun selectLocalRoute(
        request: BrainRequest,
        allowOnlineHelper: Boolean
    ): com.nova.luna.model.BrainRouteDecision? {
        return null
    }
}
