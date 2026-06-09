package com.nova.luna.modelinstall

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DownloadStateStore(
    private val storage: PrivateAppModelStorage
) {
    private val stateFile: File = File(storage.rootDir, "download_state.json")
    private val lock = Any()

    fun snapshot(): List<ModelDownloadState> {
        return synchronized(lock) { readFromDisk() }
    }

    fun find(packId: ModelPackId, sourceId: String): ModelDownloadState? {
        return snapshot().firstOrNull {
            it.packId == packId && it.sourceId.equals(sourceId, ignoreCase = true)
        }
    }

    fun latest(packId: ModelPackId): ModelDownloadState? {
        return snapshot()
            .filter { it.packId == packId }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    fun upsert(state: ModelDownloadState) {
        synchronized(lock) {
            val normalized = state.normalized()
            val updated = readFromDisk()
                .filterNot {
                    it.packId == normalized.packId &&
                        it.sourceId.equals(normalized.sourceId, ignoreCase = true)
                }
                .plus(normalized)
                .sortedWith(compareBy<ModelDownloadState> { it.packId.priority }.thenBy { it.sourceId })
            writeToDisk(updated)
        }
    }

    fun remove(packId: ModelPackId, sourceId: String) {
        synchronized(lock) {
            val updated = readFromDisk().filterNot {
                it.packId == packId && it.sourceId.equals(sourceId, ignoreCase = true)
            }
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

    fun markDownloading(
        source: ModelDownloadSource,
        bytesDownloaded: Long = 0L,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        return updateFromSource(
            source = source,
            status = ModelDownloadStatus.DOWNLOADING,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
    }

    fun markVerifying(
        source: ModelDownloadSource,
        bytesDownloaded: Long = 0L,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        return updateFromSource(
            source = source,
            status = ModelDownloadStatus.VERIFYING,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
    }

    fun markSuccess(
        source: ModelDownloadSource,
        bytesDownloaded: Long,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        return updateFromSource(
            source = source,
            status = ModelDownloadStatus.SUCCESS,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
    }

    fun markFailed(
        source: ModelDownloadSource,
        bytesDownloaded: Long = 0L,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        return updateFromSource(
            source = source,
            status = ModelDownloadStatus.FAILED,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
    }

    fun markCancelled(
        source: ModelDownloadSource,
        bytesDownloaded: Long = 0L,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        return updateFromSource(
            source = source,
            status = ModelDownloadStatus.CANCELLED,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
    }

    private fun updateFromSource(
        source: ModelDownloadSource,
        status: ModelDownloadStatus,
        bytesDownloaded: Long = 0L,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        val state = ModelDownloadState.fromSource(
            source = source,
            status = status,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
        upsert(state)
        return state
    }

    private fun readFromDisk(): List<ModelDownloadState> {
        if (!stateFile.exists()) return emptyList()

        val text = stateFile.readText().trim()
        if (text.isBlank()) return emptyList()

        val root = SimpleJson.parseObject(text)
        val states = root.jsonArray("states")
        return buildList {
            for (item in states) {
                @Suppress("UNCHECKED_CAST")
                add(ModelDownloadState.fromJsonMap(item as? Map<String, Any?>
                    ?: error("Expected download state object")))
            }
        }
    }

    private fun writeToDisk(states: List<ModelDownloadState>) {
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
