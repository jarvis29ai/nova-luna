package com.nova.luna.model

data class InternetPermissionDecision(
    val category: InternetPermissionCategory,
    val reason: String,
    val requiresUserPrompt: Boolean = false
)
