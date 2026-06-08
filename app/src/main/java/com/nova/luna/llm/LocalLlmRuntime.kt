package com.nova.luna.llm

import android.content.Context
import com.nova.luna.brain.ModelAssetLocator

interface LocalLlmRuntime {
    fun isReady(modelId: LocalLlmModelId): LocalLlmStatus
    fun generate(request: LocalLlmRequest): LocalLlmResult
    fun close()
    fun diagnostics(): LocalLlmDiagnostics
}

data class LocalLlmDiagnostics(
    val selectedModel: LocalLlmModelId? = null,
    val readinessStatus: LocalLlmStatus = LocalLlmStatus.NOT_CONFIGURED,
    val assetFound: Boolean = false,
    val engineAvailable: Boolean = false,
    val memoryOk: Boolean = false,
    val promptWithinLimit: Boolean = false,
    val lastError: String? = null,
    val lastLatency: Long = 0,
    val generationCount: Int = 0,
    val invalidJsonCount: Int = 0,
    val blockedOutputCount: Int = 0
)

class LocalLlmReadinessChecker(
    private val context: Context,
    private val assetLocator: ModelAssetLocator = ModelAssetLocator()
) {
    fun check(config: LocalLlmModelConfig): LocalLlmStatus {
        if (config.assetPath.isBlank()) return LocalLlmStatus.NOT_CONFIGURED
        
        // Use the existing ModelAssetLocator which check file existence
        // We'll simulate it for now or use the real one if it fits.
        // Actually, the real one takes PhoneLocalLlmModelConfig.
        // I'll just check file existence directly here for now to be decoupled.
        val file = java.io.File(config.assetPath)
        if (!file.exists()) return LocalLlmStatus.ASSET_MISSING
        
        // Memory check (simplified)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availableMb = memoryInfo.availMem / (1024 * 1024)
        
        if (availableMb < config.minMemoryMbRequired / 2) { // Allow some buffer
             return LocalLlmStatus.LOW_MEMORY_DISABLED
        }
        
        return LocalLlmStatus.READY
    }
}
