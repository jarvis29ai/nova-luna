package com.nova.luna.modelinstall

internal const val MODEL_SOURCE_NOT_CONFIGURED_MESSAGE =
    "Model source not configured. Add a signed model pack source before download."

class ModelDownloadSourceProvider(
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    val sourceManifest: ModelSourceManifest = ModelSourceManifest.empty(),
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
        val configuredManifestSources = sourceManifest.configuredEntriesFor(packId)
        if (configuredManifestSources.isNotEmpty()) {
            val manifestSources = sourceManifest.entriesFor(packId)
            if (manifestSources.size != pack.files.size ||
                configuredManifestSources.size != pack.files.size
            ) {
                return emptyList()
            }

            return configuredManifestSources.mapIndexed { index, entry ->
                entry.toDownloadSource(pack, index)
            }
        }

        if (sourceManifest.entriesFor(packId).isNotEmpty()) {
            return emptyList()
        }

        if (baseDownloadUrl.isNullOrBlank()) {
            return emptyList()
        }

        return pack.files.mapIndexed { index, file ->
            val normalized = file.normalized()
            if (normalized.sha256.isNullOrBlank() || normalized.byteCount == null || normalized.byteCount <= 0L) {
                return@mapIndexed null
            }

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
        }.filterNotNull().takeIf { it.size == pack.files.size } ?: emptyList()
    }

    fun sourceFor(packId: ModelPackId): ModelDownloadSource? {
        return sourcesFor(packId).firstOrNull()
    }

    fun isConfigured(packId: ModelPackId): Boolean {
        return sourcesFor(packId).isNotEmpty()
    }

    fun configurationMessage(packId: ModelPackId): String {
        return if (isConfigured(packId)) {
            "Download available."
        } else {
            MODEL_SOURCE_NOT_CONFIGURED_MESSAGE
        }
    }

    fun withCatalog(catalog: List<ModelPackSpec>): ModelDownloadSourceProvider {
        return ModelDownloadSourceProvider(
            catalog = catalog,
            sourceManifest = sourceManifest,
            baseDownloadUrl = baseDownloadUrl
        )
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

    private fun ModelSourceEntry.toDownloadSource(
        pack: ModelPackSpec,
        sourceIndex: Int
    ): ModelDownloadSource {
        val normalized = normalized()
        val sourceId = "${pack.id.wireValue}-${sourceIndex + 1}-${normalized.fileName}"
        return ModelDownloadSource(
            packId = pack.id,
            packDisplayName = pack.displayName,
            sourceId = sourceId,
            fileName = normalized.fileName,
            relativePath = normalized.relativePath,
            downloadUrl = normalized.downloadUrl,
            expectedSha256 = normalized.expectedSha256,
            expectedByteCount = normalized.expectedByteCount,
            notes = pack.notes
        ).normalized()
    }
}
