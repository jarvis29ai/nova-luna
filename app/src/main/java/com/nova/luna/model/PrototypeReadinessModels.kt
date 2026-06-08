package com.nova.luna.model

enum class PrototypeReadinessStatus {
    READY,
    PARTIAL_READY,
    BLOCKED,
    WARNING,
    UNKNOWN
}

data class PrototypeReadinessReport(
    val overallStatus: PrototypeReadinessStatus,
    val permissionsStatus: PrototypeReadinessStatus,
    val accessibilityStatus: PrototypeReadinessStatus,
    val voiceInputStatus: PrototypeReadinessStatus,
    val voiceResponseStatus: PrototypeReadinessStatus,
    val popupStatus: PrototypeReadinessStatus,
    val localLlmStatus: PrototypeReadinessStatus,
    val memoryStatus: PrototypeReadinessStatus,
    val safetyStatus: PrototypeReadinessStatus,
    val demoFlowStatus: PrototypeReadinessStatus,
    val missingRequirements: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val userActionsNeeded: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
