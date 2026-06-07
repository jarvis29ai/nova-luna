package com.nova.luna.brain

data class OnlineAiTrace(
    val providerType: OnlineAiProviderType,
    val providerName: String,
    val policyDecision: OnlineAiPolicyDecision,
    val status: OnlineAiStatus,
    val used: Boolean,
    val skipped: Boolean,
    val blocked: Boolean,
    val failed: Boolean,
    val fallbackUsed: Boolean,
    val redactionCount: Int,
    val promptBuilt: Boolean,
    val providerSucceeded: Boolean,
    val sanitizerSucceeded: Boolean,
    val reason: String,
    val userConsentGiven: Boolean,
    val networkAvailable: Boolean,
    val privacyBlocked: Boolean,
    val latencyMillis: Long? = null
) {
    companion object {
        fun skipped(
            providerType: OnlineAiProviderType,
            providerName: String,
            reason: String,
            networkAvailable: Boolean,
            userConsentGiven: Boolean = false
        ): OnlineAiTrace {
            return OnlineAiTrace(
                providerType = providerType,
                providerName = providerName,
                policyDecision = OnlineAiPolicyDecision.FALLBACK_LOCAL,
                status = OnlineAiStatus.SKIPPED,
                used = false,
                skipped = true,
                blocked = false,
                failed = false,
                fallbackUsed = true,
                redactionCount = 0,
                promptBuilt = false,
                providerSucceeded = false,
                sanitizerSucceeded = false,
                reason = reason,
                userConsentGiven = userConsentGiven,
                networkAvailable = networkAvailable,
                privacyBlocked = false
            )
        }

        fun blocked(
            providerType: OnlineAiProviderType,
            providerName: String,
            decision: OnlineAiPolicyDecision,
            reason: String,
            networkAvailable: Boolean,
            redactionCount: Int = 0,
            userConsentGiven: Boolean = false,
            privacyBlocked: Boolean = false
        ): OnlineAiTrace {
            return OnlineAiTrace(
                providerType = providerType,
                providerName = providerName,
                policyDecision = decision,
                status = when (decision) {
                    OnlineAiPolicyDecision.DENY_NO_INTERNET -> OnlineAiStatus.BLOCKED_NO_INTERNET
                    OnlineAiPolicyDecision.DENY_PRIVACY -> OnlineAiStatus.BLOCKED_PRIVACY
                    OnlineAiPolicyDecision.DENY_SENSITIVE -> OnlineAiStatus.BLOCKED_SENSITIVE
                    OnlineAiPolicyDecision.DENY_USER_DISABLED -> OnlineAiStatus.BLOCKED_USER_DISABLED
                    OnlineAiPolicyDecision.DENY_TASK_NOT_NEEDED -> OnlineAiStatus.BLOCKED_TASK_NOT_NEEDED
                    OnlineAiPolicyDecision.ASK_USER_PERMISSION -> OnlineAiStatus.ASK_USER_PERMISSION
                    OnlineAiPolicyDecision.FALLBACK_LOCAL -> OnlineAiStatus.FALLBACK_LOCAL
                    OnlineAiPolicyDecision.ALLOW -> OnlineAiStatus.FAILED
                },
                used = false,
                skipped = false,
                blocked = true,
                failed = false,
                fallbackUsed = true,
                redactionCount = redactionCount,
                promptBuilt = false,
                providerSucceeded = false,
                sanitizerSucceeded = false,
                reason = reason,
                userConsentGiven = userConsentGiven,
                networkAvailable = networkAvailable,
                privacyBlocked = privacyBlocked
            )
        }

        fun used(
            providerType: OnlineAiProviderType,
            providerName: String,
            decision: OnlineAiPolicyDecision,
            reason: String,
            networkAvailable: Boolean,
            redactionCount: Int,
            promptBuilt: Boolean,
            providerSucceeded: Boolean,
            sanitizerSucceeded: Boolean,
            userConsentGiven: Boolean,
            latencyMillis: Long? = null
        ): OnlineAiTrace {
            return OnlineAiTrace(
                providerType = providerType,
                providerName = providerName,
                policyDecision = decision,
                status = if (sanitizerSucceeded) OnlineAiStatus.SANITIZED else OnlineAiStatus.FAILED,
                used = true,
                skipped = false,
                blocked = false,
                failed = !sanitizerSucceeded,
                fallbackUsed = false,
                redactionCount = redactionCount,
                promptBuilt = promptBuilt,
                providerSucceeded = providerSucceeded,
                sanitizerSucceeded = sanitizerSucceeded,
                reason = reason,
                userConsentGiven = userConsentGiven,
                networkAvailable = networkAvailable,
                privacyBlocked = false,
                latencyMillis = latencyMillis
            )
        }

        fun askPermission(
            providerType: OnlineAiProviderType,
            providerName: String,
            reason: String,
            networkAvailable: Boolean,
            userConsentGiven: Boolean = false
        ): OnlineAiTrace {
            return OnlineAiTrace(
                providerType = providerType,
                providerName = providerName,
                policyDecision = OnlineAiPolicyDecision.ASK_USER_PERMISSION,
                status = OnlineAiStatus.ASK_USER_PERMISSION,
                used = false,
                skipped = false,
                blocked = false,
                failed = false,
                fallbackUsed = false,
                redactionCount = 0,
                promptBuilt = false,
                providerSucceeded = false,
                sanitizerSucceeded = false,
                reason = reason,
                userConsentGiven = userConsentGiven,
                networkAvailable = networkAvailable,
                privacyBlocked = false
            )
        }

        fun failed(
            providerType: OnlineAiProviderType,
            providerName: String,
            decision: OnlineAiPolicyDecision,
            reason: String,
            networkAvailable: Boolean,
            redactionCount: Int = 0,
            userConsentGiven: Boolean = false,
            promptBuilt: Boolean = false
        ): OnlineAiTrace {
            return OnlineAiTrace(
                providerType = providerType,
                providerName = providerName,
                policyDecision = decision,
                status = OnlineAiStatus.FAILED,
                used = false,
                skipped = false,
                blocked = false,
                failed = true,
                fallbackUsed = true,
                redactionCount = redactionCount,
                promptBuilt = promptBuilt,
                providerSucceeded = false,
                sanitizerSucceeded = false,
                reason = reason,
                userConsentGiven = userConsentGiven,
                networkAvailable = networkAvailable,
                privacyBlocked = false
            )
        }
    }
}
