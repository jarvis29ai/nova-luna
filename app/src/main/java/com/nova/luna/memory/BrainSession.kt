package com.nova.luna.memory

data class BrainSession(
    val sessionId: String,
    val sessionType: BrainSessionType,
    val status: BrainSessionStatus,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val sourceCommand: String,
    val normalizedGoal: String,
    val activeDomain: BrainSessionType = sessionType,
    val selectedAppProvider: String? = null,
    val currentStep: String? = null,
    val lastSafeAction: String? = null,
    val lastSafeResult: String? = null,
    val lastSpokenReply: String? = null,
    val lastOptionsShown: List<String> = emptyList(),
    val selectedOption: String? = null,
    val pendingUserInputType: String? = null,
    val retryCount: Int = 0,
    val recoveryState: RecoveryState? = null,
    val safeScreenSummaryId: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return expiresAtMillis?.let { nowMillis >= it } ?: false
    }
}
