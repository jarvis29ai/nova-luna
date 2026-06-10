package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelCatalogEntry

internal fun BrainModelCatalogEntry.toModelPackSpec(): ModelPackSpec {
    return ModelPackSpec(
        id = ModelPackId.fromWireValue(packWireValue)
            ?: error("Unknown model pack wire value: $packWireValue"),
        displayName = displayName,
        description = description,
        requirement = ModelPackRequirement(
            minRamMb = minimumRamMb ?: 0,
            minFreeStorageMb = minimumFreeStorageMb ?: 0
        ),
        files = listOf(
            ModelFileSpec(
                fileName = fileName,
                relativePath = storageRelativePath,
                sha256 = sha256,
                byteCount = expectedByteCount
            )
        ),
        notes = notes
    ).normalized()
}

internal fun BrainModelCatalogEntry.toSourceEntry(): ModelSourceEntry {
    return ModelSourceEntry(
        role = role,
        displayName = displayName,
        fileName = fileName,
        relativePath = storageRelativePath,
        downloadUrl = downloadUrl,
        expectedSha256 = sha256,
        expectedByteCount = expectedByteCount,
        enabled = sourceEnabled,
        minimumRamMb = minimumRamMb ?: 0,
        minimumFreeStorageMb = minimumFreeStorageMb ?: 0
    ).normalized()
}

internal fun BrainModelCatalogEntry.toSourceMessage(): String {
    return when {
        !sourceEnabled || downloadUrl.isNullOrBlank() ->
            "SOURCE_NOT_CONFIGURED"

        sha256.isNullOrBlank() ->
            "HASH_NOT_CONFIGURED"

        expectedByteCount == null || expectedByteCount <= 0L ->
            "SIZE_NOT_CONFIGURED"

        else ->
            "READY"
    }
}

internal fun BrainModelCatalogEntry.downloadModelLabel(): String {
    return buildString {
        append(displayName)
        append(" (")
        append(modelId)
        append(")")
    }
}
