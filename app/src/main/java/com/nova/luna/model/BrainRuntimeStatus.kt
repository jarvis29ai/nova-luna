package com.nova.luna.model

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
    val modelLoaded: Boolean = false
)
