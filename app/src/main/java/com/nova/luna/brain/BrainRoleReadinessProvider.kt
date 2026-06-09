package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole

fun interface BrainRoleReadinessProvider {
    fun isReady(role: BrainModelRole): Boolean
}

object NoOpBrainRoleReadinessProvider : BrainRoleReadinessProvider {
    override fun isReady(role: BrainModelRole): Boolean = false
}
