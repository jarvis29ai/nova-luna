package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelRole
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ModelRepairManager(
    private val coordinator: ModelInstallCoordinator,
    private val updateManager: ModelUpdateManager = ModelUpdateManager(coordinator),
    private val healthScanner: ModelHealthScanner = ModelHealthScanner(coordinator),
    private val cleanupPolicy: ModelCleanupPolicy = ModelCleanupPolicy(coordinator)
) {
    fun repairModel(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        val pack = coordinator.packSpec(packId)
        val currentScan = healthScanner.scan(packId)
        val backupDir = cleanupPolicy.backupRoot(
            packId = packId,
            version = currentScan.installedVersion.ifBlank { "previous" }
        )
        val activeModelDir = File(coordinator.storage.modelsRootDir, packId.wireValue)

        coordinator.runtimeStateStore.markUnavailable(
            pack = pack,
            message = "Repairing AI brain.",
            expectedFileCount = currentScan.expectedFileKeys.size,
            verifiedFileCount = currentScan.installedFileKeys.size,
            missingFileCount = currentScan.missingFileKeys.size,
            corruptFileCount = currentScan.corruptFileKeys.size
        )

        if (activeModelDir.exists()) {
            cleanupPolicy.deleteIfSafe(backupDir)
            moveDirectory(activeModelDir, backupDir)
        }

        cleanupPolicy.deleteIfSafe(coordinator.storage.downloadsDir(packId))

        val updated = try {
            updateManager.updateModel(
                packId = packId,
                force = true,
                cancelRequested = cancelRequested
            )
        } catch (throwable: Throwable) {
            restoreBackupIfNeeded(backupDir, activeModelDir)
            coordinator.runtimeStateStore.markUnavailable(
                pack = pack,
                message = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Repair failed for ${pack.displayName}.",
                expectedFileCount = currentScan.expectedFileKeys.size,
                verifiedFileCount = currentScan.installedFileKeys.size,
                missingFileCount = currentScan.missingFileKeys.size,
                corruptFileCount = currentScan.corruptFileKeys.size
            )
            return healthScanner.scan(packId)
        }

        return if (updated.ready) {
            updated
        } else {
            restoreBackupIfNeeded(backupDir, activeModelDir)
            healthScanner.scan(packId)
        }
    }

    fun repairModel(
        role: BrainModelRole,
        cancelRequested: () -> Boolean = { false }
    ): ModelHealthScanResult {
        val packId = role.toModelPackIdOrNull()
            ?: return updateManager.updateModel(role, force = true, cancelRequested = cancelRequested)
        return repairModel(packId, cancelRequested)
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

    private fun restoreBackupIfNeeded(source: File, target: File) {
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
