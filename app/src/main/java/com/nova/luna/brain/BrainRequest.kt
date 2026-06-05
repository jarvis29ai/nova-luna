package com.nova.luna.brain

data class BrainRequest(
    val rawText: String,
    val activeCabSession: Boolean = false,
    val activeGrocerySession: Boolean = false
)
