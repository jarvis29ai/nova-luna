package com.nova.luna.confirmation

import com.nova.luna.model.BrainAction
import com.nova.luna.model.SafetyDecision

data class ConfirmationRequest(
    val confirmationId: String,
    val action: BrainAction,
    val safetyDecision: SafetyDecision,
    val title: String,
    val summary: String,
    val details: Map<String, String>,
    val createdAt: Long = System.currentTimeMillis()
)
