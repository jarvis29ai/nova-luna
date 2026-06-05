package com.nova.luna.model

enum class BrainModelRole(val wireValue: String) {
    GEMMA_REASONING("gemma_reasoning"),
    ACTION_JSON("action_json"),
    LITE_COMMAND("lite_command"),
    SCREEN_UNDERSTANDING("screen_understanding"),
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
