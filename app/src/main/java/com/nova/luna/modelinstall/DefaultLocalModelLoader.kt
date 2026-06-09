package com.nova.luna.modelinstall

import java.io.File

class DefaultLocalModelLoader(
    private val storage: PrivateAppModelStorage,
    private val coordinator: ModelInstallCoordinator = ModelInstallCoordinator(storage),
    private val backend: LocalRuntimeBackend = NoOpLocalRuntimeBackend
) : LocalModelLoader {
    override fun load(
        packId: ModelPackId,
        selection: ModelPackSelection?
    ): LocalModelLoadResult {
        val installStatus = coordinator.getInstallStatus(packId)

        if (!installStatus.ready) {
            return buildNotReadyResult(
                packId = packId,
                selection = selection,
                installStatus = installStatus
            )
        }

        val modelDir = storage.modelsDir(packId).canonicalFile
        val modelFiles = installStatus.expectedFileKeys.map { key ->
            storage.packFile(packId, key)
        }

        if (modelFiles.isEmpty()) {
            return LocalModelLoadResult(
                packId = packId,
                status = LocalModelLoadStatus.NOT_READY,
                reason = "No verified model files are available for ${installStatus.displayName}.",
                installStatus = installStatus,
                selection = selection
            )
        }

        val unsafeFile = modelFiles.firstOrNull { !it.isInsideDirectory(modelDir) }
        if (unsafeFile != null) {
            return LocalModelLoadResult(
                packId = packId,
                status = LocalModelLoadStatus.REFUSED,
                reason = "Model files must stay inside private app storage.",
                modelFiles = emptyList(),
                installStatus = installStatus,
                selection = selection
            )
        }

        val missingFile = modelFiles.firstOrNull { !it.exists() }
        if (missingFile != null) {
            return LocalModelLoadResult(
                packId = packId,
                status = LocalModelLoadStatus.MISSING,
                reason = "Verified model file is missing: ${missingFile.path}.",
                modelFiles = modelFiles,
                installStatus = installStatus,
                selection = selection
            )
        }

        return try {
            if (backend.load(modelFiles)) {
                LocalModelLoadResult(
                    packId = packId,
                    status = LocalModelLoadStatus.READY,
                    modelFiles = modelFiles,
                    reason = "${installStatus.displayName} is ready for local runtime use.",
                    installStatus = installStatus,
                    selection = selection
                )
            } else {
                LocalModelLoadResult(
                    packId = packId,
                    status = LocalModelLoadStatus.FAILED,
                    modelFiles = modelFiles,
                    reason = "Local runtime backend refused to load ${installStatus.displayName}.",
                    installStatus = installStatus,
                    selection = selection
                )
            }
        } catch (throwable: Throwable) {
            LocalModelLoadResult(
                packId = packId,
                status = LocalModelLoadStatus.FAILED,
                modelFiles = modelFiles,
                reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Local runtime backend failed for ${installStatus.displayName}.",
                installStatus = installStatus,
                selection = selection
            )
        }
    }

    private fun buildNotReadyResult(
        packId: ModelPackId,
        selection: ModelPackSelection?,
        installStatus: ModelInstallStatusSnapshot
    ): LocalModelLoadResult {
        val status = when (installStatus.runtimeStatus) {
            ModelRuntimeStatus.MISSING -> LocalModelLoadStatus.MISSING
            ModelRuntimeStatus.CORRUPT -> LocalModelLoadStatus.CORRUPT
            ModelRuntimeStatus.FAILED,
            ModelRuntimeStatus.CANCELLED -> LocalModelLoadStatus.FAILED
            else -> LocalModelLoadStatus.NOT_READY
        }

        val reason = when (status) {
            LocalModelLoadStatus.MISSING ->
                buildProblemMessage("Verified model file is missing", installStatus.missingFileKeys)
            LocalModelLoadStatus.CORRUPT ->
                buildProblemMessage("Verified model file failed SHA-256 checks", installStatus.corruptFileKeys)
            LocalModelLoadStatus.FAILED ->
                installStatus.runtimeState.message
                    ?: "Local runtime is unavailable for ${installStatus.displayName}."
            else ->
                installStatus.runtimeState.message
                    ?: "Model ${installStatus.displayName} is not ready for local runtime use."
        }

        return LocalModelLoadResult(
            packId = packId,
            status = status,
            reason = reason,
            installStatus = installStatus,
            selection = selection
        )
    }

    private fun buildProblemMessage(prefix: String, fileKeys: List<String>): String {
        return if (fileKeys.isEmpty()) {
            prefix
        } else {
            "$prefix: ${fileKeys.joinToString(", ")}"
        }
    }
}

private fun File.isInsideDirectory(parent: File): Boolean {
    val parentPath = parent.canonicalFile.path.trimEnd(File.separatorChar)
    val childPath = canonicalFile.path
    return childPath == parentPath || childPath.startsWith("$parentPath${File.separator}")
}
