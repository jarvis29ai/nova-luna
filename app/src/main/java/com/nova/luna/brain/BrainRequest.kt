package com.nova.luna.brain

import com.nova.luna.screen.ScreenState

data class BrainRequest(
    val rawText: String,
    val activeCabSession: Boolean = false,
    val activeGrocerySession: Boolean = false,
    val activeFoodSession: Boolean = false,
    val screenState: ScreenState? = null
)
