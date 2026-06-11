package com.nova.luna.modelinstall

import android.content.Context
import java.io.File

/**
 * PRODUCTION-STYLE MODEL INSTALL AND PATH MANAGEMENT SYSTEM - PHASE 21
 *
 * Path priority:
 * A. Verified stored path if still valid
 * B. Debug override path if provided
 * C. Default internal files model path
 * D. External files model path if present
 * E. Missing state
 */
class ModelPathResolver(
    private val context: Context,
    private val storage: PrivateAppModelStorage,
    private val runtimeStateStore: ModelRuntimeStateStore
) {
    fun resolve(spec: ModelInstallSpec, debugOverridePath: String? = null): String? {
        // A. Verified stored path if still valid
        val storedPath = runtimeStateStore.find(ModelPackId.fromWireValue(spec.modelId) ?: ModelPackId.CORE)?.modelRootPath
        if (storedPath != null) {
            val file = File(storedPath)
            if (file.exists() && file.isFile && file.canRead()) {
                // If it's a directory, we expect the file inside
                if (file.isDirectory) {
                    val modelFile = File(file, spec.expectedFileName)
                    if (modelFile.exists()) return modelFile.absolutePath
                } else {
                    return file.absolutePath
                }
            }
        }

        // B. Debug override path if provided
        if (debugOverridePath != null) {
            val file = File(debugOverridePath)
            if (file.exists()) return file.absolutePath
        }

        // C. Default internal files model path
        val internalFile = File(storage.modelsDir(ModelPackId.fromWireValue(spec.modelId) ?: ModelPackId.CORE), spec.expectedFileName)
        if (internalFile.exists()) return internalFile.absolutePath

        // D. External files model path if present
        val externalDir = context.getExternalFilesDir("models")
        if (externalDir != null) {
            val externalFile = File(externalDir, spec.expectedFileName)
            if (externalFile.exists()) return externalFile.absolutePath
            
            // Also check subdirectories by modelId or preferredInstallDirName
            val modelSubDir = File(externalDir, spec.preferredInstallDirName)
            val externalFileSub = File(modelSubDir, spec.expectedFileName)
            if (externalFileSub.exists()) return externalFileSub.absolutePath
        }

        return null
    }
}
