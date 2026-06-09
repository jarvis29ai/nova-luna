package com.nova.luna.model

enum class BrainModelRole(val wireValue: String) {
    GEMMA_REASONING("gemma_reasoning"),
    CORE_BRAIN("core_brain"),
    MULTILINGUAL_BACKUP("multilingual_backup"),
    LITE_FALLBACK("lite_fallback"),
    ACTION_JSON("action_json"),
    LITE_COMMAND("lite_command"),
    SCREEN_UNDERSTANDING("screen_understanding"),
    ONLINE_AI_HELPER("online_ai_helper"),
    MOCK_FALLBACK("mock_fallback");

    companion object {
        fun fromWireValue(value: String?): BrainModelRole? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}

fun BrainModelRole.isLegacyLocalBrainRole(): Boolean {
    return this == BrainModelRole.GEMMA_REASONING
}

fun BrainModelRole.isFutureLocalBrainRole(): Boolean {
    return this == BrainModelRole.CORE_BRAIN ||
        this == BrainModelRole.MULTILINGUAL_BACKUP ||
        this == BrainModelRole.LITE_FALLBACK
}

fun BrainModelRole.isLocalBrainRole(): Boolean {
    return isLegacyLocalBrainRole() || isFutureLocalBrainRole()
}
