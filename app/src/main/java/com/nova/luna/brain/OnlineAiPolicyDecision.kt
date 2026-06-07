package com.nova.luna.brain

enum class OnlineAiPolicyDecision(val wireValue: String) {
    ALLOW("allow"),
    DENY_PRIVACY("deny_privacy"),
    DENY_SENSITIVE("deny_sensitive"),
    DENY_NO_INTERNET("deny_no_internet"),
    DENY_USER_DISABLED("deny_user_disabled"),
    DENY_TASK_NOT_NEEDED("deny_task_not_needed"),
    ASK_USER_PERMISSION("ask_user_permission"),
    FALLBACK_LOCAL("fallback_local");

    companion object {
        fun fromWireValue(value: String?): OnlineAiPolicyDecision? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
