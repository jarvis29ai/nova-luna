package com.nova.luna.modelinstall

class ModelDownloadSourceProvider(
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    private val baseDownloadUrl: String? = null
) {
    fun sourcesFor(packId: ModelPackId): List<ModelDownloadSource> {
        val pack = catalog.firstOrNull { it.id == packId }
            ?: error("Unknown model pack: $packId")

        return pack.files.mapIndexed { index, file ->
            val normalized = file.normalized()
            val sourceId = "${pack.id.wireValue}-${index + 1}-${normalized.fileName}"
            ModelDownloadSource(
                packId = pack.id,
                packDisplayName = pack.displayName,
                sourceId = sourceId,
                fileName = normalized.fileName,
                relativePath = normalized.relativePath,
                downloadUrl = buildDownloadUrl(normalized),
                expectedSha256 = normalized.sha256,
                expectedByteCount = normalized.byteCount,
                notes = pack.notes
            ).normalized()
        }
    }

    fun sourceFor(packId: ModelPackId): ModelDownloadSource? {
        return sourcesFor(packId).firstOrNull()
    }

    private fun buildDownloadUrl(file: ModelFileSpec): String? {
        val base = baseDownloadUrl?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val path = buildString {
            if (file.relativePath.isNotBlank()) {
                append(file.relativePath.trim('/'))
                append('/')
            }
            append(file.fileName)
        }
        return "$base/$path"
    }
}
