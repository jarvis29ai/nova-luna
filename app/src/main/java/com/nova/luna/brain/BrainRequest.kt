package com.nova.luna.brain

import com.nova.luna.memory.BrainMemorySnapshot
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.LocalUserPreferences
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.memory.RecoveryState
import com.nova.luna.screen.ScreenState

data class BrainRequest(
    val rawText: String,
    val activeCabSession: Boolean = false,
    val activeGrocerySession: Boolean = false,
    val activeFoodSession: Boolean = false,
    val screenState: ScreenState? = null,
    val onlineConsentGiven: Boolean = false,
    val activeSessionType: BrainSessionType? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val memorySnapshot: BrainMemorySnapshot? = null,
    val preferences: LocalUserPreferences? = null,
    val recoveryState: RecoveryState? = null
)
