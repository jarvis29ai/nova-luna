package com.nova.luna.modelinstall

class ModelHealthScanner(
    private val coordinator: ModelInstallCoordinator,
    private val readinessChecker: LocalRuntimeReadinessChecker = LocalRuntimeReadinessChecker(
        storage = coordinator.storage,
        coordinator = coordinator
    )
) {
    fun scan(packId: ModelPackId): ModelHealthScanResult {
        val availablePack = coordinator.packSpec(packId)
        val runtimeReadiness = readinessChecker.inspect(packId)
        val effectiveInstallStatus = runtimeReadiness.installStatus

        val runtimeState = effectiveInstallStatus.runtimeState
        val ready = effectiveInstallStatus.ready && runtimeReadiness.ready
        val reason = runtimeReadiness.reason.takeIf { it.isNotBlank() }
            ?: runtimeState.message
            ?: "Model ${effectiveInstallStatus.displayName} is not ready."

        return ModelHealthScanResult(
            packId = packId,
            displayName = effectiveInstallStatus.displayName,
            runtimeStatus = runtimeState.runtimeStatus,
            installState = runtimeState.installState,
            ready = ready,
            registryConfirmed = effectiveInstallStatus.registryConfirmed,
            verificationPassed = effectiveInstallStatus.verificationPassed,
            runtimeLoaded = runtimeState.runtimeLoaded,
            healthCheckPassed = runtimeState.healthCheckPassed,
            installedVersion = runtimeState.version,
            availableVersion = availablePack.versionTag(),
            reason = reason,
            expectedFileKeys = effectiveInstallStatus.expectedFileKeys,
            installedFileKeys = effectiveInstallStatus.installedFileKeys,
            missingFileKeys = effectiveInstallStatus.missingFileKeys,
            corruptFileKeys = effectiveInstallStatus.corruptFileKeys,
            userFacingStatus = ModelUserFacingStatus.create(
                packId = packId,
                displayName = effectiveInstallStatus.displayName,
                runtimeStatus = runtimeState.runtimeStatus,
                installState = runtimeState.installState,
                ready = ready,
                message = reason,
                canUpdate = ready && availablePack.versionTag() != runtimeState.version
            )
        )
    }

    fun scan(): List<ModelHealthScanResult> {
        return coordinator.catalog.map { pack -> scan(pack.id) }
    }

    fun userSafeState(packId: ModelPackId): ModelUserFacingStatus {
        return scan(packId).userFacingStatus
    }
}
