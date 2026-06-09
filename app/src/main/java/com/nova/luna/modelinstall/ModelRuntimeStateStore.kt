package com.nova.luna.modelinstall

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ModelRuntimeStateStore(
    private val storage: PrivateAppModelStorage
) {
    val stateFile: File = File(storage.rootDir, "runtime_state.json")

    private val lock = Any()

    fun snapshot(): List<ModelRuntimeState> {
        return synchronized(lock) { readFromDisk() }
    }

    fun find(packId: ModelPackId): ModelRuntimeState? {
        return snapshot().firstOrNull { it.packId == packId }
    }

    fun upsert(state: ModelRuntimeState): ModelRuntimeState {
        synchronized(lock) {
            val normalized = state.normalized()
            val updated = readFromDisk()
                .filterNot { it.packId == normalized.packId }
                .plus(normalized)
                .sortedBy { it.packId.priority }
            writeToDisk(updated)
            return normalized
        }
    }

    fun remove(packId: ModelPackId) {
        synchronized(lock) {
            val updated = readFromDisk().filterNot { it.packId == packId }
            writeToDisk(updated)
        }
    }

    fun clear() {
        synchronized(lock) {
            if (stateFile.exists()) {
                stateFile.delete()
            }
        }
    }

    fun markIdle(
        pack: ModelPackSpec,
        message: String? = null
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.IDLE,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = pack.files.size,
            verifiedFileCount = 0,
            missingFileCount = 0,
            corruptFileCount = 0,
            manifestPath = null,
            modelRootPath = null,
            message = message
        )
    }

    fun markDownloading(
        pack: ModelPackSpec,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = 0,
        message: String? = null
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.DOWNLOADING,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = 0,
            corruptFileCount = 0,
            manifestPath = null,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    fun markVerifying(
        pack: ModelPackSpec,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int,
        message: String? = null
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.VERIFYING,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = 0,
            corruptFileCount = 0,
            manifestPath = null,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    fun markReady(
        pack: ModelPackSpec,
        registryConfirmed: Boolean = true,
        verificationPassed: Boolean = true,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = expectedFileCount,
        manifestPath: String? = storage.manifestFile(pack.id).path,
        modelRootPath: String? = storage.modelsDir(pack.id).path,
        message: String? = null,
        installedAtEpochMs: Long = System.currentTimeMillis()
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.READY,
            registryConfirmed = registryConfirmed,
            verificationPassed = verificationPassed,
            ready = true,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = 0,
            corruptFileCount = 0,
            installedAtEpochMs = installedAtEpochMs,
            manifestPath = manifestPath,
            modelRootPath = modelRootPath ?: File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    fun markFailed(
        pack: ModelPackSpec,
        message: String? = null,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = 0,
        missingFileCount: Int = 0,
        corruptFileCount: Int = 0
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.FAILED,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = missingFileCount,
            corruptFileCount = corruptFileCount,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    fun markUnavailable(
        pack: ModelPackSpec,
        message: String? = null,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = 0,
        missingFileCount: Int = 0,
        corruptFileCount: Int = 0
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.UNAVAILABLE,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = missingFileCount,
            corruptFileCount = corruptFileCount,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    fun markCorrupt(
        pack: ModelPackSpec,
        message: String? = null,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = 0,
        registryConfirmed: Boolean = false,
        missingFileCount: Int = 0,
        corruptFileCount: Int = 1
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.CORRUPT,
            registryConfirmed = registryConfirmed,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = missingFileCount,
            corruptFileCount = corruptFileCount,
            modelRootPath = storage.modelsDir(pack.id).path,
            message = message
        )
    }

    fun markMissing(
        pack: ModelPackSpec,
        message: String? = null,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = 0,
        registryConfirmed: Boolean = false,
        missingFileCount: Int = 1
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.MISSING,
            registryConfirmed = registryConfirmed,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = missingFileCount,
            corruptFileCount = 0,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    fun markCancelled(
        pack: ModelPackSpec,
        message: String? = null,
        expectedFileCount: Int = pack.files.size,
        verifiedFileCount: Int = 0
    ): ModelRuntimeState {
        return writeState(
            pack = pack,
            runtimeStatus = ModelRuntimeStatus.CANCELLED,
            registryConfirmed = false,
            verificationPassed = false,
            ready = false,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = 0,
            corruptFileCount = 0,
            modelRootPath = File(storage.modelsRootDir, pack.id.wireValue).path,
            message = message
        )
    }

    private fun writeState(
        pack: ModelPackSpec,
        runtimeStatus: ModelRuntimeStatus,
        registryConfirmed: Boolean,
        verificationPassed: Boolean,
        ready: Boolean,
        expectedFileCount: Int,
        verifiedFileCount: Int,
        missingFileCount: Int,
        corruptFileCount: Int,
        installedAtEpochMs: Long = 0L,
        manifestPath: String? = null,
        modelRootPath: String? = null,
        message: String? = null
    ): ModelRuntimeState {
        val state = ModelRuntimeState(
            packId = pack.id,
            version = pack.versionTag(),
            displayName = pack.displayName,
            runtimeStatus = runtimeStatus,
            installState = runtimeStatus.toInstallState(),
            registryConfirmed = registryConfirmed,
            verificationPassed = verificationPassed,
            ready = ready,
            expectedFileCount = expectedFileCount,
            verifiedFileCount = verifiedFileCount,
            missingFileCount = missingFileCount,
            corruptFileCount = corruptFileCount,
            installedAtEpochMs = installedAtEpochMs,
            updatedAtEpochMs = System.currentTimeMillis(),
            manifestPath = manifestPath,
            modelRootPath = modelRootPath,
            message = message
        ).normalized()
        return upsert(state)
    }

    private fun readFromDisk(): List<ModelRuntimeState> {
        if (!stateFile.exists()) return emptyList()

        val text = stateFile.readText().trim()
        if (text.isBlank()) return emptyList()

        val root = SimpleJson.parseObject(text)
        val states = root.jsonArray("states")
        return buildList {
            for (item in states) {
                @Suppress("UNCHECKED_CAST")
                add(ModelRuntimeState.fromJsonMap(item as? Map<String, Any?>
                    ?: error("Expected runtime state object")))
            }
        }
    }

    private fun writeToDisk(states: List<ModelRuntimeState>) {
        stateFile.parentFile?.mkdirs()
        val root = linkedMapOf(
            "version" to 1,
            "states" to states.map { it.toJsonValue() }
        )

        val tempFile = File(stateFile.parentFile, "${stateFile.name}.tmp")
        tempFile.writeText(SimpleJson.stringify(root, indentSpaces = 2))

        try {
            Files.move(
                tempFile.toPath(),
                stateFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                tempFile.toPath(),
                stateFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
