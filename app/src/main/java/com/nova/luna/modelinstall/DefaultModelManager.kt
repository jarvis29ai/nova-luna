package com.nova.luna.modelinstall

class DefaultModelManager(
    val coordinator: ModelInstallCoordinator
) {
    constructor(storage: PrivateAppModelStorage) : this(ModelInstallCoordinator(storage))

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
}
