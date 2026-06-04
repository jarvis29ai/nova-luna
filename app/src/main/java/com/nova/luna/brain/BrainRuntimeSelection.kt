package com.nova.luna.brain

import com.nova.luna.model.BrainRuntimeStatus

data class BrainRuntimeSelection(
    val provider: BrainProvider,
    val runtimeStatus: BrainRuntimeStatus
)
