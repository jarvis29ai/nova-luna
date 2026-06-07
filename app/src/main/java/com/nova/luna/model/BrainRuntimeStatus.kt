package com.nova.luna.model

import com.nova.luna.memory.BrainSessionType

data class BrainRuntimeStatus(
    val selectedProvider: String,
    val capabilityMode: BrainCapabilityMode,
    val internetAvailable: Boolean,
    val localModelAvailable: Boolean,
    val fallbackActive: Boolean,
    val reason: String,
    val safetyChainActive: Boolean,
    val selectedBrainRole: BrainModelRole? = null,
    val modelPathConfigured: Boolean = false,
    val modelFileExists: Boolean = false,
    val runtimeAvailable: Boolean = false,
    val modelLoaded: Boolean = false,
    val selectedLocalModelId: String? = null,
    val selectedLocalModelDisplayName: String? = null,
    val selectedLocalModelStatus: String? = null,
    val selectedLocalModelAssetMissing: Boolean = false,
    val promptBuilt: Boolean = false,
    val jsonParseSucceeded: Boolean = false,
    val modelLatencyMillis: Long? = null,
    val onlineTrace: com.nova.luna.brain.OnlineAiTrace? = null,
    val activeSessionType: BrainSessionType? = null,
    val pendingConfirmationCount: Int = 0,
    val memoryLoaded: Boolean = false,
    val memorySessionCount: Int = 0
)
