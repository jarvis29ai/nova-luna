package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelRole

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

    fun scanHealth(packId: ModelPackId): ModelHealthScanResult {
        return coordinator.scanHealth(packId)
    }

    fun scanHealth(): List<ModelHealthScanResult> {
        return coordinator.scanHealth()
    }

    fun getUserSafeState(packId: ModelPackId): ModelUserFacingStatus {
        return coordinator.getUserSafeState(packId)
    }

    fun repairModel(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        return coordinator.repairModel(packId, cancelRequested)
    }

    fun repairModel(
        role: BrainModelRole,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        return coordinator.repairModel(role, cancelRequested)
    }

    fun updateModel(
        packId: ModelPackId,
        force: Boolean = false,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        return coordinator.updateModel(packId, force, cancelRequested)
    }

    fun updateModel(
        role: BrainModelRole,
        force: Boolean = false,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        return coordinator.updateModel(role, force, cancelRequested)
    }

    fun deletePack(packId: ModelPackId): ModelCleanupResult {
        return coordinator.deletePack(packId)
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

    fun loadRuntime(packId: ModelPackId): LocalRuntimeLoadResult {
        return readinessChecker.loadRuntime(packId)
    }
}
