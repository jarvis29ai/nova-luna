package com.nova.luna.modelinstall

enum class ModelDownloadStatus {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class ModelDownloadSource(
    val packId: ModelPackId,
    val packDisplayName: String = packId.displayName,
    val sourceId: String,
    val fileName: String,
    val relativePath: String = "",
    val downloadUrl: String? = null,
    val expectedSha256: String? = null,
    val expectedByteCount: Long? = null,
    val notes: List<String> = emptyList()
) {
    fun normalized(): ModelDownloadSource {
        return copy(
            packDisplayName = packDisplayName.trim(),
            sourceId = sourceId.trim(),
            fileName = fileName.trim(),
            relativePath = relativePath.trim().trim('/'),
            downloadUrl = downloadUrl?.trim()?.takeIf { it.isNotBlank() },
            expectedSha256 = expectedSha256?.trim()?.takeIf { it.isNotBlank() },
            expectedByteCount = expectedByteCount?.coerceAtLeast(0L),
            notes = notes.map { it.trim() }.filter { it.isNotBlank() }
        )
    }

    fun configurationProblems(): List<String> {
        return buildList {
            if (downloadUrl.isNullOrBlank()) add("download URL")
            if (expectedSha256.isNullOrBlank()) add("SHA-256")
            if (expectedByteCount == null || expectedByteCount <= 0L) add("expected byte size")
        }
    }

    fun isConfigured(): Boolean {
        return configurationProblems().isEmpty()
    }

    val fileKey: String
        get() = if (relativePath.isBlank()) fileName else "$relativePath/$fileName"
}

data class ModelDownloadState(
    val packId: ModelPackId,
    val packDisplayName: String = packId.displayName,
    val sourceId: String,
    val fileName: String,
    val relativePath: String = "",
    val downloadUrl: String? = null,
    val expectedSha256: String? = null,
    val expectedByteCount: Long? = null,
    val status: ModelDownloadStatus = ModelDownloadStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val stagedFilePath: String? = null,
    val finalFilePath: String? = null,
    val message: String? = null,
    val updatedAtEpochMs: Long = 0L
) {
    fun normalized(): ModelDownloadState {
        return copy(
            packDisplayName = packDisplayName.trim(),
            sourceId = sourceId.trim(),
            fileName = fileName.trim(),
            relativePath = relativePath.trim().trim('/'),
            downloadUrl = downloadUrl?.trim()?.takeIf { it.isNotBlank() },
            expectedSha256 = expectedSha256?.trim()?.takeIf { it.isNotBlank() },
            expectedByteCount = expectedByteCount?.coerceAtLeast(0L),
            bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
            totalBytes = totalBytes?.coerceAtLeast(0L),
            stagedFilePath = stagedFilePath?.trim()?.takeIf { it.isNotBlank() },
            finalFilePath = finalFilePath?.trim()?.takeIf { it.isNotBlank() },
            message = message?.trim()?.takeIf { it.isNotBlank() },
            updatedAtEpochMs = updatedAtEpochMs.coerceAtLeast(0L)
        )
    }

    val fileKey: String
        get() = if (relativePath.isBlank()) fileName else "$relativePath/$fileName"

    val isTerminal: Boolean
        get() = status == ModelDownloadStatus.SUCCESS ||
            status == ModelDownloadStatus.FAILED ||
            status == ModelDownloadStatus.CANCELLED

    fun toJsonValue(): Map<String, Any?> {
        return linkedMapOf(
            "packId" to packId.wireValue,
            "packDisplayName" to packDisplayName,
            "sourceId" to sourceId,
            "fileName" to fileName,
            "relativePath" to relativePath.takeIf { it.isNotBlank() },
            "downloadUrl" to downloadUrl,
            "expectedSha256" to expectedSha256,
            "expectedByteCount" to expectedByteCount,
            "status" to status.name,
            "bytesDownloaded" to bytesDownloaded,
            "totalBytes" to totalBytes,
            "stagedFilePath" to stagedFilePath,
            "finalFilePath" to finalFilePath,
            "message" to message,
            "updatedAtEpochMs" to updatedAtEpochMs
        ).filterValues { value -> value != null }
    }

    companion object {
        fun fromSource(
            source: ModelDownloadSource,
            status: ModelDownloadStatus = ModelDownloadStatus.IDLE,
            bytesDownloaded: Long = 0L,
            totalBytes: Long? = null,
            stagedFilePath: String? = null,
            finalFilePath: String? = null,
            message: String? = null,
            updatedAtEpochMs: Long = System.currentTimeMillis()
        ): ModelDownloadState {
            val normalizedSource = source.normalized()
            return ModelDownloadState(
                packId = normalizedSource.packId,
                packDisplayName = normalizedSource.packDisplayName,
                sourceId = normalizedSource.sourceId,
                fileName = normalizedSource.fileName,
                relativePath = normalizedSource.relativePath,
                downloadUrl = normalizedSource.downloadUrl,
                expectedSha256 = normalizedSource.expectedSha256,
                expectedByteCount = normalizedSource.expectedByteCount,
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                stagedFilePath = stagedFilePath,
                finalFilePath = finalFilePath,
                message = message,
                updatedAtEpochMs = updatedAtEpochMs
            ).normalized()
        }

        fun fromJsonMap(json: Map<String, Any?>): ModelDownloadState {
            val packIdValue = json.jsonString("packId")
            val packId = ModelPackId.fromWireValue(packIdValue)
                ?: error("Unknown pack id: $packIdValue")

            val status = runCatching {
                ModelDownloadStatus.valueOf(json.jsonString("status", ModelDownloadStatus.IDLE.name))
            }.getOrDefault(ModelDownloadStatus.IDLE)

            return ModelDownloadState(
                packId = packId,
                packDisplayName = json.jsonString("packDisplayName", packId.displayName),
                sourceId = json.jsonString("sourceId"),
                fileName = json.jsonString("fileName"),
                relativePath = json.jsonString("relativePath", ""),
                downloadUrl = json.jsonStringOrNull("downloadUrl"),
                expectedSha256 = json.jsonStringOrNull("expectedSha256"),
                expectedByteCount = json.jsonLongOrNull("expectedByteCount"),
                status = status,
                bytesDownloaded = json.jsonLongOrNull("bytesDownloaded") ?: 0L,
                totalBytes = json.jsonLongOrNull("totalBytes"),
                stagedFilePath = json.jsonStringOrNull("stagedFilePath"),
                finalFilePath = json.jsonStringOrNull("finalFilePath"),
                message = json.jsonStringOrNull("message"),
                updatedAtEpochMs = json.jsonLongOrNull("updatedAtEpochMs") ?: 0L
            ).normalized()
        }
    }
}
