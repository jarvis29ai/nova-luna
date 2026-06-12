package com.nova.luna.screen

data class ScreenUnderstandingResult(
    val success: Boolean,
    val snapshot: ScreenSnapshot?,
    val detectedScreenType: ScreenType,
    val candidateActions: List<String> = emptyList(),
    val safeNextStep: ScreenStep?,
    val failureReason: String?,
    val humanReadableSummary: String,
    val confidence: Float
)
