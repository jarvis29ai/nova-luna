package com.nova.luna.brain

enum class OnlineAiProviderType(val wireValue: String) {
    UNAVAILABLE("unavailable"),
    FAKE("fake"),
    CHATGPT("chatgpt"),
    GEMINI("gemini"),
    CLAUDE("claude");

    companion object {
        fun fromWireValue(value: String?): OnlineAiProviderType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
