package com.nova.luna.modelinstall

data class ModelManifest(
    val packId: ModelPackId,
    val version: String,
    val displayName: String = packId.displayName,
    val state: ModelInstallState = ModelInstallState.NOT_INSTALLED,
    val installedAtEpochMs: Long = 0L,
    val files: List<ModelFileSpec> = emptyList(),
    val notes: List<String> = emptyList(),
    val checksumSha256: String? = null
) {
    fun normalized(): ModelManifest {
        return copy(
            version = version.trim(),
            displayName = displayName.trim(),
            files = files.map { it.normalized() },
            notes = notes.map { it.trim() }.filter { it.isNotBlank() },
            checksumSha256 = checksumSha256?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    fun toReady(files: List<ModelFileSpec>, installedAtEpochMs: Long = System.currentTimeMillis()): ModelManifest {
        return copy(
            state = ModelInstallState.READY,
            installedAtEpochMs = installedAtEpochMs,
            files = files.map { it.normalized() }
        ).normalized()
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return SimpleJson.stringify(toJsonValue(), indentSpaces)
    }

    internal fun toJsonValue(): Map<String, Any?> {
        return linkedMapOf(
            "packId" to packId.wireValue,
            "version" to version,
            "displayName" to displayName,
            "state" to state.name,
            "installedAtEpochMs" to installedAtEpochMs,
            "files" to files.map { it.toJsonValue() },
            "notes" to notes,
            "checksumSha256" to checksumSha256
        ).filterValues { value -> value != null }
    }

    companion object {
        fun fromJsonString(value: String): ModelManifest {
            return fromJsonMap(SimpleJson.parseObject(value))
        }

        fun fromJsonMap(json: Map<String, Any?>): ModelManifest {
            val packIdValue = json.jsonString("packId")
            val packId = ModelPackId.fromWireValue(packIdValue)
                ?: error("Unknown pack id: $packIdValue")

            val files = json.jsonArray("files")
                .map { element ->
                    @Suppress("UNCHECKED_CAST")
                    ModelFileSpec.fromJsonMap(element as? Map<String, Any?>
                        ?: error("Expected model file object in 'files'"))
                }
            val notes = json.jsonArray("notes")
                .map { entry ->
                    entry?.toString().orEmpty().trim()
                }
                .filter { it.isNotBlank() }

            return ModelManifest(
                packId = packId,
                version = json.jsonString("version"),
                displayName = json.jsonString("displayName", packId.displayName),
                state = runCatching { ModelInstallState.valueOf(json.jsonString("state", ModelInstallState.NOT_INSTALLED.name)) }
                    .getOrDefault(ModelInstallState.NOT_INSTALLED),
                installedAtEpochMs = json.jsonLongOrNull("installedAtEpochMs") ?: 0L,
                files = files,
                notes = notes,
                checksumSha256 = json.jsonStringOrNull("checksumSha256")?.takeIf { it.isNotBlank() }
            ).normalized()
        }
    }
}

internal fun ModelFileSpec.toJsonValue(): Map<String, Any?> {
    return linkedMapOf(
        "fileName" to fileName,
        "relativePath" to relativePath.takeIf { it.isNotBlank() },
        "sha256" to sha256,
        "byteCount" to byteCount
    ).filterValues { value -> value != null }
}
