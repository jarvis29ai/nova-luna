package com.nova.luna.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallSpecRegistry
import com.nova.luna.safety.SafetyGate
import java.io.File

class ModelDiagnosticsProvider(
    private val context: Context,
    private val modelInstallService: ModelInstallService,
    private val safetyGate: SafetyGate
) {

    fun getDiagnostics(): ModelDiagnosticsResult {
        val registry = ModelInstallSpecRegistry()
        val models = registry.allSpecs().map { spec ->
            val state = modelInstallService.getInstallState(spec.modelId)
            val file = state.resolvedPath?.let { File(it) }
            
            ModelDiagnosticInfo(
                modelId = spec.modelId,
                displayName = spec.displayName,
                role = spec.role,
                expectedFileName = spec.expectedFileName,
                resolvedPath = state.resolvedPath,
                exists = file?.exists() ?: false,
                readable = file?.canRead() ?: false,
                sizeBytes = file?.length(),
                ready = state.ready,
                reason = state.reason
            )
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val statFs = StatFs(Environment.getDataDirectory().path)
        val storageTotal = (statFs.totalBytes / 1024 / 1024)
        val storageAvailable = (statFs.availableBytes / 1024 / 1024)

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        models.forEach {
            if (!it.exists) errors.add("Model file missing for ${it.displayName}")
            if (it.exists && !it.ready) warnings.add("Model ready check failed for ${it.displayName}")
        }
        
        if (storageAvailable < 500) warnings.add("Low storage space: ${storageAvailable}MB available")

        val overallStatus = when {
            errors.isNotEmpty() -> "ERROR"
            models.all { it.ready } -> "READY"
            models.any { it.ready } -> "PARTIAL"
            else -> "MISSING_MODEL"
        }

        return ModelDiagnosticsResult(
            overallStatus = overallStatus,
            installedModels = models,
            ramTotalMb = memInfo.totalMem / 1024 / 1024,
            ramAvailableMb = memInfo.availMem / 1024 / 1024,
            storageTotalMb = storageTotal,
            storageAvailableMb = storageAvailable,
            safetyGateAvailable = true,
            brainRouterAvailable = true,
            warnings = warnings,
            errors = errors
        )
    }
}
