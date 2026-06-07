package com.nova.luna.memory

enum class BrainSessionStatus(val wireValue: String) {
    ACTIVE("active"),
    WAITING_FOR_USER("waiting_for_user"),
    WAITING_FOR_CONFIRMATION("waiting_for_confirmation"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    BLOCKED("blocked"),
    EXPIRED("expired"),
    FAILED("failed");

    companion object {
        fun fromWireValue(value: String?): BrainSessionStatus? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
