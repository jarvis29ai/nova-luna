package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class GemmaBrainModel(
    private val runtime: PhoneGemmaRuntime = PhoneGemmaRuntime(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.GEMMA_REASONING
    override val available: Boolean
        get() = runtime.localReadinessStatus().available

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        return runtime.generateBrainAction(request, routeDecision)
    }
}
