package com.nova.luna.screen

data class ScreenStepResult(
    val success: Boolean,
    val stepExecuted: ScreenStep,
    val reason: String,
    val requiresHuman: Boolean = false,
    val error: String? = null
)
