package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelRole
import java.security.MessageDigest

enum class ModelRuntimeStatus {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    READY,
    FAILED,
    UNAVAILABLE,
    CORRUPT,
    MISSING,
    CANCELLED
}

data class ModelRuntimeState(
    val packId: ModelPackId,
    val version: String,
    val displayName: String = packId.displayName,
    val runtimeStatus: ModelRuntimeStatus = ModelRuntimeStatus.IDLE,
    val installState: ModelInstallStatus = ModelInstallStatus.NOT_INSTALLED,
    val registryConfirmed: Boolean = false,
    val verificationPassed: Boolean = false,
    val runtimeLoaded: Boolean = false,
    val healthCheckPassed: Boolean = false,
    val healthCheckPrompt: String? = null,
    val healthCheckResponse: String? = null,
    val healthCheckReason: String? = null,
    val loadedModelPath: String? = null,
    val ready: Boolean = false,
    val expectedFileCount: Int = 0,
    val verifiedFileCount: Int = 0,
    val missingFileCount: Int = 0,
    val corruptFileCount: Int = 0,
    val installedAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val lastRuntimeLoadEpochMs: Long = 0L,
    val lastHealthCheckEpochMs: Long = 0L,
    val manifestPath: String? = null,
    val modelRootPath: String? = null,
    val message: String? = null
) {
    fun normalized(): ModelRuntimeState {
        val normalizedStatus = runtimeStatus
        val normalizedInstallState = installState
        val normalizedRuntimeLoaded = runtimeLoaded || ready
        val normalizedHealthCheckPassed = healthCheckPassed || ready
        val normalizedReady = ready &&
            normalizedStatus == ModelRuntimeStatus.READY &&
            normalizedInstallState == ModelInstallStatus.READY &&
            normalizedRuntimeLoaded &&
            normalizedHealthCheckPassed

        return copy(
            version = version.trim(),
            displayName = displayName.trim(),
            runtimeStatus = normalizedStatus,
            installState = normalizedInstallState,
            registryConfirmed = registryConfirmed,
            verificationPassed = verificationPassed,
            runtimeLoaded = normalizedRuntimeLoaded,
            healthCheckPassed = normalizedHealthCheckPassed,
            healthCheckPrompt = healthCheckPrompt?.trim()?.takeIf { it.isNotBlank() },
            healthCheckResponse = healthCheckResponse?.trim()?.takeIf { it.isNotBlank() },
            healthCheckReason = healthCheckReason?.trim()?.takeIf { it.isNotBlank() },
            loadedModelPath = loadedModelPath?.trim()?.takeIf { it.isNotBlank() },
            ready = normalizedReady,
            expectedFileCount = expectedFileCount.coerceAtLeast(0),
            verifiedFileCount = verifiedFileCount.coerceAtLeast(0),
            missingFileCount = missingFileCount.coerceAtLeast(0),
            corruptFileCount = corruptFileCount.coerceAtLeast(0),
            installedAtEpochMs = installedAtEpochMs.coerceAtLeast(0L),
            updatedAtEpochMs = updatedAtEpochMs.coerceAtLeast(0L),
            lastRuntimeLoadEpochMs = lastRuntimeLoadEpochMs.coerceAtLeast(0L),
            lastHealthCheckEpochMs = lastHealthCheckEpochMs.coerceAtLeast(0L),
            manifestPath = manifestPath?.trim()?.takeIf { it.isNotBlank() },
            modelRootPath = modelRootPath?.trim()?.takeIf { it.isNotBlank() },
            message = message?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    fun isRuntimeReady(): Boolean {
        return runtimeStatus == ModelRuntimeStatus.READY &&
            installState == ModelInstallStatus.READY &&
            ready &&
            registryConfirmed &&
            verificationPassed &&
            runtimeLoaded &&
            healthCheckPassed &&
            missingFileCount == 0 &&
            corruptFileCount == 0 &&
            verifiedFileCount >= expectedFileCount
    }

    fun toJsonValue(): Map<String, Any?> {
        return linkedMapOf(
            "packId" to packId.wireValue,
            "version" to version,
            "displayName" to displayName,
            "runtimeStatus" to runtimeStatus.name,
            "installState" to installState.name,
            "registryConfirmed" to registryConfirmed,
            "verificationPassed" to verificationPassed,
            "runtimeLoaded" to runtimeLoaded,
            "healthCheckPassed" to healthCheckPassed,
            "healthCheckPrompt" to healthCheckPrompt,
            "healthCheckResponse" to healthCheckResponse,
            "healthCheckReason" to healthCheckReason,
            "loadedModelPath" to loadedModelPath,
            "ready" to ready,
            "expectedFileCount" to expectedFileCount,
            "verifiedFileCount" to verifiedFileCount,
            "missingFileCount" to missingFileCount,
            "corruptFileCount" to corruptFileCount,
            "installedAtEpochMs" to installedAtEpochMs,
            "updatedAtEpochMs" to updatedAtEpochMs,
            "lastRuntimeLoadEpochMs" to lastRuntimeLoadEpochMs,
            "lastHealthCheckEpochMs" to lastHealthCheckEpochMs,
            "manifestPath" to manifestPath,
            "modelRootPath" to modelRootPath,
            "message" to message
        ).filterValues { value -> value != null }
    }

    companion object {
        fun fromJsonMap(json: Map<String, Any?>): ModelRuntimeState {
            val packIdValue = json.jsonString("packId")
            val packId = ModelPackId.fromWireValue(packIdValue)
                ?: error("Unknown pack id: $packIdValue")

            return ModelRuntimeState(
                packId = packId,
                version = json.jsonString("version", packId.wireValue),
                displayName = json.jsonString("displayName", packId.displayName),
                runtimeStatus = runCatching {
                    ModelRuntimeStatus.valueOf(json.jsonString("runtimeStatus", ModelRuntimeStatus.IDLE.name))
                }.getOrDefault(ModelRuntimeStatus.IDLE),
                installState = runCatching {
                    ModelInstallStatus.valueOf(json.jsonString("installState", ModelInstallStatus.NOT_INSTALLED.name))
                }.getOrDefault(ModelInstallStatus.NOT_INSTALLED),
                registryConfirmed = json.jsonBoolean("registryConfirmed", false),
                verificationPassed = json.jsonBoolean("verificationPassed", false),
                runtimeLoaded = json.jsonBoolean("runtimeLoaded", json.jsonBoolean("ready", false)),
                healthCheckPassed = json.jsonBoolean("healthCheckPassed", json.jsonBoolean("ready", false)),
                healthCheckPrompt = json.jsonStringOrNull("healthCheckPrompt"),
                healthCheckResponse = json.jsonStringOrNull("healthCheckResponse"),
                healthCheckReason = json.jsonStringOrNull("healthCheckReason"),
                loadedModelPath = json.jsonStringOrNull("loadedModelPath"),
                ready = json.jsonBoolean("ready", false),
                expectedFileCount = json.jsonIntOrNull("expectedFileCount") ?: 0,
                verifiedFileCount = json.jsonIntOrNull("verifiedFileCount") ?: 0,
                missingFileCount = json.jsonIntOrNull("missingFileCount") ?: 0,
                corruptFileCount = json.jsonIntOrNull("corruptFileCount") ?: 0,
                installedAtEpochMs = json.jsonLongOrNull("installedAtEpochMs") ?: 0L,
                updatedAtEpochMs = json.jsonLongOrNull("updatedAtEpochMs") ?: 0L,
                lastRuntimeLoadEpochMs = json.jsonLongOrNull("lastRuntimeLoadEpochMs") ?: 0L,
                lastHealthCheckEpochMs = json.jsonLongOrNull("lastHealthCheckEpochMs") ?: 0L,
                manifestPath = json.jsonStringOrNull("manifestPath"),
                modelRootPath = json.jsonStringOrNull("modelRootPath"),
                message = json.jsonStringOrNull("message")
            ).normalized()
        }
    }
}

data class ModelInstallStatusSnapshot(
    val packId: ModelPackId,
    val displayName: String,
    val runtimeState: ModelRuntimeState,
    val registryManifest: ModelManifest?,
    val expectedFileKeys: List<String>,
    val installedFileKeys: List<String>,
    val missingFileKeys: List<String>,
    val corruptFileKeys: List<String>
) {
    val runtimeStatus: ModelRuntimeStatus
        get() = runtimeState.runtimeStatus

    val installState: ModelInstallStatus
        get() = runtimeState.installState

    val registryConfirmed: Boolean
        get() = runtimeState.registryConfirmed

    val verificationPassed: Boolean
        get() = runtimeState.verificationPassed

    val ready: Boolean
        get() = runtimeState.isRuntimeReady()

    val hasMissingFiles: Boolean
        get() = missingFileKeys.isNotEmpty()

    val hasCorruptFiles: Boolean
        get() = corruptFileKeys.isNotEmpty()
}

internal fun ModelRuntimeStatus.toInstallStatus(): ModelInstallStatus {
    return when (this) {
        ModelRuntimeStatus.IDLE -> ModelInstallStatus.NOT_INSTALLED
        ModelRuntimeStatus.DOWNLOADING -> ModelInstallStatus.DOWNLOADING
        ModelRuntimeStatus.VERIFYING -> ModelInstallStatus.VERIFYING
        ModelRuntimeStatus.READY -> ModelInstallStatus.READY
        ModelRuntimeStatus.FAILED -> ModelInstallStatus.FAILED
        ModelRuntimeStatus.UNAVAILABLE -> ModelInstallStatus.FAILED
        ModelRuntimeStatus.CORRUPT -> ModelInstallStatus.REPAIR_NEEDED
        ModelRuntimeStatus.MISSING -> ModelInstallStatus.REPAIR_NEEDED
        ModelRuntimeStatus.CANCELLED -> ModelInstallStatus.FAILED
    }
}

internal fun ModelPackSpec.versionTag(): String {
    val payload = buildString {
        append(id.wireValue)
        append('|')
        append(displayName)
        append('|')
        append(description)
        append('|')
        append(files.joinToString(";") { file ->
            val normalized = file.normalized()
            listOf(
                normalized.fileName,
                normalized.relativePath,
                normalized.sha256.orEmpty(),
                normalized.byteCount?.toString().orEmpty()
            ).joinToString(":")
        })
    }
    return sha256Hex(payload).take(32)
}

internal fun ModelPackSpec.expectedFilesForStorage(): List<ModelFileSpec> {
    return files.map { file -> file.normalized().forPackStorage(id) }
}

internal fun ModelFileSpec.forPackStorage(packId: ModelPackId): ModelFileSpec {
    val normalizedRelativePath = normalizedPackRelativePath(packId, relativePath)
    return copy(relativePath = normalizedRelativePath).normalized()
}

internal fun ModelDownloadSource.storageFileKey(): String {
    val normalizedRelativePath = normalizedPackRelativePath(packId, relativePath)
    return if (normalizedRelativePath.isBlank()) {
        fileName.trim()
    } else {
        "$normalizedRelativePath/${fileName.trim()}"
    }
}

internal fun ModelFileSpec.storageFileKey(packId: ModelPackId): String {
    val normalizedRelativePath = normalizedPackRelativePath(packId, relativePath)
    return if (normalizedRelativePath.isBlank()) {
        fileName.trim()
    } else {
        "$normalizedRelativePath/${fileName.trim()}"
    }
}

internal fun ModelPackSpec.toReadyManifest(
    installedFiles: List<ModelFileSpec>,
    installedAtEpochMs: Long = System.currentTimeMillis()
): ModelManifest {
    val normalizedFiles = installedFiles.map { it.forPackStorage(id) }
    return ModelManifest(
        packId = id,
        version = versionTag(),
        displayName = displayName,
        state = ModelInstallStatus.READY,
        installedAtEpochMs = installedAtEpochMs,
        files = normalizedFiles,
        notes = notes,
        checksumSha256 = sha256Hex(normalizedFiles.joinToString("|") { file ->
            listOf(
                file.fileName,
                file.relativePath,
                file.sha256.orEmpty(),
                file.byteCount?.toString().orEmpty()
            ).joinToString(":")
        })
    ).normalized()
}

internal fun normalizedPackRelativePath(packId: ModelPackId, relativePath: String): String {
    val segments = relativePath.trim().trim('/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (segments.isEmpty()) {
        return ""
    }

    val packSegment = packId.wireValue
    val storageSegments = if (segments.first().equals(packSegment, ignoreCase = true)) {
        segments.drop(1)
    } else {
        segments
    }

    return storageSegments.joinToString("/")
}

internal fun ModelDownloadSource.toInstalledFileSpec(fileLength: Long): ModelFileSpec {
    return ModelFileSpec(
        fileName = fileName,
        relativePath = normalizedPackRelativePath(packId, relativePath),
        sha256 = expectedSha256,
        byteCount = fileLength
    ).normalized()
}

enum class ModelUserFacingState {
    CHECKING_PHONE,
    INSTALLING_AI_BRAIN,
    VERIFYING_AI_BRAIN,
    READY,
    REPAIRING,
    STORAGE_NEEDED,
    UNAVAILABLE
}

data class ModelUserFacingStatus(
    val packId: ModelPackId,
    val displayName: String,
    val state: ModelUserFacingState,
    val message: String,
    val canRetry: Boolean = false,
    val canRepair: Boolean = false,
    val canUpdate: Boolean = false,
    val busy: Boolean = false
) {
    fun normalized(): ModelUserFacingStatus {
        return copy(
            displayName = displayName.trim(),
            message = message.trim().takeIf { it.isNotBlank() }
                ?: defaultMessage(state, displayName)
        )
    }

    companion object {
        fun create(
            packId: ModelPackId,
            displayName: String,
            runtimeStatus: ModelRuntimeStatus,
            installState: ModelInstallStatus,
            ready: Boolean,
            message: String?,
            canUpdate: Boolean = false
        ): ModelUserFacingStatus {
            val state = mapState(runtimeStatus, installState, ready, message)
            return ModelUserFacingStatus(
                packId = packId,
                displayName = displayName.trim(),
                state = state,
                message = defaultMessage(state, displayName),
                canRetry = state in setOf(
                    ModelUserFacingState.REPAIRING,
                    ModelUserFacingState.UNAVAILABLE,
                    ModelUserFacingState.STORAGE_NEEDED
                ),
                canRepair = state in setOf(
                    ModelUserFacingState.REPAIRING,
                    ModelUserFacingState.UNAVAILABLE,
                    ModelUserFacingState.STORAGE_NEEDED
                ),
                canUpdate = canUpdate || state == ModelUserFacingState.READY,
                busy = state in setOf(
                    ModelUserFacingState.CHECKING_PHONE,
                    ModelUserFacingState.INSTALLING_AI_BRAIN,
                    ModelUserFacingState.VERIFYING_AI_BRAIN,
                    ModelUserFacingState.REPAIRING
                )
            ).normalized()
        }

        private fun mapState(
            runtimeStatus: ModelRuntimeStatus,
            installState: ModelInstallStatus,
            ready: Boolean,
            message: String?
        ): ModelUserFacingState {
            if (ready && runtimeStatus == ModelRuntimeStatus.READY && installState == ModelInstallStatus.READY) {
                return ModelUserFacingState.READY
            }

            val normalizedMessage = message?.lowercase().orEmpty()
            val storageHints = listOf("storage", "space", "disk full", "no space", "insufficient")
            val repairHints = listOf("repair", "corrupt", "missing", "verification", "checksum")

            return when (runtimeStatus) {
                ModelRuntimeStatus.DOWNLOADING -> ModelUserFacingState.INSTALLING_AI_BRAIN
                ModelRuntimeStatus.VERIFYING -> ModelUserFacingState.VERIFYING_AI_BRAIN
                ModelRuntimeStatus.READY -> ModelUserFacingState.READY
                ModelRuntimeStatus.CORRUPT,
                ModelRuntimeStatus.MISSING,
                ModelRuntimeStatus.CANCELLED -> ModelUserFacingState.REPAIRING

                ModelRuntimeStatus.FAILED,
                ModelRuntimeStatus.UNAVAILABLE -> {
                    when {
                        storageHints.any { normalizedMessage.contains(it) } -> ModelUserFacingState.STORAGE_NEEDED
                        repairHints.any { normalizedMessage.contains(it) } -> ModelUserFacingState.REPAIRING
                        installState == ModelInstallStatus.REPAIR_NEEDED -> ModelUserFacingState.REPAIRING
                        installState == ModelInstallStatus.DOWNLOADING -> ModelUserFacingState.INSTALLING_AI_BRAIN
                        installState == ModelInstallStatus.VERIFYING -> ModelUserFacingState.VERIFYING_AI_BRAIN
                        installState == ModelInstallStatus.NOT_INSTALLED -> ModelUserFacingState.CHECKING_PHONE
                        else -> ModelUserFacingState.UNAVAILABLE
                    }
                }

                ModelRuntimeStatus.IDLE -> ModelUserFacingState.CHECKING_PHONE
            }
        }

        private fun defaultMessage(state: ModelUserFacingState, displayName: String): String {
            return when (state) {
                ModelUserFacingState.CHECKING_PHONE -> "Checking phone compatibility for $displayName."
                ModelUserFacingState.INSTALLING_AI_BRAIN -> "Installing AI brain."
                ModelUserFacingState.VERIFYING_AI_BRAIN -> "Verifying AI brain."
                ModelUserFacingState.READY -> "$displayName is ready."
                ModelUserFacingState.REPAIRING -> "Repairing AI brain."
                ModelUserFacingState.STORAGE_NEEDED -> "Additional storage is needed."
                ModelUserFacingState.UNAVAILABLE -> "$displayName is unavailable."
            }
        }
    }
}

data class ModelHealthScanResult(
    val packId: ModelPackId,
    val displayName: String,
    val runtimeStatus: ModelRuntimeStatus,
    val installState: ModelInstallStatus,
    val ready: Boolean,
    val registryConfirmed: Boolean,
    val verificationPassed: Boolean,
    val runtimeLoaded: Boolean,
    val healthCheckPassed: Boolean,
    val installedVersion: String,
    val availableVersion: String,
    val reason: String,
    val expectedFileKeys: List<String>,
    val installedFileKeys: List<String>,
    val missingFileKeys: List<String>,
    val corruptFileKeys: List<String>,
    val userFacingStatus: ModelUserFacingStatus
) {
    val upToDate: Boolean
        get() = installedVersion == availableVersion

    val requiresRepair: Boolean
        get() = runtimeStatus in setOf(ModelRuntimeStatus.MISSING, ModelRuntimeStatus.CORRUPT, ModelRuntimeStatus.FAILED, ModelRuntimeStatus.UNAVAILABLE)
}

data class ModelCleanupResult(
    val packId: ModelPackId? = null,
    val deletedPaths: List<String> = emptyList(),
    val refusedPaths: List<String> = emptyList(),
    val skippedPaths: List<String> = emptyList(),
    val restoredPaths: List<String> = emptyList(),
    val message: String = "",
    val success: Boolean = true
)

internal fun BrainModelRole.toModelPackIdOrNull(): ModelPackId? {
    return when (this) {
        BrainModelRole.CORE_BRAIN,
        BrainModelRole.GEMMA_REASONING -> ModelPackId.CORE
        BrainModelRole.MULTILINGUAL_BACKUP -> ModelPackId.FULL
        BrainModelRole.LITE_FALLBACK -> ModelPackId.LITE
        else -> null
    }
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
