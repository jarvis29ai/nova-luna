package com.nova.luna.modelinstall

enum class ModelPackId(
    val wireValue: String,
    val displayName: String,
    val priority: Int
) {
    LITE(
        wireValue = "lite",
        displayName = "Lite",
        priority = 0
    ),
    CORE(
        wireValue = "core",
        displayName = "Core",
        priority = 1
    ),
    FULL(
        wireValue = "full",
        displayName = "Full",
        priority = 2
    );

    companion object {
        fun fromWireValue(value: String?): ModelPackId? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}

enum class ModelInstallStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    READY,
    REPAIR_NEEDED,
    FAILED
}

data class DeviceCapabilitySnapshot(
    val totalRamMb: Int,
    val freeStorageMb: Int,
    val androidVersion: Int,
    val cpuAbi: String,
    val networkAvailable: Boolean = true
) {
    val normalizedCpuAbi: String
        get() = cpuAbi.trim()

    val isArm64: Boolean
        get() = normalizedCpuAbi.contains("arm64", ignoreCase = true)
}

data class ModelPackRequirement(
    val minRamMb: Int,
    val minFreeStorageMb: Int,
    val requiredAbi: String? = null
) {
    fun matches(snapshot: DeviceCapabilitySnapshot): Boolean {
        val abiMatches = requiredAbi.isNullOrBlank() ||
            snapshot.normalizedCpuAbi.contains(requiredAbi, ignoreCase = true)

        return abiMatches &&
            snapshot.totalRamMb >= minRamMb &&
            snapshot.freeStorageMb >= minFreeStorageMb
    }

    fun warnings(snapshot: DeviceCapabilitySnapshot): List<String> {
        return buildList {
            if (!requiredAbi.isNullOrBlank() &&
                !snapshot.normalizedCpuAbi.contains(requiredAbi, ignoreCase = true)
            ) {
                add("CPU ABI ${snapshot.cpuAbi} is below the recommended $requiredAbi target.")
            }

            if (snapshot.totalRamMb < minRamMb) {
                add("Available RAM ${snapshot.totalRamMb} MB is below the recommended ${minRamMb} MB.")
            }

            if (snapshot.freeStorageMb < minFreeStorageMb) {
                add("Available storage ${snapshot.freeStorageMb} MB is below the recommended ${minFreeStorageMb} MB.")
            }
        }
    }
}

data class ModelFileSpec(
    val fileName: String,
    val relativePath: String = "",
    val sha256: String? = null,
    val byteCount: Long? = null
) {
    fun normalized(): ModelFileSpec {
        return copy(
            fileName = fileName.trim(),
            relativePath = relativePath.trim().trim('/'),
            sha256 = sha256?.trim()?.takeIf { it.isNotBlank() },
            byteCount = byteCount?.coerceAtLeast(0L)
        )
    }

    companion object {
        fun fromJsonMap(json: Map<String, Any?>): ModelFileSpec {
            return ModelFileSpec(
                fileName = json.jsonString("fileName"),
                relativePath = json.jsonString("relativePath", ""),
                sha256 = json.jsonStringOrNull("sha256")?.takeIf { it.isNotBlank() },
                byteCount = json.jsonLongOrNull("byteCount")
            ).normalized()
        }
    }
}

data class ModelPackSpec(
    val id: ModelPackId,
    val displayName: String,
    val description: String,
    val requirement: ModelPackRequirement,
    val files: List<ModelFileSpec>,
    val notes: List<String> = emptyList()
) {
    fun normalized(): ModelPackSpec {
        return copy(
            displayName = displayName.trim(),
            description = description.trim(),
            files = files.map { it.normalized() },
            notes = notes.map { it.trim() }.filter { it.isNotBlank() }
        )
    }
}

data class ModelPackSelection(
    val packId: ModelPackId,
    val displayName: String,
    val reason: String,
    val warnings: List<String> = emptyList()
)
