package com.nova.luna.model

enum class BrainActionType(val wireValue: String) {
    NONE("none"),
    READ_ONLY("read_only"),
    PREPARE("prepare"),
    EXTERNAL_ACTION("external_action"),
    HUMAN_ONLY("human_only");

    companion object {
        fun fromWireValue(value: String?): BrainActionType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
