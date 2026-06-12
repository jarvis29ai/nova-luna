package com.nova.luna.model

enum class SafetyStatus {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    BLOCKED
}

enum class SafetyCategory {
    SAFE_LOW_RISK,
    MEDIUM_RISK_CONFIRMATION_REQUIRED,
    PAYMENT_OR_FINANCIAL,
    OTP_OR_VERIFICATION_CODE,
    LOGIN_OR_AUTHENTICATION,
    CAPTCHA_OR_BOT_CHECK,
    DESTRUCTIVE_ACTION,
    PRIVACY_SENSITIVE,
    BOOKING_OR_ORDER_FINALIZATION,
    MESSAGE_OR_CALL_SENSITIVE,
    UNKNOWN_OR_UNSUPPORTED
}

data class SafetyDecision(
    val status: SafetyStatus,
    val reason: String,
    val category: SafetyCategory,
    val requiresUserConfirmation: Boolean = false,
    val blockedTerms: List<String> = emptyList(),
    val allowedActionType: String? = null,
    val originalActionType: String? = null,
    
    // Legacy fields for backward compatibility or internal tracking
    val requiresBiometric: Boolean = false,
    val humanRequired: Boolean = false,
    val message: String = reason,
    val requiresConfirmation: Boolean = requiresUserConfirmation,
    val finalActionAllowed: Boolean = status == SafetyStatus.ALLOWED,
    val allowed: Boolean = status == SafetyStatus.ALLOWED,
    val level: SafetyLevel = when (status) {
        SafetyStatus.ALLOWED -> SafetyLevel.SAFE
        SafetyStatus.CONFIRMATION_REQUIRED -> SafetyLevel.CONFIRMATION_REQUIRED
        SafetyStatus.BLOCKED -> if (humanRequired) SafetyLevel.HUMAN_ONLY else SafetyLevel.BLOCKED
    }
) {
    companion object {
        fun allow(
            reason: String = "Allowed", 
            category: SafetyCategory = SafetyCategory.SAFE_LOW_RISK,
            allowedActionType: String? = null,
            originalActionType: String? = null
        ) = SafetyDecision(
            status = SafetyStatus.ALLOWED,
            category = category,
            reason = reason,
            requiresUserConfirmation = false,
            allowedActionType = allowedActionType,
            originalActionType = originalActionType
        )

        fun requireConfirmation(
            reason: String,
            category: SafetyCategory = SafetyCategory.MEDIUM_RISK_CONFIRMATION_REQUIRED,
            originalActionType: String? = null
        ) = SafetyDecision(
            status = SafetyStatus.CONFIRMATION_REQUIRED,
            category = category,
            reason = reason,
            requiresUserConfirmation = true,
            originalActionType = originalActionType
        )

        fun block(
            reason: String,
            category: SafetyCategory = SafetyCategory.UNKNOWN_OR_UNSUPPORTED,
            blockedTerms: List<String> = emptyList(),
            originalActionType: String? = null,
            humanRequired: Boolean = false,
            requiresBiometric: Boolean = false
        ) = SafetyDecision(
            status = SafetyStatus.BLOCKED,
            category = category,
            reason = reason,
            blockedTerms = blockedTerms,
            originalActionType = originalActionType,
            humanRequired = humanRequired,
            requiresBiometric = requiresBiometric
        )
        
        fun humanOnly(
            reason: String,
            category: SafetyCategory = SafetyCategory.UNKNOWN_OR_UNSUPPORTED,
            blockedTerms: List<String> = emptyList(),
            originalActionType: String? = null
        ) = block(
            reason = reason,
            category = category,
            blockedTerms = blockedTerms,
            originalActionType = originalActionType,
            humanRequired = true
        )
    }
}
