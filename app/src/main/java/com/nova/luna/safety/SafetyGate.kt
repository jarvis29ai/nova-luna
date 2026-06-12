package com.nova.luna.safety

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.SafetyCategory
import com.nova.luna.model.SafetyStatus
import com.nova.luna.memory.PendingConfirmation
import java.util.Locale

class SafetyGate {

    // HARD BLOCKS: PAYMENT / FINANCIAL
    private val paymentTerms = listOf(
        "pay now", "send money", "upi", "enter upi pin", "confirm payment", 
        "buy now", "purchase", "checkout payment", "bank transfer", 
        "wallet top up", "card payment", "net banking", "investment",
        "trade real money", "crypto buy", "crypto sell", "rupay", "checkout payment",
        "complete payment"
    )

    // HARD BLOCKS: OTP / VERIFICATION
    private val otpTerms = listOf(
        "read otp", "enter otp", "share otp", "auto-fill otp",
        "verification code", "sms code", "email code", "one-time password",
        "2fa code", "mfa code", "otp"
    )

    // HARD BLOCKS: LOGIN / AUTH
    private val loginTerms = listOf(
        "login", "sign in", "enter password", "type password",
        "reset password", "change password", "biometric unlock",
        "unlock app", "enter pin", "enter passcode", "authenticate", "password",
        "bypass login"
    )

    // HARD BLOCKS: CAPTCHA / BOT CHECK
    private val captchaTerms = listOf(
        "solve captcha", "click captcha", "bypass captcha",
        "i am not robot", "bot check", "human verification", "captcha"
    )

    // HARD BLOCKS: DESTRUCTIVE / IRREVERSIBLE / UNSAFE
    private val destructiveTerms = listOf(
        "delete account", "delete files", "factory reset", "erase phone",
        "wipe data", "uninstall app", "remove account", "clear all data",
        "send resignation", "block contact", "report user",
        "cancel subscription without confirmation", "permanently delete",
        "format storage", "delete", "erase", "place final order",
        "beer", "alcohol", "wine", "liquor", "spirits"
    )

    // HARD BLOCKS: PRIVACY-SENSITIVE
    private val privacySensitiveTerms = listOf(
        "open private photos", "read private messages", "send personal data",
        "share location", "expose contacts", "export chats", "send documents",
        "access hidden files", "access banking app", "access password manager",
        "aadhaar", "pan", "personal data"
    )

    // CONFIRMATION REQUIRED: BOOKINGS, COMMUNICATIONS, ORDERS
    private val confirmationTerms = listOf(
        "book cab", "confirm ride", "place food order", "place grocery order",
        "place shopping order", "reserve hotel", "book ticket", "submit form",
        "final submit", "post publicly", "send email", "send message",
        "make phone call", "cancel order", "cancel booking", "accept terms",
        "schedule appointment", "send location", "share contact", "call mom", "call dad",
        "book", "order", "share live location", "buy", "checkout"
    )

    fun evaluate(
        action: BrainAction,
        originalUserText: String? = null,
        pendingConfirmation: PendingConfirmation? = null,
        userConfirmed: Boolean = false
    ): SafetyDecision {
        val originalActionType = action.actionType.name
        val rawCommandLower = (originalUserText ?: action.rawCommand).lowercase(Locale.US)
        val normalizedLower = action.normalizedCommand.lowercase(Locale.US)
        
        // 1. Combine all text sources for keyword analysis
        val searchableText = buildString {
            append(rawCommandLower).append(" ")
            append(normalizedLower).append(" ")
            action.params.values.forEach { append(it.lowercase(Locale.US)).append(" ") }
        }

        // 2. HARD BLOCKS: Keywords match or explicit sensitive ActionType
        val blockedPayment = containsAny(searchableText, paymentTerms) || action.actionType == BrainActionType.PAYMENT_REQUEST
        if (blockedPayment) {
            return SafetyDecision.block(
                reason = "Payment and financial actions are strictly blocked for your safety. Please handle this manually.",
                category = SafetyCategory.PAYMENT_OR_FINANCIAL,
                originalActionType = originalActionType,
                blockedTerms = findMatchingTerms(searchableText, paymentTerms)
            )
        }

        val blockedOtp = containsAny(searchableText, otpTerms) || action.actionType == BrainActionType.OTP_REQUEST
        if (blockedOtp) {
            return SafetyDecision.block(
                reason = "OTP and verification codes are blocked for your safety. Please handle this manually.",
                category = SafetyCategory.OTP_OR_VERIFICATION_CODE,
                originalActionType = originalActionType,
                blockedTerms = findMatchingTerms(searchableText, otpTerms)
            )
        }

        val blockedLogin = containsAny(searchableText, loginTerms) || action.actionType == BrainActionType.LOGIN_REQUEST
        if (blockedLogin) {
            return SafetyDecision.block(
                reason = "Login and authentication actions are blocked for your safety. Please handle this manually.",
                category = SafetyCategory.LOGIN_OR_AUTHENTICATION,
                originalActionType = originalActionType,
                blockedTerms = findMatchingTerms(searchableText, loginTerms)
            )
        }

        val blockedCaptcha = containsAny(searchableText, captchaTerms) || action.actionType == BrainActionType.CAPTCHA_REQUEST
        if (blockedCaptcha) {
            return SafetyDecision.block(
                reason = "CAPTCHA or bot checks cannot be automated. Please handle this manually.",
                category = SafetyCategory.CAPTCHA_OR_BOT_CHECK,
                originalActionType = originalActionType,
                blockedTerms = findMatchingTerms(searchableText, captchaTerms)
            )
        }

        val blockedDestructive = containsAny(searchableText, destructiveTerms) || action.actionType == BrainActionType.DESTRUCTIVE_REQUEST
        if (blockedDestructive) {
            val reason = if (containsAny(searchableText, listOf("beer", "alcohol", "wine", "liquor", "spirits"))) {
                 "Blocked due to unsafe food orders (alcohol/restricted items)."
            } else {
                 "Destructive or irreversible actions are blocked for your safety. Please handle this manually."
            }
            return SafetyDecision.block(
                reason = reason,
                category = SafetyCategory.DESTRUCTIVE_ACTION,
                originalActionType = originalActionType,
                blockedTerms = findMatchingTerms(searchableText, destructiveTerms)
            )
        }

        val blockedPrivacy = containsAny(searchableText, privacySensitiveTerms) || action.actionType == BrainActionType.PRIVACY_SENSITIVE_REQUEST
        if (blockedPrivacy) {
            return SafetyDecision.block(
                reason = "Privacy-sensitive actions are blocked for your safety. Please handle this manually.",
                category = SafetyCategory.PRIVACY_SENSITIVE,
                originalActionType = originalActionType,
                blockedTerms = findMatchingTerms(searchableText, privacySensitiveTerms)
            )
        }

        // 3. HARD BLOCKS: Model explicitly flagged it as HUMAN_ONLY or HIGH risk
        if (action.riskLevel == BrainRiskLevel.HUMAN_ONLY || action.riskLevel == BrainRiskLevel.HIGH || action.actionType == BrainActionType.HUMAN_ONLY || action.riskLevel == BrainRiskLevel.BLOCKED) {
            return SafetyDecision.humanOnly(
                reason = "This action is flagged as high-risk or human-only. Please handle this manually.",
                category = SafetyCategory.UNKNOWN_OR_UNSUPPORTED,
                originalActionType = originalActionType
            )
        }

        // 4. PREVIOUSLY CONFIRMED (Phase 30 will handle this more robustly)
        if (pendingConfirmation != null && userConfirmed) {
            val matches = pendingConfirmation.actionSummary == action.reply
            
            if (matches) {
                return SafetyDecision.allow(
                    reason = "Action confirmed by user.",
                    category = mapConfirmationCategory(action, searchableText),
                    allowedActionType = action.actionType.name,
                    originalActionType = originalActionType
                )
            } else {
                return SafetyDecision.requireConfirmation(
                    reason = "The pending action no longer matches the current request. Please confirm again.",
                    category = mapConfirmationCategory(action, searchableText),
                    originalActionType = originalActionType
                )
            }
        }

        // 5. CONFIRMATION REQUIRED: Keywords or Medium Risk or Specific Action Types
        val isDraft = rawCommandLower.contains("draft") || normalizedLower.contains("draft")
        val isCompare = rawCommandLower.contains("compare") || normalizedLower.contains("compare") || 
                        rawCommandLower.contains("search") || rawCommandLower.contains("show")
        val isPlanning = rawCommandLower.contains("stop before payment") || 
                         rawCommandLower.contains("stop before finalize") ||
                         rawCommandLower.contains("stop at final confirmation") ||
                         !action.finalActionAllowed
        
        // Low-risk exceptions that would otherwise be confirmation-required or blocked
        if (isDraft || isCompare || isPlanning) {
             // If it's just a draft, comparison, or planning, allow it as safe low risk
             if (action.actionType in setOf(BrainActionType.MAKE_CALL_DRAFT, BrainActionType.SEND_MESSAGE_DRAFT, BrainActionType.CAB_SEARCH, BrainActionType.FOOD_SEARCH, BrainActionType.GROCERY_SEARCH, BrainActionType.OPEN_APP, BrainActionType.EXTERNAL_ACTION, BrainActionType.PREPARE)) {
                 return SafetyDecision.allow(
                     reason = "Drafting, searching, and planning are safe low-risk actions.",
                     category = SafetyCategory.SAFE_LOW_RISK,
                     allowedActionType = action.actionType.name,
                     originalActionType = originalActionType
                 )
             }
        }

        val requiresConfirmationByActionType = action.actionType in setOf(
            BrainActionType.MAKE_CALL_DRAFT,
            BrainActionType.SEND_MESSAGE_DRAFT,
            BrainActionType.CAB_SEARCH,
            BrainActionType.FOOD_SEARCH,
            BrainActionType.GROCERY_SEARCH,
            BrainActionType.BOOKING_REQUEST,
            BrainActionType.CREATE_CONTENT
        )

        val requiresConfirmationByKeyword = containsAny(searchableText, confirmationTerms)
        val requiresConfirmationByModel = action.requiresConfirmation || 
                                          action.riskLevel == BrainRiskLevel.MEDIUM ||
                                          action.riskLevel == BrainRiskLevel.CONFIRMATION_REQUIRED

        if (requiresConfirmationByActionType || requiresConfirmationByKeyword || requiresConfirmationByModel) {
            val category = mapConfirmationCategory(action, searchableText)
            val reason = if (action.actionType == BrainActionType.FOOD_SEARCH || action.actionType == BrainActionType.GROCERY_SEARCH) {
                "Food ordering flow allowed, but final confirmation is required."
            } else {
                "Confirmation is required before executing this action."
            }
            return SafetyDecision.requireConfirmation(
                reason = reason,
                category = category,
                originalActionType = originalActionType
            )
        }

        // 6. UNKNOWN / AMBIGUOUS
        if (action.actionType == BrainActionType.UNKNOWN || action.actionType == BrainActionType.UNSUPPORTED || action.riskLevel == BrainRiskLevel.UNKNOWN) {
            return SafetyDecision.requireConfirmation(
                reason = "I'm not sure if this action is safe. Please confirm you want to proceed.",
                category = SafetyCategory.UNKNOWN_OR_UNSUPPORTED,
                originalActionType = originalActionType
            )
        }

        // 7. ALLOWED: Low Risk
        val isLowRisk = action.riskLevel == BrainRiskLevel.LOW || action.riskLevel == BrainRiskLevel.SAFE
        if (isLowRisk && !action.requiresConfirmation) {
            return SafetyDecision.allow(
                reason = "Action is safe to execute.",
                category = SafetyCategory.SAFE_LOW_RISK,
                allowedActionType = action.actionType.name,
                originalActionType = originalActionType
            )
        }

        // 8. UNKNOWN / AMBIGUOUS - Prefer confirmation over hard block
        return SafetyDecision.requireConfirmation(
            reason = "This action requires your confirmation for safety.",
            category = SafetyCategory.UNKNOWN_OR_UNSUPPORTED,
            originalActionType = originalActionType
        )
    }

    fun evaluate(intent: com.nova.luna.model.CommandIntent): SafetyDecision {
        val action = BrainAction(
            intent = intent.intentType.name,
            actionType = mapActionType(intent.actionType),
            riskLevel = intent.riskLevel,
            requiresConfirmation = intent.requiresConfirmation,
            params = intent.entities,
            rawCommand = intent.rawText,
            normalizedCommand = intent.normalizedText
        ).withPhase23Metadata(
            schemaVersion = 1,
            source = com.nova.luna.model.BrainActionSource.RULE_FALLBACK,
            rawCommand = intent.rawText,
            normalizedCommand = intent.normalizedText,
            confidence = 1.0,
            assistantReply = "",
            reason = "SafetyGate CommandIntent evaluation"
        )
        return evaluate(action, originalUserText = intent.rawText)
    }

    private fun mapActionType(type: com.nova.luna.model.ActionType): BrainActionType {
        return when (type) {
            com.nova.luna.model.ActionType.OPEN_APP -> BrainActionType.OPEN_APP
            com.nova.luna.model.ActionType.OPEN_SETTINGS -> BrainActionType.OPEN_SETTINGS
            com.nova.luna.model.ActionType.CALL_CONTACT -> BrainActionType.MAKE_CALL_DRAFT
            com.nova.luna.model.ActionType.CAB_BOOKING -> BrainActionType.CAB_SEARCH
            com.nova.luna.model.ActionType.FOOD_ORDER -> BrainActionType.FOOD_SEARCH
            com.nova.luna.model.ActionType.GROCERY_BOOKING -> BrainActionType.GROCERY_SEARCH
            com.nova.luna.model.ActionType.COMMUNICATION -> BrainActionType.SEND_MESSAGE_DRAFT
            com.nova.luna.model.ActionType.CONTENT_CREATION -> BrainActionType.CREATE_CONTENT
            com.nova.luna.model.ActionType.BLOCKED -> BrainActionType.HUMAN_ONLY
            else -> BrainActionType.EXTERNAL_ACTION
        }
    }

    private fun containsAny(text: String, terms: List<String>): Boolean {
        return terms.any { term -> text.contains(term) }
    }

    private fun findMatchingTerms(text: String, terms: List<String>): List<String> {
        return terms.filter { term -> text.contains(term) }
    }

    private fun mapConfirmationCategory(action: BrainAction, searchableText: String): SafetyCategory {
        return when {
            action.actionType == BrainActionType.MAKE_CALL_DRAFT || 
            action.actionType == BrainActionType.SEND_MESSAGE_DRAFT ||
            searchableText.contains("call") || searchableText.contains("message") || searchableText.contains("email") -> 
                SafetyCategory.MESSAGE_OR_CALL_SENSITIVE
            
            action.actionType == BrainActionType.CAB_SEARCH || 
            action.actionType == BrainActionType.FOOD_SEARCH || 
            action.actionType == BrainActionType.GROCERY_SEARCH || 
            action.actionType == BrainActionType.BOOKING_REQUEST ||
            searchableText.contains("book") || searchableText.contains("order") -> 
                SafetyCategory.BOOKING_OR_ORDER_FINALIZATION
                
            else -> SafetyCategory.MEDIUM_RISK_CONFIRMATION_REQUIRED
        }
    }
}
