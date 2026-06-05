package com.nova.luna.model

data class SafetyDecision(
    val level: SafetyLevel,
    val allowed: Boolean,
    val message: String,
    val requiresBiometric: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val finalActionAllowed: Boolean = true,
    val humanRequired: Boolean = false
) {
    companion object {
        fun allow(message: String = "Allowed") = SafetyDecision(
            level = SafetyLevel.SAFE,
            allowed = true,
            message = message,
            requiresBiometric = false,
            requiresConfirmation = false,
            finalActionAllowed = true,
            humanRequired = false
        )

        fun requireBiometric(message: String) = SafetyDecision(
            level = SafetyLevel.SENSITIVE,
            allowed = false,
            message = message,
            requiresBiometric = true,
            requiresConfirmation = false,
            finalActionAllowed = false,
            humanRequired = false
        )

        fun requireConfirmation(message: String) = SafetyDecision(
            level = SafetyLevel.CONFIRMATION_REQUIRED,
            allowed = false,
            message = message,
            requiresBiometric = false,
            requiresConfirmation = true,
            finalActionAllowed = false,
            humanRequired = false
        )

        fun humanOnly(message: String) = SafetyDecision(
            level = SafetyLevel.HUMAN_ONLY,
            allowed = false,
            message = message,
            requiresBiometric = false,
            requiresConfirmation = false,
            finalActionAllowed = false,
            humanRequired = true
        )

        fun block(message: String) = SafetyDecision(
            level = SafetyLevel.BLOCKED,
            allowed = false,
            message = message,
            requiresBiometric = false,
            requiresConfirmation = false,
            finalActionAllowed = false,
            humanRequired = false
        )
    }
}
