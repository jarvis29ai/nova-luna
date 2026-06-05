package com.nova.luna.model

enum class BrainCapabilityMode(val wireValue: String) {
    OFFLINE_ONLY("offline_only"),
    ONLINE_ASSISTED("online_assisted"),
    LOCAL_LLM_DEV("local_llm_dev");

    companion object {
        fun fromWireValue(value: String?): BrainCapabilityMode? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
