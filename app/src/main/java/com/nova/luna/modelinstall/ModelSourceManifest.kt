package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelCatalog
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

enum class ModelSourceConfigurationState {
    READY,
    SOURCE_NOT_CONFIGURED,
    HASH_NOT_CONFIGURED,
    SIZE_NOT_CONFIGURED
}

data class ModelSourceConfigurationStatus(
    val state: ModelSourceConfigurationState,
    val message: String,
    val sourceConfigured: Boolean,
    val hashConfigured: Boolean,
    val sizeConfigured: Boolean,
    val ready: Boolean,
    val problems: List<String> = emptyList()
)

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

    fun configurationStatus(packId: ModelPackId): ModelSourceConfigurationStatus {
        val packEntries = entriesFor(packId)
        if (packEntries.isEmpty()) {
            return ModelSourceConfigurationStatus(
                state = ModelSourceConfigurationState.SOURCE_NOT_CONFIGURED,
                message = "SOURCE_NOT_CONFIGURED",
                sourceConfigured = false,
                hashConfigured = false,
                sizeConfigured = false,
                ready = false,
                problems = listOf("source")
            )
        }

        val sourceConfigured = packEntries.all { !it.downloadUrl.isNullOrBlank() && it.enabled }
        val hashConfigured = packEntries.all { !it.expectedSha256.isNullOrBlank() }
        val sizeConfigured = packEntries.all { it.expectedByteCount != null && it.expectedByteCount > 0L }

        val state = when {
            !sourceConfigured -> ModelSourceConfigurationState.SOURCE_NOT_CONFIGURED
            !hashConfigured -> ModelSourceConfigurationState.HASH_NOT_CONFIGURED
            !sizeConfigured -> ModelSourceConfigurationState.SIZE_NOT_CONFIGURED
            else -> ModelSourceConfigurationState.READY
        }

        val problems = buildList {
            if (!sourceConfigured) add("source")
            if (!hashConfigured) add("hash")
            if (!sizeConfigured) add("size")
        }

        return ModelSourceConfigurationStatus(
            state = state,
            message = when (state) {
                ModelSourceConfigurationState.READY -> "READY"
                ModelSourceConfigurationState.SOURCE_NOT_CONFIGURED -> "SOURCE_NOT_CONFIGURED"
                ModelSourceConfigurationState.HASH_NOT_CONFIGURED -> "HASH_NOT_CONFIGURED"
                ModelSourceConfigurationState.SIZE_NOT_CONFIGURED -> "SIZE_NOT_CONFIGURED"
            },
            sourceConfigured = sourceConfigured,
            hashConfigured = hashConfigured,
            sizeConfigured = sizeConfigured,
            ready = state == ModelSourceConfigurationState.READY,
            problems = problems
        )
    }

    companion object {
        fun empty(): ModelSourceManifest {
            return ModelSourceManifest(emptyList())
        }

        fun fromBuildConfig(): ModelSourceManifest {
            return ModelSourceManifest(
                entries = BrainModelCatalog.entries.map { it.toSourceEntry() }
            )
        }
    }
}
