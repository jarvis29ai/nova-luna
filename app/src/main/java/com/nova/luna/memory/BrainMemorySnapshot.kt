package com.nova.luna.memory

data class BrainMemorySnapshot(
    val sessions: Map<BrainSessionType, BrainSession> = emptyMap(),
    val pendingConfirmations: List<PendingConfirmation> = emptyList(),
    val screenSnapshots: Map<String, ScreenMemorySnapshot> = emptyMap(),
    val recoveryStates: Map<BrainSessionType, RecoveryState> = emptyMap(),
    val preferences: LocalUserPreferences = LocalUserPreferences(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val activePendingConfirmation: PendingConfirmation?
        get() = pendingConfirmations.firstOrNull { it.isPending() }

    val activePendingConfirmationCount: Int
        get() = pendingConfirmations.count { it.isPending() }

    val activeSessionCount: Int
        get() = sessions.values.count { it.status == BrainSessionStatus.ACTIVE || it.status == BrainSessionStatus.WAITING_FOR_CONFIRMATION || it.status == BrainSessionStatus.WAITING_FOR_USER }

    fun activeSession(type: BrainSessionType): BrainSession? {
        return sessions[type]?.takeIf { it.status == BrainSessionStatus.ACTIVE || it.status == BrainSessionStatus.WAITING_FOR_CONFIRMATION || it.status == BrainSessionStatus.WAITING_FOR_USER }
    }

    fun activeSessionType(priority: List<BrainSessionType> = defaultPriority()): BrainSessionType? {
        return priority.firstOrNull { sessionType ->
            activeSession(sessionType) != null
        }
    }

    private fun defaultPriority(): List<BrainSessionType> {
        return listOf(
            BrainSessionType.GROCERY,
            BrainSessionType.FOOD,
            BrainSessionType.CAB,
            BrainSessionType.SHOPPING,
            BrainSessionType.COMMUNICATION,
            BrainSessionType.CONTENT,
            BrainSessionType.MUSIC,
            BrainSessionType.MEDIA,
            BrainSessionType.PHONE,
            BrainSessionType.SCREEN,
            BrainSessionType.BASIC_CONTROL,
            BrainSessionType.ONLINE_HELPER,
            BrainSessionType.LOCAL_LLM,
            BrainSessionType.UNKNOWN
        )
    }
}
