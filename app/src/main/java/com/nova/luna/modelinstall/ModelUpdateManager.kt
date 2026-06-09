package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelRole
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ModelUpdateManager(
    private val coordinator: ModelInstallCoordinator,
    private val healthScanner: ModelHealthScanner = ModelHealthScanner(coordinator),
    private val cleanupPolicy: ModelCleanupPolicy = ModelCleanupPolicy(coordinator)
) {
    fun updateModel(
        packId: ModelPackId,
        force: Boolean = false,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        val pack = coordinator.packSpec(packId)
        val currentScan = healthScanner.scan(packId)
        val availableVersion = pack.versionTag()

        if (!force && currentScan.ready && currentScan.installedVersion == availableVersion) {
            return currentScan
        }

        val stagingRoot = cleanupPolicy.stagingRoot(packId, availableVersion)
        cleanupPolicy.deleteIfSafe(stagingRoot)
        val tempStorage = PrivateAppModelStorage.from(stagingRoot)
        val tempRegistry = LocalModelRegistry(tempStorage)
        val tempStateStore = ModelRuntimeStateStore(tempStorage)
        val tempDownloadStateStore = DownloadStateStore(tempStorage)
        val tempDownloader = coordinator.downloader.fork(
            storage = tempStorage,
            stateStore = tempDownloadStateStore,
            registry = tempRegistry
        )
        val tempCoordinator = ModelInstallCoordinator(
            storage = tempStorage,
            catalog = listOf(pack),
            downloadSourceProviderOverride = ModelDownloadSourceProvider(
                catalog = listOf(pack)
            ),
            downloaderOverride = tempDownloader,
            registryOverride = tempRegistry,
            runtimeStateStoreOverride = tempStateStore
        )
        val tempScanner = ModelHealthScanner(tempCoordinator)

        val sourceStates = coordinator.downloadSourceProvider.sourcesFor(packId)
        for ((index, source) in sourceStates.withIndex()) {
            if (cancelRequested()) {
                cleanupPolicy.deleteIfSafe(tempStorage.rootDir)
                return currentScan
            }

            val downloadResult = tempDownloader.download(
                source = source,
                cancelRequested = cancelRequested
            )

            when (downloadResult.status) {
                ModelDownloadStatus.SUCCESS -> Unit

                ModelDownloadStatus.CANCELLED -> {
                    cleanupPolicy.deleteIfSafe(tempStorage.rootDir)
                    return currentScan
                }

                ModelDownloadStatus.FAILED -> {
                    cleanupPolicy.deleteIfSafe(tempStorage.rootDir)
                    return currentScan
                }

                else -> {
                    cleanupPolicy.deleteIfSafe(tempStorage.rootDir)
                    return currentScan
                }
            }

            if (index == sourceStates.lastIndex) {
                // no-op, the loop is intentionally explicit so cancellation can
                // be checked between files when the pack has multiple blobs.
            }
        }

        val installedFiles = pack.files.map { fileSpec ->
            val installedSpec = fileSpec.forPackStorage(pack.id)
            val storageKey = installedSpec.storageFileKey(pack.id)
            val file = tempStorage.packFile(pack.id, storageKey)
            installedSpec.copy(byteCount = file.length())
        }

        tempRegistry.upsert(
            pack.toReadyManifest(
                installedFiles = installedFiles,
                installedAtEpochMs = System.currentTimeMillis()
            )
        )
        tempStateStore.markReady(
            pack = pack,
            registryConfirmed = true,
            verificationPassed = true,
            expectedFileCount = sourceStates.size,
            verifiedFileCount = installedFiles.size,
            manifestPath = tempStorage.manifestFile(pack.id).path,
            modelRootPath = tempStorage.modelsDir(pack.id).path,
            message = "Update staged successfully."
        )

        val stagedScan = tempScanner.scan(packId)
        if (!stagedScan.ready) {
            cleanupPolicy.deleteIfSafe(tempStorage.rootDir)
            return currentScan
        }

        val activeModelDir = File(coordinator.storage.modelsRootDir, packId.wireValue)
        val backupDir = cleanupPolicy.backupRoot(
            packId = packId,
            version = currentScan.installedVersion.ifBlank { "previous" }
        )

        try {
            if (activeModelDir.exists()) {
                cleanupPolicy.deleteIfSafe(backupDir)
                moveDirectory(activeModelDir, backupDir)
            }

            moveDirectory(tempStorage.modelsDir(packId), activeModelDir)

            val committedFiles = pack.files.map { fileSpec ->
                val normalized = fileSpec.forPackStorage(pack.id)
                val storageKey = normalized.storageFileKey(pack.id)
                normalized.copy(byteCount = File(activeModelDir, storageKey).length())
            }

            coordinator.registry.upsert(
                pack.toReadyManifest(
                    installedFiles = committedFiles,
                    installedAtEpochMs = System.currentTimeMillis()
                )
            )
            coordinator.runtimeStateStore.markReady(
                pack = pack,
                registryConfirmed = true,
                verificationPassed = true,
                expectedFileCount = committedFiles.size,
                verifiedFileCount = committedFiles.size,
                manifestPath = coordinator.storage.manifestFile(pack.id).path,
                modelRootPath = coordinator.storage.modelsDir(pack.id).path,
                message = "Update completed successfully."
            )
        } catch (throwable: Throwable) {
            if (activeModelDir.exists()) {
                cleanupPolicy.deleteIfSafe(activeModelDir)
            }
            if (backupDir.exists()) {
                restoreDirectory(backupDir, activeModelDir)
            }
            coordinator.runtimeStateStore.markUnavailable(
                pack = pack,
                message = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Model update failed for ${pack.displayName}.",
                expectedFileCount = sourceStates.size,
                verifiedFileCount = currentScan.installedFileKeys.size,
                missingFileCount = currentScan.missingFileKeys.size,
                corruptFileCount = currentScan.corruptFileKeys.size
            )
            cleanupPolicy.deleteIfSafe(tempStorage.rootDir)
            return healthScanner.scan(packId)
        }

        cleanupPolicy.cleanupInactiveVersions(packId, availableVersion)
        return healthScanner.scan(packId)
    }

    fun updateModel(
        role: BrainModelRole,
        force: Boolean = false,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        val packId = role.toModelPackIdOrNull()
            ?: return currentUnavailable(role)

        return updateModel(packId, force, cancelRequested)
    }

    private fun currentUnavailable(role: BrainModelRole): ModelHealthScanResult {
        return ModelHealthScanResult(
            packId = ModelPackId.CORE,
            displayName = role.name,
            runtimeStatus = ModelRuntimeStatus.UNAVAILABLE,
            installState = ModelInstallState.FAILED,
            ready = false,
            registryConfirmed = false,
            verificationPassed = false,
            runtimeLoaded = false,
            healthCheckPassed = false,
            installedVersion = "",
            availableVersion = "",
            reason = "Unsupported role: ${role.wireValue}.",
            expectedFileKeys = emptyList(),
            installedFileKeys = emptyList(),
            missingFileKeys = emptyList(),
            corruptFileKeys = emptyList(),
            userFacingStatus = ModelUserFacingStatus.create(
                packId = ModelPackId.CORE,
                displayName = role.name,
                runtimeStatus = ModelRuntimeStatus.UNAVAILABLE,
                installState = ModelInstallState.FAILED,
                ready = false,
                message = "Unsupported role."
            )
        )
    }

    private fun moveDirectory(source: File, target: File) {
        if (!source.exists()) return
        target.parentFile?.mkdirs()
        if (target.exists()) {
            target.deleteRecursively()
        }

        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun restoreDirectory(source: File, target: File) {
        if (!source.exists()) return
        target.parentFile?.mkdirs()
        if (target.exists()) {
            target.deleteRecursively()
        }

        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
