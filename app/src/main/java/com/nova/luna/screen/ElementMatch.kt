package com.nova.luna.screen

data class ElementMatch(
    val element: ScreenElement?,
    val confidence: Float,
    val reason: String,
    val alternatives: List<ScreenElement> = emptyList(),
    val failureReason: String? = null
)
