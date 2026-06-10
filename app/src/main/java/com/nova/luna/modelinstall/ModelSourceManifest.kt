package com.nova.luna.modelinstall

import com.nova.luna.BuildConfig
import com.nova.luna.model.BrainModelRole

data class ModelSourceEntry(
    val role: BrainModelRole,
    val displayName: String,
    val fileName: String,
    val relativePath: String = "",
    val downloadUrl: String? = null,
    val expectedSha256: String? = null,
    val expectedByteCount: Long? = null,
    val enabled: Boolean = false,
    val minimumRamMb: Int,
    val minimumFreeStorageMb: Int
) {
    fun normalized(): ModelSourceEntry {
        return copy(
            displayName = displayName.trim(),
            fileName = fileName.trim(),
            relativePath = relativePath.trim().trim('/'),
            downloadUrl = downloadUrl?.trim()?.takeIf { it.isNotBlank() },
            expectedSha256 = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
            expectedByteCount = expectedByteCount?.coerceAtLeast(0L),
            minimumRamMb = minimumRamMb.coerceAtLeast(0),
            minimumFreeStorageMb = minimumFreeStorageMb.coerceAtLeast(0)
        )
    }

    val packId: ModelPackId?
        get() = role.toModelPackIdOrNull()

    fun validationProblems(): List<String> {
        return buildList {
            if (!enabled) add("disabled")
            if (fileName.isBlank()) add("file name")
            if (downloadUrl.isNullOrBlank()) add("download URL")
            if (expectedSha256.isNullOrBlank()) add("SHA-256")
            if (expectedByteCount == null || expectedByteCount <= 0L) add("expected byte size")
        }
    }

    fun isConfigured(): Boolean {
        return validationProblems().isEmpty()
    }
}

data class ModelSourceManifest(
    val entries: List<ModelSourceEntry> = emptyList()
) {
    fun entriesFor(packId: ModelPackId): List<ModelSourceEntry> {
        return entries.filter { it.packId == packId }
    }

    fun configuredEntriesFor(packId: ModelPackId): List<ModelSourceEntry> {
        val packEntries = entriesFor(packId)
        if (packEntries.isEmpty()) {
            return emptyList()
        }

        return if (packEntries.all { it.isConfigured() }) {
            packEntries
        } else {
            emptyList()
        }
    }

    fun isConfigured(packId: ModelPackId): Boolean {
        return configuredEntriesFor(packId).isNotEmpty()
    }

    companion object {
        fun empty(): ModelSourceManifest {
            return ModelSourceManifest(emptyList())
        }

        fun fromBuildConfig(): ModelSourceManifest {
            val corePack = ModelPackCatalog.requirePack(ModelPackId.CORE)
            val fullPack = ModelPackCatalog.requirePack(ModelPackId.FULL)
            val litePack = ModelPackCatalog.requirePack(ModelPackId.LITE)

            return ModelSourceManifest(
                entries = buildList {
                    addAll(disabledEntriesForPack(
                        role = BrainModelRole.CORE_BRAIN,
                        pack = corePack
                    ))
                    addAll(disabledEntriesForPack(
                        role = BrainModelRole.MULTILINGUAL_BACKUP,
                        pack = fullPack
                    ))
                    add(buildLiteEntry(litePack))
                }
            )
        }

        private fun disabledEntriesForPack(
            role: BrainModelRole,
            pack: ModelPackSpec
        ): List<ModelSourceEntry> {
            return pack.files.map { file ->
                ModelSourceEntry(
                    role = role,
                    displayName = pack.displayName,
                    fileName = file.fileName,
                    relativePath = file.relativePath,
                    downloadUrl = null,
                    expectedSha256 = null,
                    expectedByteCount = null,
                    enabled = false,
                    minimumRamMb = pack.requirement.minRamMb,
                    minimumFreeStorageMb = pack.requirement.minFreeStorageMb
                ).normalized()
            }
        }

        private fun buildLiteEntry(pack: ModelPackSpec): ModelSourceEntry {
            val defaultFile = pack.files.firstOrNull()
            val fileName = BuildConfig.NOVA_LUNA_LITE_MODEL_FILENAME
                .trim()
                .ifBlank { defaultFile?.fileName.orEmpty() }
            val relativePath = BuildConfig.NOVA_LUNA_LITE_MODEL_RELATIVE_PATH
                .trim()
                .ifBlank { defaultFile?.relativePath.orEmpty() }

            return ModelSourceEntry(
                role = BrainModelRole.LITE_FALLBACK,
                displayName = pack.displayName,
                fileName = fileName,
                relativePath = relativePath,
                downloadUrl = BuildConfig.NOVA_LUNA_MODEL_BASE_URL,
                expectedSha256 = BuildConfig.NOVA_LUNA_LITE_MODEL_SHA256,
                expectedByteCount = BuildConfig.NOVA_LUNA_LITE_MODEL_BYTES.takeIf { it > 0L },
                enabled = BuildConfig.NOVA_LUNA_LITE_MODEL_ENABLED,
                minimumRamMb = pack.requirement.minRamMb,
                minimumFreeStorageMb = pack.requirement.minFreeStorageMb
            ).normalized()
        }
    }
}
