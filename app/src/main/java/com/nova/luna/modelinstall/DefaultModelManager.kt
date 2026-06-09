package com.nova.luna.modelinstall

class DefaultModelManager(
    val coordinator: ModelInstallCoordinator,
    private val capabilityChecker: DeviceCapabilityChecker = DeviceCapabilityChecker(),
    private val runtimeReadinessChecker: LocalRuntimeReadinessChecker? = null
) {
    constructor(storage: PrivateAppModelStorage) : this(ModelInstallCoordinator(storage))

    private val readinessChecker: LocalRuntimeReadinessChecker =
        runtimeReadinessChecker ?: LocalRuntimeReadinessChecker(
            storage = coordinator.storage,
            coordinator = coordinator,
            capabilityChecker = capabilityChecker
        )

    fun getInstallStatus(packId: ModelPackId): ModelInstallStatusSnapshot {
        return coordinator.getInstallStatus(packId)
    }

    fun startInstall(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot {
        return coordinator.startInstall(packId, cancelRequested, onStateChanged)
    }

    fun startInstallDownload(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot {
        return coordinator.startInstallDownload(packId, cancelRequested, onStateChanged)
    }

    fun retryFailedInstall(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot {
        return coordinator.retryFailedInstall(packId, cancelRequested, onStateChanged)
    }

    fun detectReadyModel(packId: ModelPackId): Boolean {
        return coordinator.detectReadyModel(packId)
    }

    fun detectMissingOrCorruptModel(packId: ModelPackId): Boolean {
        return coordinator.detectMissingOrCorruptModel(packId)
    }

    fun selectRecommendedPack(snapshot: DeviceCapabilitySnapshot): ModelPackSelection {
        return capabilityChecker.select(snapshot)
    }

    fun inspectRuntime(snapshot: DeviceCapabilitySnapshot): LocalRuntimeReadiness {
        return readinessChecker.inspect(snapshot)
    }

    fun inspectRuntime(packId: ModelPackId): LocalRuntimeReadiness {
        return readinessChecker.inspect(packId)
    }

    fun loadRuntime(packId: ModelPackId): LocalModelLoadResult {
        return readinessChecker.load(packId)
    }
}
