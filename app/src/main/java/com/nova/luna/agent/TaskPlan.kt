package com.nova.luna.agent

import com.nova.luna.memory.BrainSessionType

data class TaskPlan(
    val goal: String,
    val loopCapable: Boolean,
    val reason: String,
    val domain: BrainSessionType? = null,
    val requiresScreenContext: Boolean = false,
    val requiresUserConfirmation: Boolean = false,
    val allowLoop: Boolean = true,
    val maxSteps: Int = 6,
    val maxRetries: Int = 1,
    val completionHints: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList()
)
