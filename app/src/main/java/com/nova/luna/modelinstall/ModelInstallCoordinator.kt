package com.nova.luna.modelinstall

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ModelInstallCoordinator(
    val storage: PrivateAppModelStorage,
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    downloadSourceProviderOverride: ModelDownloadSourceProvider? = null,
    downloaderOverride: HttpModelDownloader? = null,
    verifierOverride: Sha256ModelVerifier? = null,
    registryOverride: LocalModelRegistry? = null,
    runtimeStateStoreOverride: ModelRuntimeStateStore? = null
) {
    val downloadSourceProvider: ModelDownloadSourceProvider =
        downloadSourceProviderOverride ?: ModelDownloadSourceProvider(catalog = catalog)

    val verifier: Sha256ModelVerifier = verifierOverride ?: Sha256ModelVerifier()

    val registry: LocalModelRegistry = registryOverride ?: LocalModelRegistry(storage)

    val runtimeStateStore: ModelRuntimeStateStore =
        runtimeStateStoreOverride ?: ModelRuntimeStateStore(storage)

    val downloader: HttpModelDownloader = downloaderOverride ?: HttpModelDownloader(
        storage = storage,
        stateStore = DownloadStateStore(storage),
        registry = registry,
        verifier = verifier
    )

    fun getInstallStatus(packId: ModelPackId): ModelInstallStatusSnapshot {
        val pack = packFor(packId)
        val runtimeState = runtimeStateStore.find(packId)
        val inspection = inspectPack(pack, runtimeState)
        val effectiveRuntimeState = inspection.runtimeState

        return buildSnapshot(
            pack = pack,
            runtimeState = effectiveRuntimeState,
            registryManifest = inspection.registryManifest,
            expectedFiles = inspection.expectedFiles,
            installedFiles = inspection.installedFiles,
            missingFiles = inspection.missingFiles,
            corruptFiles = inspection.corruptFiles
        )
    }

    fun startInstall(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot {
        val pack = packFor(packId)
        cleanPackStorage(packId)

        val sourceStates = downloadSourceProvider.sourcesFor(packId)
        runtimeStateStore.markDownloading(
            pack = pack,
            expectedFileCount = sourceStates.size,
            message = "Starting download."
        )
        onStateChanged(getInstallStatus(packId))

        val installedFiles = mutableListOf<ModelFileSpec>()
        var verifiedSourceCount = 0

        for ((index, source) in sourceStates.withIndex()) {
            if (cancelRequested()) {
                return finishCancelled(
                    pack = pack,
                    sourceCount = sourceStates.size,
                    verifiedSourceCount = verifiedSourceCount,
                    onStateChanged = onStateChanged,
                    message = "Install cancelled before source ${index + 1} started."
                )
            }

            runtimeStateStore.markDownloading(
                pack = pack,
                expectedFileCount = sourceStates.size,
                verifiedFileCount = verifiedSourceCount,
                message = "Downloading ${source.fileName}."
            )
            onStateChanged(getInstallStatus(packId))

            val downloadResult = downloader.download(
                source = source,
                cancelRequested = cancelRequested
            )

            when (downloadResult.status) {
                ModelDownloadStatus.SUCCESS -> {
                    val movedFile = moveDownloadedFileIntoPackLayout(pack, source)
                    val installedSpec = source.toInstalledFileSpec(movedFile.length())
                    installedFiles += installedSpec
                    verifiedSourceCount++
                }

                ModelDownloadStatus.CANCELLED -> {
                    return finishCancelled(
                        pack = pack,
                        sourceCount = sourceStates.size,
                        verifiedSourceCount = verifiedSourceCount,
                        onStateChanged = onStateChanged,
                        message = downloadResult.message ?: "Download cancelled."
                    )
                }

                ModelDownloadStatus.FAILED -> {
                    return if (isShaFailure(downloadResult.message)) {
                        finishCorrupt(
                            pack = pack,
                            sourceCount = sourceStates.size,
                            verifiedSourceCount = verifiedSourceCount,
                            onStateChanged = onStateChanged,
                            message = downloadResult.message ?: "SHA-256 verification failed."
                        )
                    } else {
                        finishFailure(
                            pack = pack,
                            sourceCount = sourceStates.size,
                            verifiedSourceCount = verifiedSourceCount,
                            onStateChanged = onStateChanged,
                            message = downloadResult.message ?: "Download failed."
                        )
                    }
                }

                ModelDownloadStatus.IDLE,
                ModelDownloadStatus.DOWNLOADING,
                ModelDownloadStatus.VERIFYING -> {
                    return finishFailure(
                        pack = pack,
                        sourceCount = sourceStates.size,
                        verifiedSourceCount = verifiedSourceCount,
                        onStateChanged = onStateChanged,
                        message = "Unexpected downloader state: ${downloadResult.status.name}"
                    )
                }
            }
        }

        if (cancelRequested()) {
            return finishCancelled(
                pack = pack,
                sourceCount = sourceStates.size,
                verifiedSourceCount = verifiedSourceCount,
                onStateChanged = onStateChanged,
                message = "Install cancelled before verification."
            )
        }

        runtimeStateStore.markVerifying(
            pack = pack,
            expectedFileCount = sourceStates.size,
            verifiedFileCount = verifiedSourceCount,
            message = "Verifying installed files."
        )
        onStateChanged(getInstallStatus(packId))

        val verification = verifyInstalledFiles(pack, installedFiles)
        if (verification.missingFiles.isNotEmpty()) {
            cleanPackStorage(packId)
            runtimeStateStore.markMissing(
                pack = pack,
                message = buildProblemMessage("Missing model file(s)", verification.missingFiles),
                expectedFileCount = sourceStates.size,
                verifiedFileCount = verification.verifiedFileKeys.size,
                registryConfirmed = false,
                missingFileCount = verification.missingFiles.size
            )
            return onStateChangedAndReturn(packId, onStateChanged)
        }

        if (verification.corruptFiles.isNotEmpty() || verification.hasUnverifiedFiles) {
            cleanPackStorage(packId)
            runtimeStateStore.markCorrupt(
                pack = pack,
                message = buildProblemMessage("SHA-256 verification failed", verification.corruptFiles),
                expectedFileCount = sourceStates.size,
                verifiedFileCount = verification.verifiedFileKeys.size,
                registryConfirmed = false,
                corruptFileCount = verification.corruptFiles.size.coerceAtLeast(1)
            )
            return onStateChangedAndReturn(packId, onStateChanged)
        }

        val readyManifest = pack.toReadyManifest(
            installedFiles = installedFiles,
            installedAtEpochMs = System.currentTimeMillis()
        )
        registry.upsert(readyManifest)
        runtimeStateStore.markReady(
            pack = pack,
            registryConfirmed = true,
            verificationPassed = true,
            expectedFileCount = sourceStates.size,
            verifiedFileCount = installedFiles.size,
            manifestPath = storage.manifestFile(pack.id).path,
            modelRootPath = storage.modelsDir(pack.id).path,
            message = "Install completed."
        )
        return onStateChangedAndReturn(packId, onStateChanged)
    }

    fun startInstallDownload(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot {
        return startInstall(packId, cancelRequested, onStateChanged)
    }

    fun retryFailedInstall(
        packId: ModelPackId,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot {
        val current = getInstallStatus(packId)
        return when (current.runtimeStatus) {
            ModelRuntimeStatus.READY -> current
            ModelRuntimeStatus.DOWNLOADING,
            ModelRuntimeStatus.VERIFYING -> current
            else -> startInstall(packId, cancelRequested, onStateChanged)
        }
    }

    fun detectReadyModel(packId: ModelPackId): Boolean {
        return getInstallStatus(packId).ready
    }

    fun detectMissingOrCorruptModel(packId: ModelPackId): Boolean {
        return getInstallStatus(packId).runtimeStatus in setOf(
            ModelRuntimeStatus.FAILED,
            ModelRuntimeStatus.CORRUPT,
            ModelRuntimeStatus.MISSING
        )
    }

    private fun onStateChangedAndReturn(
        packId: ModelPackId,
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit
    ): ModelInstallStatusSnapshot {
        val snapshot = getInstallStatus(packId)
        onStateChanged(snapshot)
        return snapshot
    }

    private fun finishCancelled(
        pack: ModelPackSpec,
        sourceCount: Int,
        verifiedSourceCount: Int,
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit,
        message: String
    ): ModelInstallStatusSnapshot {
        cleanPackStorage(pack.id)
        runtimeStateStore.markCancelled(
            pack = pack,
            message = message,
            expectedFileCount = sourceCount,
            verifiedFileCount = verifiedSourceCount
        )
        return onStateChangedAndReturn(pack.id, onStateChanged)
    }

    private fun finishFailure(
        pack: ModelPackSpec,
        sourceCount: Int,
        verifiedSourceCount: Int,
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit,
        message: String
    ): ModelInstallStatusSnapshot {
        cleanPackStorage(pack.id)
        runtimeStateStore.markFailed(
            pack = pack,
            message = message,
            expectedFileCount = sourceCount,
            verifiedFileCount = verifiedSourceCount
        )
        return onStateChangedAndReturn(pack.id, onStateChanged)
    }

    private fun finishCorrupt(
        pack: ModelPackSpec,
        sourceCount: Int,
        verifiedSourceCount: Int,
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit,
        message: String
    ): ModelInstallStatusSnapshot {
        cleanPackStorage(pack.id)
        runtimeStateStore.markCorrupt(
            pack = pack,
            message = message,
            expectedFileCount = sourceCount,
            verifiedFileCount = verifiedSourceCount
        )
        return onStateChangedAndReturn(pack.id, onStateChanged)
    }

    private fun packFor(packId: ModelPackId): ModelPackSpec {
        return catalog.firstOrNull { it.id == packId }
            ?: error("Unknown model pack: $packId")
    }

    private fun inspectPack(
        pack: ModelPackSpec,
        runtimeState: ModelRuntimeState?
    ): ModelPackInspection {
        val expectedFiles = pack.expectedFilesForStorage()
        val registryManifest = registry.find(pack.id)
        val currentRuntimeState = runtimeState
        val installedFiles = mutableListOf<String>()
        val verifiedFiles = mutableListOf<String>()
        val missingFiles = mutableListOf<String>()
        val corruptFiles = mutableListOf<String>()

        expectedFiles.forEach { expected ->
            val localKey = expected.storageFileKey(pack.id)
            val candidate = storage.packFile(pack.id, localKey)
            if (!candidate.exists()) {
                missingFiles += localKey
                return@forEach
            }

            installedFiles += localKey
            val expectedSha = expected.sha256?.trim().orEmpty()
            if (expectedSha.isBlank() || !verifier.verify(candidate, expectedSha)) {
                corruptFiles += localKey
            } else {
                verifiedFiles += localKey
            }
        }

        val registryConfirmed = registryManifest?.let { manifest ->
            manifest.packId == pack.id &&
                manifest.state == ModelInstallState.READY &&
                manifest.files.map { it.forPackStorage(pack.id) } == expectedFiles
        } ?: false

        val hasArtifacts = currentRuntimeState != null ||
            registryManifest != null ||
            installedFiles.isNotEmpty()

        val verificationPassed = expectedFiles.isNotEmpty() &&
            verifiedFiles.size == expectedFiles.size &&
            corruptFiles.isEmpty() &&
            missingFiles.isEmpty()

        val activeRuntimeStatus = currentRuntimeState?.runtimeStatus in setOf(
            ModelRuntimeStatus.DOWNLOADING,
            ModelRuntimeStatus.VERIFYING
        )
        val currentStatus = currentRuntimeState?.runtimeStatus

        val derivedStatus = when {
            currentStatus == ModelRuntimeStatus.READY &&
                currentRuntimeState?.isRuntimeReady() == true &&
                registryConfirmed &&
                verificationPassed -> ModelRuntimeStatus.READY

            activeRuntimeStatus -> currentStatus!!

            currentStatus == ModelRuntimeStatus.FAILED -> ModelRuntimeStatus.FAILED

            currentStatus == ModelRuntimeStatus.CANCELLED -> ModelRuntimeStatus.CANCELLED

            currentStatus == ModelRuntimeStatus.CORRUPT -> ModelRuntimeStatus.CORRUPT

            currentStatus == ModelRuntimeStatus.MISSING -> ModelRuntimeStatus.MISSING

            currentStatus == ModelRuntimeStatus.UNAVAILABLE -> ModelRuntimeStatus.UNAVAILABLE

            hasArtifacts && missingFiles.isNotEmpty() -> ModelRuntimeStatus.MISSING

            hasArtifacts && corruptFiles.isNotEmpty() -> ModelRuntimeStatus.CORRUPT

            currentStatus == ModelRuntimeStatus.READY &&
                (!registryConfirmed || !verificationPassed) ->
                if (corruptFiles.isNotEmpty()) ModelRuntimeStatus.CORRUPT else ModelRuntimeStatus.MISSING

            currentStatus == null -> {
                if (hasArtifacts) ModelRuntimeStatus.MISSING else ModelRuntimeStatus.IDLE
            }

            currentStatus == ModelRuntimeStatus.IDLE -> {
                if (hasArtifacts) ModelRuntimeStatus.MISSING else ModelRuntimeStatus.IDLE
            }

            currentStatus == ModelRuntimeStatus.READY -> {
                if (currentRuntimeState?.isRuntimeReady() == true && registryConfirmed && verificationPassed) {
                    ModelRuntimeStatus.READY
                } else {
                    ModelRuntimeStatus.UNAVAILABLE
                }
            }

            else -> currentStatus
                ?: if (hasArtifacts) ModelRuntimeStatus.MISSING else ModelRuntimeStatus.IDLE
        }

        val shouldPersistDerivedRepair = derivedStatus in setOf(
            ModelRuntimeStatus.MISSING,
            ModelRuntimeStatus.CORRUPT
        ) && (currentStatus == null ||
            currentStatus == ModelRuntimeStatus.IDLE ||
            currentStatus == ModelRuntimeStatus.READY)

        val baseRuntimeState = currentRuntimeState ?: ModelRuntimeState(
            packId = pack.id,
            version = pack.versionTag(),
            displayName = pack.displayName,
            runtimeStatus = ModelRuntimeStatus.IDLE,
            installState = ModelInstallState.NOT_INSTALLED,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFiles.size,
            verifiedFileCount = 0,
            missingFileCount = 0,
            corruptFileCount = 0,
            installedAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            manifestPath = null,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = null
        )

        val effectiveRuntimeState = baseRuntimeState.copy(
            runtimeStatus = derivedStatus,
            installState = derivedStatus.toInstallState(),
            registryConfirmed = registryConfirmed,
            verificationPassed = verificationPassed,
            ready = derivedStatus == ModelRuntimeStatus.READY &&
                registryConfirmed &&
                verificationPassed &&
                missingFiles.isEmpty() &&
                corruptFiles.isEmpty(),
            expectedFileCount = expectedFiles.size,
            verifiedFileCount = verifiedFiles.size,
            missingFileCount = missingFiles.size,
            corruptFileCount = corruptFiles.size,
            manifestPath = storage.manifestFile(pack.id).path,
            modelRootPath = baseRuntimeState.modelRootPath ?: File(storage.modelsRootDir, pack.id.wireValue).path
        ).normalized()

        if (shouldPersistDerivedRepair) {
            when (derivedStatus) {
                ModelRuntimeStatus.MISSING -> runtimeStateStore.markMissing(
                    pack = pack,
                    message = buildProblemMessage("Missing model file(s)", missingFiles),
                    expectedFileCount = expectedFiles.size,
                    verifiedFileCount = verifiedFiles.size,
                    registryConfirmed = registryConfirmed,
                    missingFileCount = missingFiles.size
                )

                ModelRuntimeStatus.CORRUPT -> runtimeStateStore.markCorrupt(
                    pack = pack,
                    message = buildProblemMessage("SHA-256 verification failed", corruptFiles),
                    expectedFileCount = expectedFiles.size,
                    verifiedFileCount = verifiedFiles.size,
                    registryConfirmed = registryConfirmed,
                    missingFileCount = missingFiles.size,
                    corruptFileCount = corruptFiles.size.coerceAtLeast(1)
                )

                else -> Unit
            }
        }

        return ModelPackInspection(
            runtimeState = effectiveRuntimeState,
            registryManifest = registryManifest,
            expectedFiles = expectedFiles,
            installedFiles = installedFiles,
            missingFiles = missingFiles,
            corruptFiles = corruptFiles
        )
    }

    private fun buildSnapshot(
        pack: ModelPackSpec,
        runtimeState: ModelRuntimeState,
        registryManifest: ModelManifest?,
        expectedFiles: List<ModelFileSpec>,
        installedFiles: List<String>,
        missingFiles: List<String>,
        corruptFiles: List<String>
    ): ModelInstallStatusSnapshot {
        return ModelInstallStatusSnapshot(
            packId = pack.id,
            displayName = pack.displayName,
            runtimeState = runtimeState.normalized(),
            registryManifest = registryManifest,
            expectedFileKeys = expectedFiles.map { it.storageFileKey(pack.id) },
            installedFileKeys = installedFiles,
            missingFileKeys = missingFiles,
            corruptFileKeys = corruptFiles
        )
    }

    private fun verifyInstalledFiles(
        pack: ModelPackSpec,
        installedFiles: List<ModelFileSpec>
    ): PackVerificationResult {
        val missingFiles = mutableListOf<String>()
        val corruptFiles = mutableListOf<String>()
        val verifiedFiles = mutableListOf<String>()

        installedFiles.forEach { fileSpec ->
            val key = fileSpec.storageFileKey(pack.id)
            val file = storage.packFile(pack.id, key)
            if (!file.exists()) {
                missingFiles += key
                return@forEach
            }

            val expectedSha = fileSpec.sha256?.trim().orEmpty()
            if (expectedSha.isBlank() || !verifier.verify(file, expectedSha)) {
                corruptFiles += key
            } else {
                verifiedFiles += key
            }
        }

        return PackVerificationResult(
            verifiedFileKeys = verifiedFiles,
            missingFiles = missingFiles,
            corruptFiles = corruptFiles,
            hasUnverifiedFiles = verifiedFiles.size != installedFiles.size
        )
    }

    private fun moveDownloadedFileIntoPackLayout(
        pack: ModelPackSpec,
        source: ModelDownloadSource
    ): File {
        val stagedFile = storage.packFile(pack.id, source.fileName)
        val targetKey = source.storageFileKey()
        val targetFile = storage.packFile(pack.id, targetKey)

        targetFile.parentFile?.mkdirs()
        if (stagedFile.canonicalPath == targetFile.canonicalPath) {
            return targetFile
        }

        try {
            Files.move(
                stagedFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                stagedFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        return targetFile
    }

    private fun cleanPackStorage(packId: ModelPackId) {
        storage.downloadsDir(packId).deleteRecursively()
        storage.modelsDir(packId).deleteRecursively()
        registry.remove(packId)
    }

    private fun buildProblemMessage(prefix: String, fileKeys: List<String>): String {
        return if (fileKeys.isEmpty()) {
            prefix
        } else {
            "$prefix: ${fileKeys.joinToString(", ")}"
        }
    }

    private fun isShaFailure(message: String?): Boolean {
        val normalized = message?.lowercase().orEmpty()
        return normalized.contains("sha-256") ||
            normalized.contains("verification failed") ||
            normalized.contains("expected sha-256")
    }

    private data class ModelPackInspection(
        val runtimeState: ModelRuntimeState,
        val registryManifest: ModelManifest?,
        val expectedFiles: List<ModelFileSpec>,
        val installedFiles: List<String>,
        val missingFiles: List<String>,
        val corruptFiles: List<String>
    )

    private data class PackVerificationResult(
        val verifiedFileKeys: List<String>,
        val missingFiles: List<String>,
        val corruptFiles: List<String>,
        val hasUnverifiedFiles: Boolean
    )
}
