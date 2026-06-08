package com.nova.luna.memory

import java.util.UUID

enum class MemoryType {
    LANGUAGE_PREFERENCE,
    VOICE_STYLE,
    PREFERRED_APP,
    HOME_LABEL,
    WORK_LABEL,
    BUDGET_PREFERENCE,
    FOOD_PREFERENCE,
    FOOD_RESTRICTION,
    SHOPPING_PREFERENCE,
    CAB_PREFERENCE,
    GROCERY_PREFERENCE,
    CONTENT_CREATION_PREFERENCE,
    MUSIC_PREFERENCE,
    COMMUNICATION_PREFERENCE,
    USER_NOTE,
    UNKNOWN
}

enum class MemorySensitivity {
    LOW,
    MEDIUM,
    HIGH,
    SENSITIVE_BLOCKED
}

enum class MemoryAction {
    SAVE,
    UPDATE,
    DELETE,
    VIEW,
    CLEAR_ALL,
    USE,
    IGNORE
}

enum class MemoryPermissionStatus {
    ALLOWED,
    NEEDS_CONFIRMATION,
    DENIED,
    BLOCKED_SENSITIVE,
    NOT_FOUND
}

data class PersonalMemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val key: String,
    val value: String,
    val normalizedValue: String? = null,
    val sensitivity: MemorySensitivity = MemorySensitivity.LOW,
    val sourceCommand: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val usageCount: Int = 0,
    val expiresAt: Long? = null,
    val userConfirmed: Boolean = false,
    val domainScope: String? = null,
    val isEnabled: Boolean = true
)

data class MemoryDecision(
    val action: MemoryAction,
    val type: MemoryType,
    val key: String,
    val value: String,
    val sensitivity: MemorySensitivity = MemorySensitivity.LOW,
    val needsConfirmation: Boolean = false,
    val reason: String? = null,
    val userMessage: String? = null,
    val confidence: Float = 1.0f
)

data class MemoryOperationResult(
    val status: MemoryPermissionStatus,
    val action: MemoryAction,
    val memoryItem: PersonalMemoryItem? = null,
    val items: List<PersonalMemoryItem>? = null,
    val userMessage: String? = null,
    val technicalReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
