package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

interface PhoneBrainModel {
    val role: BrainModelRole
    val available: Boolean

    fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult
}
