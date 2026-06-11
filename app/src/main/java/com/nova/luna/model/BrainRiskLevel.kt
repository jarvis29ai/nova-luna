package com.nova.luna.model

enum class BrainRiskLevel(val wireValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    HUMAN_ONLY("human_only"),
    UNKNOWN("unknown"),
    
    // Legacy values
    SAFE("safe"),
    CONFIRMATION_REQUIRED("confirmation_required"),
    BLOCKED("blocked");

    companion object {
        fun fromWireValue(value: String?): BrainRiskLevel? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
