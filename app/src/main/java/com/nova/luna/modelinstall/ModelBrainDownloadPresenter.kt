package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelEngineType
import com.nova.luna.model.BrainModelRole

data class ModelBrainDownloadRow(
    val role: BrainModelRole,
    val roleDisplayName: String,
    val packId: ModelPackId,
    val packDisplayName: String,
    val modelId: String,
    val engineType: BrainModelEngineType,
    val fileName: String,
    val storagePath: String,
    val status: ModelUserFacingStatus,
    val sourceConfigured: Boolean,
    val sourceMessage: String,
    val canDownload: Boolean,
    val installed: Boolean,
    val ready: Boolean,
    val expectedByteCount: Long?,
    val detectedByteCount: Long?,
    val hashVerified: Boolean,
    val activeBrainRole: Boolean
) {
    fun displayMessage(): String {
        return when {
            ready -> "Ready at $storagePath."
            sourceConfigured && status.state == ModelUserFacingState.CHECKING_PHONE -> sourceMessage
            sourceConfigured -> sourceMessage
            else -> sourceMessage
        }
    }

    fun toLine(): String {
        return buildString {
            append(roleDisplayName)
            append(" (")
            append(modelId)
            append(", ")
            append(engineType.name)
            append("): ")
            append(status.state.displayLabel())
            append(" | source=")
            append(sourceMessage)
            append(" | installed=")
            append(if (installed) "yes" else "no")
            append(" | ready=")
            append(if (ready) "yes" else "no")
            append(" | path=")
            append(storagePath)
            append(" | size=")
            append(detectedByteCount?.let(::humanReadableBytes) ?: "missing")
            append(" / expected=")
            append(expectedByteCount?.let(::humanReadableBytes) ?: "unknown")
            append(" | hash=")
            append(
                when {
                    hashVerified -> "verified"
                    sourceConfigured -> "not verified"
                    else -> "not configured"
                }
            )
            if (activeBrainRole) {
                append(" | active")
            }
        }
    }
}

data class ModelBrainDownloadReport(
    val recommendedRole: BrainModelRole,
    val recommendedRoleDisplayName: String,
    val recommendedPackId: ModelPackId,
    val recommendedPackDisplayName: String,
    val recommendationReason: String,
    val recommendationWarnings: List<String>,
    val selectedActiveRole: BrainModelRole?,
    val selectedActiveRoleDisplayName: String,
    val rows: List<ModelBrainDownloadRow>
) {
    val recommendedRow: ModelBrainDownloadRow?
        get() = rows.firstOrNull { it.packId == recommendedPackId }

    val recommendedSourceConfigured: Boolean
        get() = recommendedRow?.sourceConfigured == true

    val canDownloadRecommended: Boolean
        get() = recommendedRow?.canDownload == true

    val recommendedActionLabel: String
        get() = when {
            recommendedRow == null -> "Model pack unavailable."
            recommendedRow?.status?.state == ModelUserFacingState.READY -> "Already ready."
            recommendedSourceConfigured -> "Download available."
            else -> recommendedRow?.sourceMessage ?: MODEL_SOURCE_NOT_CONFIGURED_MESSAGE
        }

    fun toText(): String {
        return buildString {
            appendLine("Nova/Luna AI Brain")
            appendLine("Selected active brain role: $selectedActiveRoleDisplayName")
            appendLine("Recommended: $recommendedRoleDisplayName ($recommendedPackDisplayName)")
            appendLine("Reason: $recommendationReason")

            if (recommendationWarnings.isNotEmpty()) {
                appendLine("Warnings:")
                recommendationWarnings.forEach { warning ->
                    appendLine("- $warning")
                }
            }

            appendLine("Role Status:")
            rows.forEach { row ->
                appendLine("- ${row.toLine()}")
            }

            append("Action: ")
            append(recommendedActionLabel)
        }
    }
}

class ModelBrainDownloadPresenter(
    private val manager: DefaultModelManager,
    private val sourceProvider: ModelDownloadSourceProvider = manager.coordinator.downloadSourceProvider
) {
    fun buildStatusLine(snapshot: DeviceCapabilitySnapshot): String {
        val report = buildReport(snapshot)
        val row = report.recommendedRow
        return if (row == null) {
            "Nova/Luna AI Brain: no model roles are available."
        } else {
            "Nova/Luna AI Brain: ${row.roleDisplayName} (${row.packDisplayName}) - ${row.displayMessage()}"
        }
    }

    fun buildReportText(snapshot: DeviceCapabilitySnapshot): String {
        return buildReport(snapshot).toText()
    }

    fun buildReport(snapshot: DeviceCapabilitySnapshot): ModelBrainDownloadReport {
        val recommendation = manager.selectRecommendedPack(snapshot)
        val recommendedRole = recommendedRoleFor(recommendation.packId)
        val rawRows = BrainModelCatalog.supportedLocalRoles().mapNotNull { role ->
            val packId = role.toModelPackIdOrNull() ?: return@mapNotNull null
            buildRow(role = role, packId = packId)
        }
        val selectedActiveRole = rawRows.firstOrNull { it.ready }?.role
        val selectedActiveRoleDisplayName = selectedActiveRole?.let { activeRole ->
            BrainModelCatalog.entryForRole(activeRole)?.displayName ?: activeRole.name
        } ?: "No role is ready yet"
        val rows = rawRows.map { row ->
            row.copy(activeBrainRole = row.role == selectedActiveRole)
        }

        val recommendedEntry = BrainModelCatalog.entryForRole(recommendedRole)
        val recommendedPackDisplayName = runCatching {
            manager.coordinator.packSpec(recommendation.packId).displayName
        }.getOrDefault(recommendation.packId.displayName)

        return ModelBrainDownloadReport(
            recommendedRole = recommendedRole,
            recommendedRoleDisplayName = recommendedEntry?.displayName ?: recommendedRole.name,
            recommendedPackId = recommendation.packId,
            recommendedPackDisplayName = recommendedPackDisplayName,
            recommendationReason = recommendation.reason,
            recommendationWarnings = recommendation.warnings,
            selectedActiveRole = selectedActiveRole,
            selectedActiveRoleDisplayName = selectedActiveRoleDisplayName,
            rows = rows
        )
    }

    fun canDownloadRecommended(snapshot: DeviceCapabilitySnapshot): Boolean {
        return buildReport(snapshot).canDownloadRecommended
    }

    fun startRecommendedDownload(
        snapshot: DeviceCapabilitySnapshot,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelInstallStatusSnapshot) -> Unit = {}
    ): ModelInstallStatusSnapshot? {
        val report = buildReport(snapshot)
        val row = report.recommendedRow ?: return null
        if (!row.sourceConfigured || row.status.state == ModelUserFacingState.READY) {
            return manager.getInstallStatus(report.recommendedPackId)
        }

        return manager.startInstallDownload(
            packId = report.recommendedPackId,
            cancelRequested = cancelRequested,
            onStateChanged = onStateChanged
        )
    }

    private fun buildRow(
        role: BrainModelRole,
        packId: ModelPackId
    ): ModelBrainDownloadRow? {
        val entry = BrainModelCatalog.entryForRole(role)
            ?: return null
        val pack = runCatching { manager.coordinator.packSpec(packId) }.getOrNull()
            ?: return null
        val installStatus = manager.getInstallStatus(packId)
        val userFacingStatus = manager.getUserSafeState(packId)
        val sourceStatus = sourceProvider.configurationStatus(packId)
        val sourceMessage = sourceProvider.configurationMessage(packId)
        val expectedKey = installStatus.expectedFileKeys.firstOrNull()
            ?: pack.files.firstOrNull()?.storageFileKey(pack.id)
            ?: entry.fileName
        val storagePath = manager.coordinator.storage.packFile(packId, expectedKey).path
        val file = manager.coordinator.storage.packFile(packId, expectedKey)
        val detectedSize = if (file.exists()) file.length() else null

        return ModelBrainDownloadRow(
            role = role,
            roleDisplayName = entry.displayName,
            packId = packId,
            packDisplayName = pack.displayName,
            modelId = entry.modelId,
            engineType = entry.engineType,
            fileName = entry.fileName,
            storagePath = storagePath,
            status = userFacingStatus,
            sourceConfigured = sourceStatus.ready,
            sourceMessage = sourceMessage,
            canDownload = sourceStatus.ready && userFacingStatus.state != ModelUserFacingState.READY,
            installed = installStatus.runtimeStatus != ModelRuntimeStatus.IDLE,
            ready = installStatus.ready,
            expectedByteCount = entry.expectedByteCount,
            detectedByteCount = detectedSize,
            hashVerified = installStatus.verificationPassed,
            activeBrainRole = false
        )
    }

    private fun recommendedRoleFor(packId: ModelPackId): BrainModelRole {
        return when (packId) {
            ModelPackId.CORE -> BrainModelRole.CORE_BRAIN
            ModelPackId.FULL -> BrainModelRole.MULTILINGUAL_BACKUP
            ModelPackId.LITE -> BrainModelRole.LITE_FALLBACK
        }
    }

}

private fun ModelUserFacingState.displayLabel(): String {
    return when (this) {
        ModelUserFacingState.CHECKING_PHONE -> "Checking phone"
        ModelUserFacingState.INSTALLING_AI_BRAIN -> "Installing AI brain"
        ModelUserFacingState.VERIFYING_AI_BRAIN -> "Verifying AI brain"
        ModelUserFacingState.READY -> "Ready"
        ModelUserFacingState.REPAIRING -> "Repairing"
        ModelUserFacingState.STORAGE_NEEDED -> "Storage needed"
        ModelUserFacingState.UNAVAILABLE -> "Unavailable"
    }
}

private fun ModelPackId.toRole(): BrainModelRole {
    return when (this) {
        ModelPackId.CORE -> BrainModelRole.CORE_BRAIN
        ModelPackId.FULL -> BrainModelRole.MULTILINGUAL_BACKUP
        ModelPackId.LITE -> BrainModelRole.LITE_FALLBACK
    }
}

private fun humanReadableBytes(bytes: Long): String {
    if (bytes < 1024L) {
        return "$bytes B"
    }

    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    val unit = units.getOrNull(unitIndex.coerceAtLeast(0)) ?: "KB"
    return String.format("%.1f %s", value, unit)
}
