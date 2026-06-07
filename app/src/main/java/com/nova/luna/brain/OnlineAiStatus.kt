package com.nova.luna.brain

enum class OnlineAiStatus(val wireValue: String) {
    READY("ready"),
    DISABLED("disabled"),
    ASK_USER_PERMISSION("ask_user_permission"),
    BLOCKED_PRIVACY("blocked_privacy"),
    BLOCKED_SENSITIVE("blocked_sensitive"),
    BLOCKED_NO_INTERNET("blocked_no_internet"),
    BLOCKED_USER_DISABLED("blocked_user_disabled"),
    BLOCKED_TASK_NOT_NEEDED("blocked_task_not_needed"),
    FALLBACK_LOCAL("fallback_local"),
    SANITIZED("sanitized"),
    REJECTED("rejected"),
    FAILED("failed"),
    TIMEOUT("timeout"),
    UNAVAILABLE("unavailable"),
    SKIPPED("skipped");

    companion object {
        fun fromWireValue(value: String?): OnlineAiStatus? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
