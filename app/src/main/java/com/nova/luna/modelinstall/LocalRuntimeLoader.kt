package com.nova.luna.modelinstall

enum class LocalRuntimeLoadStatus {
    NOT_READY,
    MISSING,
    CORRUPT,
    REFUSED,
    UNAVAILABLE,
    FAILED,
    READY
}

data class LocalRuntimeLoadResult(
    val packId: ModelPackId,
    val status: LocalRuntimeLoadStatus,
    val reason: String,
    val installStatus: ModelInstallStatusSnapshot? = null,
    val fileLoadResult: LocalModelLoadResult? = null,
    val loadedModelRef: LoadedModelRef? = null,
    val loadedModel: LoadedModel? = null,
    val healthCheckResult: RuntimeHealthCheckResult? = null,
    val selection: ModelPackSelection? = null
) {
    val ready: Boolean
        get() = status == LocalRuntimeLoadStatus.READY

    val unavailable: Boolean
        get() = !ready
}

class LocalRuntimeLoader(
    private val runtime: BrainModelRuntime = NoOpBrainModelRuntime,
    private val healthPrompt: String = DEFAULT_HEALTH_PROMPT
) {
    fun load(
        fileLoadResult: LocalModelLoadResult,
        prompt: String = healthPrompt
    ): LocalRuntimeLoadResult {
        val installStatus = fileLoadResult.installStatus
        val selection = fileLoadResult.selection

        if (!fileLoadResult.ready) {
            return LocalRuntimeLoadResult(
                packId = fileLoadResult.packId,
                status = mapFileLoadStatus(fileLoadResult.status),
                reason = fileLoadResult.reason,
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                selection = selection
            )
        }

        val modelRef = LoadedModelRef.fromLoadResult(fileLoadResult)
            ?: return LocalRuntimeLoadResult(
                packId = fileLoadResult.packId,
                status = LocalRuntimeLoadStatus.FAILED,
                reason = "Unable to resolve a private runtime model reference.",
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                selection = selection
            )

        return load(
            modelRef = modelRef,
            installStatus = installStatus,
            fileLoadResult = fileLoadResult,
            selection = selection,
            prompt = prompt
        )
    }

    fun load(
        modelRef: LoadedModelRef,
        prompt: String = healthPrompt
    ): LocalRuntimeLoadResult {
        return load(
            modelRef = modelRef,
            installStatus = null,
            fileLoadResult = null,
            selection = modelRef.selection,
            prompt = prompt
        )
    }

    private fun load(
        modelRef: LoadedModelRef,
        installStatus: ModelInstallStatusSnapshot?,
        fileLoadResult: LocalModelLoadResult?,
        selection: ModelPackSelection?,
        prompt: String
    ): LocalRuntimeLoadResult {
        if (!modelRef.isPrivateStorage()) {
            return LocalRuntimeLoadResult(
                packId = modelRef.packId,
                status = LocalRuntimeLoadStatus.REFUSED,
                reason = "Model files must stay inside private app storage.",
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                loadedModelRef = modelRef,
                selection = selection
            )
        }

        if (modelRef.modelFiles.isEmpty()) {
            return LocalRuntimeLoadResult(
                packId = modelRef.packId,
                status = LocalRuntimeLoadStatus.NOT_READY,
                reason = "No model files were provided for ${modelRef.displayName}.",
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                loadedModelRef = modelRef,
                selection = selection
            )
        }

        val missingFile = modelRef.modelFiles.firstOrNull { !it.exists() || !it.isFile }
        if (missingFile != null) {
            return LocalRuntimeLoadResult(
                packId = modelRef.packId,
                status = LocalRuntimeLoadStatus.MISSING,
                reason = "Verified model file is missing: ${missingFile.path}.",
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                loadedModelRef = modelRef,
                selection = selection
            )
        }

        return try {
            val loadedModel = runtime.load(modelRef)
                ?: return LocalRuntimeLoadResult(
                    packId = modelRef.packId,
                    status = LocalRuntimeLoadStatus.UNAVAILABLE,
                    reason = "Local runtime backend refused to load ${modelRef.displayName}.",
                    installStatus = installStatus,
                    fileLoadResult = fileLoadResult,
                    loadedModelRef = modelRef,
                    selection = selection
                )

            val healthCheckResult = runtime.healthCheck(loadedModel, prompt)
            if (!healthCheckResult.passed) {
                return LocalRuntimeLoadResult(
                    packId = modelRef.packId,
                    status = LocalRuntimeLoadStatus.UNAVAILABLE,
                    reason = healthCheckResult.reason
                        ?: "Local runtime health check failed for ${modelRef.displayName}.",
                    installStatus = installStatus,
                    fileLoadResult = fileLoadResult,
                    loadedModelRef = modelRef,
                    loadedModel = loadedModel,
                    healthCheckResult = healthCheckResult,
                    selection = selection
                )
            }

            LocalRuntimeLoadResult(
                packId = modelRef.packId,
                status = LocalRuntimeLoadStatus.READY,
                reason = "Local runtime is ready for ${modelRef.displayName}.",
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                loadedModelRef = modelRef,
                loadedModel = loadedModel,
                healthCheckResult = healthCheckResult,
                selection = selection
            )
        } catch (throwable: Throwable) {
            LocalRuntimeLoadResult(
                packId = modelRef.packId,
                status = LocalRuntimeLoadStatus.FAILED,
                reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Local runtime load failed for ${modelRef.displayName}.",
                installStatus = installStatus,
                fileLoadResult = fileLoadResult,
                loadedModelRef = modelRef,
                selection = selection
            )
        }
    }

    private fun mapFileLoadStatus(status: LocalModelLoadStatus): LocalRuntimeLoadStatus {
        return when (status) {
            LocalModelLoadStatus.NOT_READY -> LocalRuntimeLoadStatus.NOT_READY
            LocalModelLoadStatus.MISSING -> LocalRuntimeLoadStatus.MISSING
            LocalModelLoadStatus.CORRUPT -> LocalRuntimeLoadStatus.CORRUPT
            LocalModelLoadStatus.REFUSED -> LocalRuntimeLoadStatus.REFUSED
            LocalModelLoadStatus.FAILED -> LocalRuntimeLoadStatus.UNAVAILABLE
            LocalModelLoadStatus.READY -> LocalRuntimeLoadStatus.READY
        }
    }

    companion object {
        const val DEFAULT_HEALTH_PROMPT = "ping"
    }
}
