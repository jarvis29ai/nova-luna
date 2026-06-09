package com.nova.luna.modelinstall

import java.io.File

enum class RuntimeHealthCheckStatus {
    PASSED,
    FAILED,
    UNAVAILABLE
}

data class RuntimeHealthCheckResult(
    val prompt: String,
    val status: RuntimeHealthCheckStatus,
    val response: String? = null,
    val reason: String? = null
) {
    val passed: Boolean
        get() = status == RuntimeHealthCheckStatus.PASSED

    companion object {
        fun passed(
            prompt: String,
            response: String? = null
        ): RuntimeHealthCheckResult {
            return RuntimeHealthCheckResult(
                prompt = prompt.trim(),
                status = RuntimeHealthCheckStatus.PASSED,
                response = response?.trim()?.takeIf { it.isNotBlank() }
            )
        }

        fun failed(
            prompt: String,
            reason: String? = null,
            response: String? = null
        ): RuntimeHealthCheckResult {
            return RuntimeHealthCheckResult(
                prompt = prompt.trim(),
                status = RuntimeHealthCheckStatus.FAILED,
                response = response?.trim()?.takeIf { it.isNotBlank() },
                reason = reason?.trim()?.takeIf { it.isNotBlank() }
            )
        }

        fun unavailable(
            prompt: String,
            reason: String? = null
        ): RuntimeHealthCheckResult {
            return RuntimeHealthCheckResult(
                prompt = prompt.trim(),
                status = RuntimeHealthCheckStatus.UNAVAILABLE,
                reason = reason?.trim()?.takeIf { it.isNotBlank() }
            )
        }
    }
}

data class LoadedModelRef(
    val packId: ModelPackId,
    val displayName: String,
    val version: String,
    val modelRootPath: String,
    val modelFileKeys: List<String>,
    val modelFiles: List<File>,
    val manifestPath: String? = null,
    val selection: ModelPackSelection? = null
) {
    val privateStorageRoot: File
        get() = File(modelRootPath)

    val ready: Boolean
        get() = modelFiles.isNotEmpty() &&
            modelFiles.all { it.exists() && it.isFile } &&
            isPrivateStorage()

    fun isPrivateStorage(): Boolean {
        val root = privateStorageRoot.canonicalFile
        return modelFiles.all { it.isInsideDirectory(root) }
    }

    companion object {
        fun fromLoadResult(loadResult: LocalModelLoadResult): LoadedModelRef? {
            val installStatus = loadResult.installStatus ?: return null
            val modelRootPath = installStatus.runtimeState.modelRootPath?.trim()?.takeIf { it.isNotBlank() }
                ?: loadResult.modelFiles.firstOrNull()?.parentFile?.canonicalPath
                ?: return null

            return LoadedModelRef(
                packId = loadResult.packId,
                displayName = installStatus.displayName,
                version = installStatus.runtimeState.version,
                modelRootPath = modelRootPath,
                modelFileKeys = installStatus.expectedFileKeys,
                modelFiles = loadResult.modelFiles.map { it.canonicalFile },
                manifestPath = installStatus.runtimeState.manifestPath,
                selection = loadResult.selection
            )
        }
    }
}

data class LoadedModel(
    val ref: LoadedModelRef,
    val backendName: String = "local-runtime",
    val loadedAtEpochMs: Long = System.currentTimeMillis()
)

interface BrainModelRuntime {
    fun load(modelRef: LoadedModelRef): LoadedModel?

    fun healthCheck(
        loadedModel: LoadedModel,
        prompt: String
    ): RuntimeHealthCheckResult
}

object NoOpBrainModelRuntime : BrainModelRuntime {
    override fun load(modelRef: LoadedModelRef): LoadedModel? {
        return if (modelRef.ready && modelRef.isPrivateStorage()) {
            LoadedModel(ref = modelRef)
        } else {
            null
        }
    }

    override fun healthCheck(
        loadedModel: LoadedModel,
        prompt: String
    ): RuntimeHealthCheckResult {
        return if (prompt.isBlank()) {
            RuntimeHealthCheckResult.failed(
                prompt = prompt,
                reason = "Health-check prompt is blank."
            )
        } else {
            RuntimeHealthCheckResult.passed(
                prompt = prompt,
                response = "pong"
            )
        }
    }
}

private fun File.isInsideDirectory(parent: File): Boolean {
    val parentPath = parent.canonicalFile.path.trimEnd(File.separatorChar)
    val childPath = canonicalFile.path
    return childPath == parentPath || childPath.startsWith("$parentPath${File.separator}")
}
