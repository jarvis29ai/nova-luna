package com.nova.luna.diagnostics

data class ModelDiagnosticInfo(
    val modelId: String,
    val displayName: String,
    val role: String,
    val expectedFileName: String,
    val resolvedPath: String?,
    val exists: Boolean,
    val readable: Boolean,
    val sizeBytes: Long?,
    val ready: Boolean,
    val reason: String,
    val lastCheckTimestamp: Long = System.currentTimeMillis()
)

data class ModelDiagnosticsResult(
    val timestamp: Long = System.currentTimeMillis(),
    val overallStatus: String, // READY, PARTIAL, MISSING_MODEL, ERROR
    val installedModels: List<ModelDiagnosticInfo>,
    val ramTotalMb: Long,
    val ramAvailableMb: Long,
    val storageTotalMb: Long,
    val storageAvailableMb: Long,
    val safetyGateAvailable: Boolean,
    val brainRouterAvailable: Boolean,
    val warnings: List<String>,
    val errors: List<String>
)
