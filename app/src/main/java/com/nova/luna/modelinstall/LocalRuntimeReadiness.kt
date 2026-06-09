package com.nova.luna.modelinstall

data class LocalRuntimeReadiness(
    val packId: ModelPackId,
    val status: LocalRuntimeReadinessStatus,
    val reason: String,
    val selection: ModelPackSelection? = null,
    val installStatus: ModelInstallStatusSnapshot,
    val loadResult: LocalModelLoadResult,
    val modelFileKeys: List<String> = emptyList()
) {
    val ready: Boolean
        get() = status == LocalRuntimeReadinessStatus.READY
}

enum class LocalRuntimeReadinessStatus {
    NOT_READY,
    MISSING,
    CORRUPT,
    REFUSED,
    FAILED,
    READY
}

class LocalRuntimeReadinessChecker(
    private val storage: PrivateAppModelStorage,
    private val coordinator: ModelInstallCoordinator = ModelInstallCoordinator(storage),
    private val capabilityChecker: DeviceCapabilityChecker = DeviceCapabilityChecker(),
    private val loader: LocalModelLoader = DefaultLocalModelLoader(storage, coordinator)
) {
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

    fun load(packId: ModelPackId): LocalModelLoadResult {
        return loader.load(packId)
    }

    private fun inspect(
        packId: ModelPackId,
        selection: ModelPackSelection?
    ): LocalRuntimeReadiness {
        val installStatus = coordinator.getInstallStatus(packId)
        val loadResult = loader.load(packId, selection)
        val status = when {
            loadResult.ready -> LocalRuntimeReadinessStatus.READY
            loadResult.status == LocalModelLoadStatus.MISSING -> LocalRuntimeReadinessStatus.MISSING
            loadResult.status == LocalModelLoadStatus.CORRUPT -> LocalRuntimeReadinessStatus.CORRUPT
            loadResult.status == LocalModelLoadStatus.REFUSED -> LocalRuntimeReadinessStatus.REFUSED
            loadResult.status == LocalModelLoadStatus.FAILED -> LocalRuntimeReadinessStatus.FAILED
            else -> LocalRuntimeReadinessStatus.NOT_READY
        }

        return LocalRuntimeReadiness(
            packId = packId,
            status = status,
            reason = loadResult.reason,
            selection = selection,
            installStatus = installStatus,
            loadResult = loadResult,
            modelFileKeys = installStatus.expectedFileKeys
        )
    }
}
