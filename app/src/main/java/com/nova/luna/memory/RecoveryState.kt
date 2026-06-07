package com.nova.luna.memory

data class RecoveryState(
    val sessionType: BrainSessionType,
    val retryCount: Int = 0,
    val lastFailureReason: String? = null,
    val lastSuggestion: String? = null,
    val canRetry: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
