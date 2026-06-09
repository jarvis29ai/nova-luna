package com.nova.luna.modelinstall

import java.io.File

class ModelCleanupPolicy(
    private val coordinator: ModelInstallCoordinator
) {
    private val storage: PrivateAppModelStorage
        get() = coordinator.storage

    fun maintenanceRoot(packId: ModelPackId): File {
        return File(storage.rootDir, "maintenance/${packId.wireValue}")
    }

    fun stagingRoot(packId: ModelPackId, version: String): File {
        return File(maintenanceRoot(packId), "staging/${version.trim()}")
    }

    fun backupRoot(packId: ModelPackId, version: String): File {
        return File(maintenanceRoot(packId), "backups/${version.trim()}")
    }

    fun versionsRoot(packId: ModelPackId): File {
        return File(storage.modelsDir(packId), "versions")
    }

    fun deleteIfSafe(path: File): Boolean {
        val canonicalRoot = storage.rootDir.canonicalFile
        val canonicalPath = path.canonicalFile
        if (!canonicalPath.isInsideDirectory(canonicalRoot)) {
            return false
        }

        if (!canonicalPath.exists()) {
            return true
        }

        return canonicalPath.deleteRecursively()
    }

    fun cleanupInactiveVersions(
        packId: ModelPackId,
        activeVersion: String? = null
    ): ModelCleanupResult {
        val deleted = mutableListOf<String>()
        val refused = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val packVersionsRoot = versionsRoot(packId)
        val packMaintenanceRoot = maintenanceRoot(packId)
        val active = activeVersion?.trim()?.takeIf { it.isNotBlank() }

        listOf(packVersionsRoot, packMaintenanceRoot).forEach { root ->
            if (!root.exists()) return@forEach
            root.listFiles()?.forEach { child ->
                val childVersion = child.name.trim()
                val shouldKeep = active != null &&
                    childVersion.equals(active, ignoreCase = true)
                if (shouldKeep) {
                    skipped += child.canonicalPath
                    return@forEach
                }

                if (deleteIfSafe(child)) {
                    deleted += child.canonicalPath
                } else {
                    refused += child.canonicalPath
                }
            }
        }

        return ModelCleanupResult(
            packId = packId,
            deletedPaths = deleted,
            refusedPaths = refused,
            skippedPaths = skipped,
            message = if (refused.isEmpty()) {
                "Cleanup completed for ${packId.displayName}."
            } else {
                "Cleanup completed with refused paths for ${packId.displayName}."
            },
            success = refused.isEmpty()
        )
    }

    fun cleanupAllInactiveVersions(activeVersions: Map<ModelPackId, String?> = emptyMap()): ModelCleanupResult {
        val deleted = mutableListOf<String>()
        val refused = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        coordinator.catalog.forEach { pack ->
            val result = cleanupInactiveVersions(pack.id, activeVersions[pack.id])
            deleted += result.deletedPaths
            refused += result.refusedPaths
            skipped += result.skippedPaths
        }

        return ModelCleanupResult(
            deletedPaths = deleted,
            refusedPaths = refused,
            skippedPaths = skipped,
            message = "Cleanup completed for all packs.",
            success = refused.isEmpty()
        )
    }

    fun deletePack(packId: ModelPackId): ModelCleanupResult {
        val deleted = mutableListOf<String>()
        val refused = mutableListOf<String>()

        listOf(
            storage.downloadsDir(packId),
            storage.modelsDir(packId),
            maintenanceRoot(packId)
        ).forEach { path ->
            if (deleteIfSafe(path)) {
                deleted += path.canonicalPath
            } else {
                refused += path.canonicalPath
            }
        }

        coordinator.registry.remove(packId)
        coordinator.runtimeStateStore.remove(packId)
        coordinator.downloadStateStore.remove(packId, sourceId = packId.wireValue)
        coordinator.downloadStateStore.snapshot()
            .filter { it.packId == packId }
            .forEach { coordinator.downloadStateStore.remove(packId, it.sourceId) }

        return ModelCleanupResult(
            packId = packId,
            deletedPaths = deleted,
            refusedPaths = refused,
            message = if (refused.isEmpty()) {
                "Removed ${packId.displayName}."
            } else {
                "Removed ${packId.displayName} with refused paths."
            },
            success = refused.isEmpty()
        )
    }
}

private fun File.isInsideDirectory(parent: File): Boolean {
    val parentPath = parent.canonicalFile.path.trimEnd(File.separatorChar)
    val childPath = canonicalFile.path
    return childPath == parentPath || childPath.startsWith("$parentPath${File.separator}")
}
