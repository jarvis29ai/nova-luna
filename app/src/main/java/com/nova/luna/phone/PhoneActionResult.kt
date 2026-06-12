package com.nova.luna.phone

import com.nova.luna.model.SafetyDecision

data class PhoneActionResult(
    val actionName: String,
    val attempted: Boolean,
    val success: Boolean,
    val reason: String,
    val packageName: String? = null,
    val label: String? = null,
    val safetyDecision: SafetyDecision? = null,
    val requiresUserAction: Boolean = false,
    val errorCode: String? = null
)
