package com.nova.luna.brain

interface BrainRouterBridge {
    fun selectLocalRoute(
        request: BrainRequest,
        allowOnlineHelper: Boolean = true
    ): com.nova.luna.model.BrainRouteDecision?
}

object NoOpBrainRouterBridge : BrainRouterBridge {
    override fun selectLocalRoute(
        request: BrainRequest,
        allowOnlineHelper: Boolean
    ): com.nova.luna.model.BrainRouteDecision? {
        return null
    }
}
