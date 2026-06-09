package com.nova.luna.model

data class BrainRouteDecision(
    val selectedRole: BrainModelRole,
    val reason: String,
    val requiresInternet: Boolean,
    val requiresScreenContext: Boolean,
    val fallbackAllowed: Boolean,
    val safetyNotes: List<String> = emptyList()
)
