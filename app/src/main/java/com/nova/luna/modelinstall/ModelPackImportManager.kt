package com.nova.luna.modelinstall

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ModelImportResult(
    val packId: ModelPackId,
    val displayName: String,
    val installStatus: ModelInstallStatusSnapshot,
    val sourcePaths: List<String> = emptyList(),
    val importedFileKeys: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val message: String = ""
) {
    val ready: Boolean
        get() = installStatus.ready

    val success: Boolean
        get() = ready
}

class ModelPackImportManager(
    private val storage: PrivateAppModelStorage,
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    private val registry: LocalModelRegistry = LocalModelRegistry(storage),
    private val runtimeStateStore: ModelRuntimeStateStore = ModelRuntimeStateStore(storage),
    private val verifier: Sha256ModelVerifier = Sha256ModelVerifier(),
    private val coordinatorOverride: ModelInstallCoordinator? = null
) {
    private val coordinator: ModelInstallCoordinator = coordinatorOverride ?: ModelInstallCoordinator(
        storage = storage,
        catalog = catalog,
        registryOverride = registry,
        runtimeStateStoreOverride = runtimeStateStore
    )

    fun importPack(
        packId: ModelPackId,
        sourceDir: File
    ): ModelImportResult {
        val pack = packFor(packId)
        val previousStatus = coordinator.getInstallStatus(packId)
        val preservePreviousReadyState = previousStatus.ready
        val stagingDir = storage.downloadsDir(packId)
        val backupDir = importBackupDir(packId)

        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        runtimeStateStore.markDownloading(
            pack = pack,
            message = "Importing local model file."
        )

        val sourcePaths = mutableListOf<String>()
        val importedFileKeys = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val installedFiles = mutableListOf<ModelFileSpec>()

        for (expected in pack.expectedFilesForStorage()) {
            val sourceFile = resolveSourceFile(sourceDir, expected, pack.id)
                ?: return finishMissing(
                    pack = pack,
                    previousStatus = previousStatus,
                    preservePreviousReadyState = preservePreviousReadyState,
                    sourcePaths = sourcePaths,
                    importedFileKeys = importedFileKeys,
                    warnings = warnings,
                    message = "Source file not found for ${expected.fileName}."
                )

            sourcePaths += sourceFile.canonicalPath

            val expectedSha = expected.sha256?.trim().orEmpty()
            if (expectedSha.isBlank()) {
                return finishFailed(
                    pack = pack,
                    previousStatus = previousStatus,
                    preservePreviousReadyState = preservePreviousReadyState,
                    sourcePaths = sourcePaths,
                    importedFileKeys = importedFileKeys,
                    warnings = warnings,
                    message = "HASH_NOT_CONFIGURED"
                )
            }

            val importedKey = expected.storageFileKey(pack.id)
            val stagedFile = stagingFile(pack.id, importedKey)
            stagedFile.parentFile?.mkdirs()

            Files.copy(
                sourceFile.toPath(),
                stagedFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            if (!verifier.verify(stagedFile, expectedSha)) {
                cleanupStaging(stagingDir)
                return completeFailure(
                    pack = pack,
                    previousStatus = previousStatus,
                    preservePreviousReadyState = preservePreviousReadyState,
                    sourcePaths = sourcePaths,
                    importedFileKeys = importedFileKeys,
                    warnings = warnings,
                    failureState = ModelRuntimeStatus.CORRUPT,
                    message = "SHA-256 verification failed for ${expected.fileName}."
                )
            }

            val actualByteCount = stagedFile.length()
            if (expected.byteCount != null && expected.byteCount > 0L && expected.byteCount != actualByteCount) {
                warnings += buildString {
                    append(expected.fileName)
                    append(" detected size ")
                    append(actualByteCount)
                    append(" bytes differs from catalog size ")
                    append(expected.byteCount)
                    append(". Using detected size for the imported pack.")
                }
            }

            importedFileKeys += importedKey
            installedFiles += expected.copy(byteCount = actualByteCount, sha256 = expectedSha)
        }

        val modelsDir = storage.modelsDir(pack.id)
        val hadExistingModel = modelsDir.exists()

        if (hadExistingModel) {
            backupDir.deleteRecursively()
            moveDirectory(modelsDir, backupDir)
        }

        try {
            moveDirectory(stagingDir, modelsDir)

            val readyManifest = pack.toReadyManifest(
                installedFiles = installedFiles,
                installedAtEpochMs = System.currentTimeMillis()
            )
            registry.upsert(readyManifest)
            runtimeStateStore.markReady(
                pack = pack,
                registryConfirmed = true,
                verificationPassed = true,
                expectedFileCount = pack.files.size,
                verifiedFileCount = installedFiles.size,
                manifestPath = storage.manifestFile(pack.id).path,
                modelRootPath = modelsDir.path,
                message = "Import completed successfully."
            )

            cleanupIfExists(backupDir)
            return buildResult(
                pack = pack,
                sourcePaths = sourcePaths,
                importedFileKeys = importedFileKeys,
                warnings = warnings,
                message = "Import completed successfully."
            )
        } catch (throwable: Throwable) {
            if (modelsDir.exists()) {
                modelsDir.deleteRecursively()
            }
            if (backupDir.exists()) {
                restoreDirectory(backupDir, modelsDir)
            }

            cleanupStaging(stagingDir)
            return completeFailure(
                pack = pack,
                previousStatus = previousStatus,
                preservePreviousReadyState = preservePreviousReadyState,
                sourcePaths = sourcePaths,
                importedFileKeys = importedFileKeys,
                warnings = warnings,
                failureState = ModelRuntimeStatus.FAILED,
                message = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Import failed for ${pack.displayName}."
            )
        } finally {
            cleanupStaging(stagingDir)
            cleanupIfExists(backupDir)
        }
    }

    fun importAvailablePacks(sourceDir: File): List<ModelImportResult> {
        return catalog.map { pack ->
            importPack(pack.id, sourceDir)
        }
    }

    private fun buildResult(
        pack: ModelPackSpec,
        sourcePaths: List<String>,
        importedFileKeys: List<String>,
        warnings: List<String>,
        message: String
    ): ModelImportResult {
        return ModelImportResult(
            packId = pack.id,
            displayName = pack.displayName,
            installStatus = coordinator.getInstallStatus(pack.id),
            sourcePaths = sourcePaths,
            importedFileKeys = importedFileKeys,
            warnings = warnings.distinct(),
            message = message
        )
    }

    private fun finishMissing(
        pack: ModelPackSpec,
        previousStatus: ModelInstallStatusSnapshot,
        preservePreviousReadyState: Boolean,
        sourcePaths: List<String>,
        importedFileKeys: List<String>,
        warnings: List<String>,
        message: String
    ): ModelImportResult {
        cleanupStaging(stagingDir = storage.downloadsDir(pack.id))
        return completeFailure(
            pack = pack,
            previousStatus = previousStatus,
            preservePreviousReadyState = preservePreviousReadyState,
            sourcePaths = sourcePaths,
            importedFileKeys = importedFileKeys,
            warnings = warnings,
            failureState = ModelRuntimeStatus.MISSING,
            message = message
        )
    }

    private fun finishFailed(
        pack: ModelPackSpec,
        previousStatus: ModelInstallStatusSnapshot,
        preservePreviousReadyState: Boolean,
        sourcePaths: List<String>,
        importedFileKeys: List<String>,
        warnings: List<String>,
        message: String
    ): ModelImportResult {
        cleanupStaging(stagingDir = storage.downloadsDir(pack.id))
        return completeFailure(
            pack = pack,
            previousStatus = previousStatus,
            preservePreviousReadyState = preservePreviousReadyState,
            sourcePaths = sourcePaths,
            importedFileKeys = importedFileKeys,
            warnings = warnings,
            failureState = ModelRuntimeStatus.FAILED,
            message = message
        )
    }

    private fun completeFailure(
        pack: ModelPackSpec,
        previousStatus: ModelInstallStatusSnapshot,
        preservePreviousReadyState: Boolean,
        sourcePaths: List<String>,
        importedFileKeys: List<String>,
        warnings: List<String>,
        failureState: ModelRuntimeStatus,
        message: String
    ): ModelImportResult {
        if (preservePreviousReadyState) {
            runtimeStateStore.upsert(previousStatus.runtimeState)
        } else {
            when (failureState) {
                ModelRuntimeStatus.CORRUPT -> runtimeStateStore.markCorrupt(
                    pack = pack,
                    message = message,
                    expectedFileCount = pack.files.size,
                    verifiedFileCount = importedFileKeys.size,
                    missingFileCount = 0,
                    corruptFileCount = 1
                )

                ModelRuntimeStatus.MISSING -> runtimeStateStore.markMissing(
                    pack = pack,
                    message = message,
                    expectedFileCount = pack.files.size,
                    verifiedFileCount = importedFileKeys.size,
                    registryConfirmed = false,
                    missingFileCount = 1
                )

                else -> runtimeStateStore.markFailed(
                    pack = pack,
                    message = message,
                    expectedFileCount = pack.files.size,
                    verifiedFileCount = importedFileKeys.size
                )
            }
        }

        return buildResult(
            pack = pack,
            sourcePaths = sourcePaths,
            importedFileKeys = importedFileKeys,
            warnings = warnings,
            message = message
        )
    }

    private fun resolveSourceFile(
        sourceDir: File,
        expected: ModelFileSpec,
        packId: ModelPackId
    ): File? {
        val candidates = buildList {
            add(File(sourceDir, expected.fileName))

            val relativePath = expected.relativePath.trim().trim('/')
            if (relativePath.isNotBlank()) {
                add(File(sourceDir, "$relativePath/${expected.fileName}"))
            }

            add(File(sourceDir, "${packId.wireValue}/${expected.fileName}"))
            add(File(sourceDir, expected.storageFileKey(packId)))
        }

        return candidates.firstOrNull { candidate ->
            candidate.exists() && candidate.isFile
        }?.canonicalFile
    }

    private fun packFor(packId: ModelPackId): ModelPackSpec {
        return coordinator.packSpec(packId)
    }

    private fun cleanupStaging(stagingDir: File) {
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
    }

    private fun cleanupIfExists(path: File) {
        if (path.exists()) {
            path.deleteRecursively()
        }
    }

    private fun importBackupDir(packId: ModelPackId): File {
        return File(storage.rootDir, "model_import_backups/${packId.wireValue}")
    }

    private fun stagingFile(packId: ModelPackId, relativeFileKey: String): File {
        return File(storage.downloadsDir(packId), relativeFileKey.trim())
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
