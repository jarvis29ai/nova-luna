package com.nova.luna.modelinstall

internal const val MODEL_SOURCE_NOT_CONFIGURED_MESSAGE =
    "Model source not configured. Add a signed model pack source before download."

class ModelDownloadSourceProvider(
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    private val baseDownloadUrl: String? = null
) {
    fun packSpec(packId: ModelPackId): ModelPackSpec {
        return catalog.firstOrNull { it.id == packId }
            ?: error("Unknown model pack: $packId")
    }

    fun availableVersion(packId: ModelPackId): String {
        return packSpec(packId).versionTag()
    }

    fun sourcesFor(packId: ModelPackId): List<ModelDownloadSource> {
        val pack = packSpec(packId)

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

    fun isConfigured(packId: ModelPackId): Boolean {
        val sources = sourcesFor(packId)
        if (sources.isEmpty()) {
            return false
        }

        return sources.none { it.downloadUrl.isNullOrBlank() || it.expectedSha256.isNullOrBlank() }
    }

    fun configurationMessage(packId: ModelPackId): String {
        val sources = sourcesFor(packId)
        if (sources.isEmpty()) {
            return MODEL_SOURCE_NOT_CONFIGURED_MESSAGE
        }

        val hasMissingUrl = sources.any { it.downloadUrl.isNullOrBlank() }
        val hasMissingSha = sources.any { it.expectedSha256.isNullOrBlank() }
        return when {
            hasMissingUrl || hasMissingSha -> MODEL_SOURCE_NOT_CONFIGURED_MESSAGE
            else -> "Model source configured."
        }
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
