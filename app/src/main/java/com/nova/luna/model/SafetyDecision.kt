package com.nova.luna.model

data class SafetyDecision(
    val level: SafetyLevel,
    val allowed: Boolean,
    val message: String,
    val requiresBiometric: Boolean = false
) {
    companion object {
        fun allow(message: String = "Allowed") = SafetyDecision(
            level = SafetyLevel.SAFE,
            allowed = true,
            message = message,
            requiresBiometric = false
        )

        fun requireBiometric(message: String) = SafetyDecision(
            level = SafetyLevel.SENSITIVE,
            allowed = false,
            message = message,
            requiresBiometric = true
        )

        fun block(message: String) = SafetyDecision(
            level = SafetyLevel.BLOCKED,
            allowed = false,
            message = message,
            requiresBiometric = false
        )
    }
}

