package com.nova.luna.memory

enum class BrainSessionType(val wireValue: String) {
    CAB("cab"),
    FOOD("food"),
    GROCERY("grocery"),
    SHOPPING("shopping"),
    MUSIC("music"),
    MEDIA("media"),
    CONTENT("content"),
    COMMUNICATION("communication"),
    PHONE("phone"),
    SCREEN("screen"),
    BASIC_CONTROL("basic_control"),
    ONLINE_HELPER("online_helper"),
    LOCAL_LLM("local_llm"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String?): BrainSessionType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
