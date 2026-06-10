package com.nova.luna.modelinstall

data class LocalRuntimeReadiness(
    val packId: ModelPackId,
    val status: LocalRuntimeReadinessStatus,
    val reason: String,
    val selection: ModelPackSelection? = null,
    val installStatus: ModelInstallStatusSnapshot,
    val loadResult: LocalModelLoadResult,
    val runtimeLoadResult: LocalRuntimeLoadResult? = null,
    val loadedModelRef: LoadedModelRef? = null,
    val healthCheckResult: RuntimeHealthCheckResult? = null,
    val modelFileKeys: List<String> = emptyList()
) {
    val ready: Boolean
        get() = status == LocalRuntimeReadinessStatus.READY

    val unavailable: Boolean
        get() = !ready
}

enum class LocalRuntimeReadinessStatus {
    NOT_READY,
    MISSING,
    CORRUPT,
    REFUSED,
    FAILED,
    UNAVAILABLE,
    READY
}

class LocalRuntimeReadinessChecker(
    private val storage: PrivateAppModelStorage,
    private val coordinator: ModelInstallCoordinator = ModelInstallCoordinator(storage),
    private val capabilityChecker: DeviceCapabilityChecker = DeviceCapabilityChecker(),
    private val loader: LocalModelLoader = DefaultLocalModelLoader(storage, coordinator),
    private val runtimeLoader: LocalRuntimeLoader = LocalRuntimeLoader()
) {
    private val runtimeStateStore: ModelRuntimeStateStore
        get() = coordinator.runtimeStateStore

    fun select(snapshot: DeviceCapabilitySnapshot): ModelPackSelection {
        return capabilityChecker.select(snapshot)
    }

    fun inspect(snapshot: DeviceCapabilitySnapshot): LocalRuntimeReadiness {
        val selection = select(snapshot)
        return inspect(selection.packId, selection)
    }

    fun inspect(packId: ModelPackId): LocalRuntimeReadiness {
        return inspect(packId, null)
    }

    fun installStatus(packId: ModelPackId): ModelInstallStatusSnapshot {
        return coordinator.getInstallStatus(packId)
    }

    fun installReady(packId: ModelPackId): Boolean {
        return installStatus(packId).ready
    }

    fun load(packId: ModelPackId): LocalModelLoadResult {
        return loader.load(packId)
    }

    fun loadRuntime(packId: ModelPackId): LocalRuntimeLoadResult {
        return runtimeLoader.load(load(packId))
    }

    private fun inspect(
        packId: ModelPackId,
        selection: ModelPackSelection?
    ): LocalRuntimeReadiness {
        val installStatus = coordinator.getInstallStatus(packId)
        val loadResult = loader.load(packId, selection)
        val runtimeLoadResult = runtimeLoader.load(loadResult)
        val status = mapRuntimeStatus(loadResult, runtimeLoadResult)

        persistRuntimeOutcome(
            installStatus = installStatus,
            loadResult = loadResult,
            runtimeLoadResult = runtimeLoadResult
        )

        val effectiveInstallStatus = coordinator.getInstallStatus(packId)

        return LocalRuntimeReadiness(
            packId = packId,
            status = status,
            reason = runtimeLoadResult.reason,
            selection = selection,
            installStatus = effectiveInstallStatus,
            loadResult = loadResult,
            runtimeLoadResult = runtimeLoadResult,
            loadedModelRef = runtimeLoadResult.loadedModelRef,
            healthCheckResult = runtimeLoadResult.healthCheckResult,
            modelFileKeys = effectiveInstallStatus.expectedFileKeys
        )
    }

    private fun mapRuntimeStatus(
        loadResult: LocalModelLoadResult,
        runtimeLoadResult: LocalRuntimeLoadResult
    ): LocalRuntimeReadinessStatus {
        return when {
            runtimeLoadResult.ready -> LocalRuntimeReadinessStatus.READY
            runtimeLoadResult.status == LocalRuntimeLoadStatus.MISSING -> LocalRuntimeReadinessStatus.MISSING
            runtimeLoadResult.status == LocalRuntimeLoadStatus.CORRUPT -> LocalRuntimeReadinessStatus.CORRUPT
            runtimeLoadResult.status == LocalRuntimeLoadStatus.REFUSED -> LocalRuntimeReadinessStatus.REFUSED
            runtimeLoadResult.status == LocalRuntimeLoadStatus.NOT_READY -> LocalRuntimeReadinessStatus.NOT_READY
            runtimeLoadResult.status == LocalRuntimeLoadStatus.UNAVAILABLE ||
                runtimeLoadResult.status == LocalRuntimeLoadStatus.FAILED -> LocalRuntimeReadinessStatus.UNAVAILABLE
            loadResult.status == LocalModelLoadStatus.NOT_READY -> LocalRuntimeReadinessStatus.NOT_READY
            loadResult.status == LocalModelLoadStatus.MISSING -> LocalRuntimeReadinessStatus.MISSING
            loadResult.status == LocalModelLoadStatus.CORRUPT -> LocalRuntimeReadinessStatus.CORRUPT
            loadResult.status == LocalModelLoadStatus.REFUSED -> LocalRuntimeReadinessStatus.REFUSED
            loadResult.status == LocalModelLoadStatus.FAILED -> LocalRuntimeReadinessStatus.UNAVAILABLE
            else -> LocalRuntimeReadinessStatus.UNAVAILABLE
        }
    }

    private fun persistRuntimeOutcome(
        installStatus: ModelInstallStatusSnapshot,
        loadResult: LocalModelLoadResult,
        runtimeLoadResult: LocalRuntimeLoadResult
    ) {
        val current = runtimeStateStore.find(installStatus.packId) ?: installStatus.runtimeState
        val now = System.currentTimeMillis()

        val updated = when (runtimeLoadResult.status) {
            LocalRuntimeLoadStatus.READY -> {
                current.copy(
                    runtimeStatus = ModelRuntimeStatus.READY,
                    installState = ModelInstallState.READY,
                    registryConfirmed = installStatus.registryConfirmed,
                    verificationPassed = installStatus.verificationPassed,
                    runtimeLoaded = true,
                    healthCheckPassed = true,
                    healthCheckPrompt = runtimeLoadResult.healthCheckResult?.prompt,
                    healthCheckResponse = runtimeLoadResult.healthCheckResult?.response,
                    healthCheckReason = null,
                    ready = true,
                    expectedFileCount = installStatus.expectedFileKeys.size,
                    verifiedFileCount = loadResult.modelFiles.size,
                    missingFileCount = 0,
                    corruptFileCount = 0,
                    loadedModelPath = runtimeLoadResult.loadedModelRef?.modelRootPath
                        ?: current.loadedModelPath,
                    modelRootPath = runtimeLoadResult.loadedModelRef?.modelRootPath
                        ?: current.modelRootPath,
                    manifestPath = installStatus.runtimeState.manifestPath,
                    lastRuntimeLoadEpochMs = now,
                    lastHealthCheckEpochMs = now,
                    message = runtimeLoadResult.reason
                )
            }

            LocalRuntimeLoadStatus.UNAVAILABLE,
            LocalRuntimeLoadStatus.FAILED -> {
                current.copy(
                    runtimeStatus = ModelRuntimeStatus.UNAVAILABLE,
                    installState = ModelInstallState.FAILED,
                    registryConfirmed = installStatus.registryConfirmed,
                    verificationPassed = installStatus.verificationPassed,
                    runtimeLoaded = runtimeLoadResult.loadedModel != null,
                    healthCheckPassed = false,
                    healthCheckPrompt = runtimeLoadResult.healthCheckResult?.prompt,
                    healthCheckResponse = runtimeLoadResult.healthCheckResult?.response,
                    healthCheckReason = runtimeLoadResult.reason,
                    ready = false,
                    expectedFileCount = installStatus.expectedFileKeys.size,
                    verifiedFileCount = loadResult.modelFiles.size,
                    missingFileCount = installStatus.missingFileKeys.size,
                    corruptFileCount = installStatus.corruptFileKeys.size,
                    loadedModelPath = runtimeLoadResult.loadedModelRef?.modelRootPath
                        ?: current.loadedModelPath,
                    modelRootPath = runtimeLoadResult.loadedModelRef?.modelRootPath
                        ?: current.modelRootPath,
                    manifestPath = installStatus.runtimeState.manifestPath,
                    lastRuntimeLoadEpochMs = now,
                    lastHealthCheckEpochMs = now,
                    message = runtimeLoadResult.reason
                )
            }

            else -> return
        }.normalized()

        runtimeStateStore.upsert(updated)
    }
}
