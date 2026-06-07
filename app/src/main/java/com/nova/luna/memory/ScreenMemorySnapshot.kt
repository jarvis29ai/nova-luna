package com.nova.luna.memory

import com.nova.luna.screen.ScreenRiskSignal

data class ScreenMemorySnapshot(
    val snapshotId: String,
    val sessionId: String? = null,
    val packageName: String,
    val appName: String? = null,
    val summary: String,
    val visibleOptions: List<String> = emptyList(),
    val selectedOption: String? = null,
    val riskSignals: List<ScreenRiskSignal> = emptyList(),
    val redactionCount: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
