package com.nova.luna.modelinstall

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

/**
 * PRODUCTION-STYLE MODEL INSTALL AND PATH MANAGEMENT SYSTEM - PHASE 21
 */
class ModelInstallService(
    private val specRegistry: ModelInstallSpecRegistry = ModelInstallSpecRegistry(),
    private val pathResolver: ModelPathResolver,
    private val verifier: ModelInstallVerifier,
    private val runtimeStateStore: ModelRuntimeStateStore,
    private val storage: PrivateAppModelStorage
) {
    fun getInstallState(modelId: String, debugOverridePath: String? = null): ModelInstallState {
        val spec = specRegistry.getSpec(modelId) ?: return unknownModel(modelId)
        val resolvedPath = pathResolver.resolve(spec, debugOverridePath)
        return verifier.verify(spec, resolvedPath)
    }

    fun getReadyModelPath(modelId: String): String? {
        val state = getInstallState(modelId)
        return if (state.ready) state.resolvedPath else null
    }

    fun saveVerifiedModelPath(modelId: String, path: String) {
        val spec = specRegistry.getSpec(modelId) ?: return
        val packId = ModelPackId.fromWireValue(modelId) ?: ModelPackId.CORE
        val state = verifier.verify(spec, path)
        if (state.ready) {
            val currentState = runtimeStateStore.find(packId)
            val updated = (currentState ?: ModelRuntimeState(
                packId = packId,
                version = "1.0.0",
                displayName = spec.displayName,
                runtimeStatus = ModelRuntimeStatus.READY,
                installState = ModelInstallStatus.READY,
                ready = true,
                runtimeLoaded = true,
                healthCheckPassed = true,
                registryConfirmed = true,
                verificationPassed = true,
                expectedFileCount = 1,
                verifiedFileCount = 1,
                modelRootPath = path
            )).copy(
                runtimeStatus = ModelRuntimeStatus.READY,
                installState = ModelInstallStatus.READY,
                ready = true,
                runtimeLoaded = true,
                healthCheckPassed = true,
                registryConfirmed = true,
                verificationPassed = true,
                modelRootPath = path,
                message = ModelInstallReason.MODEL_READY
            )
            runtimeStateStore.upsert(updated)
        }
    }

    fun clearBrokenModelPath(modelId: String) {
        val packId = ModelPackId.fromWireValue(modelId) ?: return
        val currentState = runtimeStateStore.find(packId)
        if (currentState != null) {
            val updated = currentState.copy(
                runtimeStatus = ModelRuntimeStatus.MISSING,
                installState = ModelInstallStatus.NOT_INSTALLED,
                ready = false,
                modelRootPath = null,
                message = ModelInstallReason.MODEL_FILE_MISSING
            )
            runtimeStateStore.upsert(updated)
        }
    }

    fun repairModelPath(modelId: String): ModelInstallState {
        val spec = specRegistry.getSpec(modelId) ?: return unknownModel(modelId)
        val packId = ModelPackId.fromWireValue(modelId) ?: ModelPackId.CORE
        
        // Try to resolve without stored path to see if it's in default locations
        val internalFile = File(storage.modelsDir(packId), spec.expectedFileName)
        if (internalFile.exists()) {
            val state = verifier.verify(spec, internalFile.absolutePath)
            if (state.ready) {
                saveVerifiedModelPath(modelId, internalFile.absolutePath)
                return state.copy(reason = ModelInstallReason.MODEL_REPAIR_SUCCESS)
            }
        }
        
        return getInstallState(modelId).copy(reason = ModelInstallReason.MODEL_REPAIR_FAILED)
    }

    fun importFromLocalFile(modelId: String, sourcePath: String): ModelInstallState {
        val spec = specRegistry.getSpec(modelId) ?: return unknownModel(modelId)
        val packId = ModelPackId.fromWireValue(modelId) ?: ModelPackId.CORE
        
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return verifier.verify(spec, null).copy(reason = "SOURCE_FILE_MISSING")
        }
        
        val targetDir = storage.modelsDir(packId)
        targetDir.mkdirs()
        val tempFile = File(targetDir, "importing_${spec.expectedFileName}")
        val targetFile = File(targetDir, spec.expectedFileName)
        
        try {
            copyFile(sourceFile, tempFile)
            val state = verifier.verify(spec, tempFile.absolutePath)
            if (state.ready) {
                if (tempFile.renameTo(targetFile)) {
                    saveVerifiedModelPath(modelId, targetFile.absolutePath)
                    return state.copy(resolvedPath = targetFile.absolutePath, reason = ModelInstallReason.MODEL_READY)
                } else {
                    // Fallback to non-atomic if rename fails
                    copyFile(tempFile, targetFile)
                    tempFile.delete()
                    saveVerifiedModelPath(modelId, targetFile.absolutePath)
                    return state.copy(resolvedPath = targetFile.absolutePath, reason = ModelInstallReason.MODEL_READY)
                }
            } else {
                tempFile.delete()
                return state.copy(reason = "IMPORT_VERIFICATION_FAILED")
            }
        } catch (e: Exception) {
            tempFile.delete()
            return verifier.verify(spec, null).copy(reason = "IMPORT_ERROR: ${e.message}")
        }
    }

    private fun copyFile(source: File, target: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val inChannel: FileChannel = input.channel
                val outChannel: FileChannel = output.channel
                inChannel.transferTo(0, inChannel.size(), outChannel)
            }
        }
    }

    private fun unknownModel(modelId: String): ModelInstallState {
        return ModelInstallState(
            modelId = modelId,
            displayName = "Unknown Model",
            role = "UNKNOWN",
            expectedFileName = "",
            resolvedPath = null,
            exists = false,
            readable = false,
            sizeBytes = null,
            minimumBytes = 0,
            sha256Expected = null,
            sha256Actual = null,
            sha256Verified = false,
            extensionAllowed = false,
            ready = false,
            reason = "UNKNOWN_MODEL_ID"
        )
    }
}

class ModelInstallSpecRegistry(
    customSpecs: List<ModelInstallSpec>? = null
) {
    val PRIMARY_BRAIN = ModelInstallSpec(
        modelId = "core",
        displayName = "Core Brain",
        role = "BRAIN",
        expectedFileName = "gemma-3n-E2B-it-int4.litertlm",
        expectedSha256 = null,
        minimumBytes = 100_000_000,
        preferredInstallDirName = "core",
        allowedExtensions = listOf(".gguf", ".bin", ".litertlm")
    )

    val LITE_FALLBACK = ModelInstallSpec(
        modelId = "lite",
        displayName = "Lightweight Fallback",
        role = "LITE_FALLBACK",
        expectedFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        expectedSha256 = null,
        minimumBytes = 100_000_000,
        preferredInstallDirName = "lite",
        allowedExtensions = listOf(".gguf", ".bin")
    )

    val FULL_MULTILINGUAL = ModelInstallSpec(
        modelId = "full",
        displayName = "Multilingual Backup",
        role = "MULTILINGUAL_BACKUP",
        expectedFileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        expectedSha256 = null,
        minimumBytes = 300_000_000,
        preferredInstallDirName = "full",
        allowedExtensions = listOf(".gguf", ".bin")
    )

    private val specs = if (customSpecs != null) {
        customSpecs.associateBy { it.modelId }
    } else {
        mapOf(
            PRIMARY_BRAIN.modelId to PRIMARY_BRAIN,
            LITE_FALLBACK.modelId to LITE_FALLBACK,
            FULL_MULTILINGUAL.modelId to FULL_MULTILINGUAL
        )
    }

    fun getSpec(modelId: String): ModelInstallSpec? = specs[modelId]
    
    fun allSpecs(): List<ModelInstallSpec> = specs.values.toList()
}
