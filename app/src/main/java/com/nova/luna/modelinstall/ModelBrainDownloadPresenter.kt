package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole

data class ModelBrainDownloadRow(
    val role: BrainModelRole,
    val roleDisplayName: String,
    val packId: ModelPackId,
    val packDisplayName: String,
    val status: ModelUserFacingStatus,
    val sourceConfigured: Boolean,
    val sourceMessage: String,
    val canDownload: Boolean
) {
    fun displayMessage(): String {
        return if (status.state == ModelUserFacingState.READY || sourceConfigured) {
            status.message
        } else {
            sourceMessage
        }
    }

    fun toLine(): String {
        val displayedState = when {
            status.state == ModelUserFacingState.READY -> ModelUserFacingState.READY
            sourceConfigured -> status.state
            else -> ModelUserFacingState.UNAVAILABLE
        }
        return "${roleDisplayName} (${packDisplayName}): ${displayedState.displayLabel()} - ${displayMessage()}"
    }
}

data class ModelBrainDownloadReport(
    val recommendedRole: BrainModelRole,
    val recommendedRoleDisplayName: String,
    val recommendedPackId: ModelPackId,
    val recommendedPackDisplayName: String,
    val recommendationReason: String,
    val recommendationWarnings: List<String>,
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
            else -> MODEL_SOURCE_NOT_CONFIGURED_MESSAGE
        }

    fun toText(): String {
        return buildString {
            appendLine("Nova/Luna AI Brain")
            appendLine("Recommended: $recommendedRoleDisplayName ($recommendedPackDisplayName)")
            appendLine("Reason: $recommendationReason")

            if (recommendationWarnings.isNotEmpty()) {
                appendLine("Warnings:")
                recommendationWarnings.forEach { warning ->
                    appendLine("- $warning")
                }
            }

            appendLine("Pack Status:")
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
            "AI Brain: no model packs are available."
        } else {
            "AI Brain: ${row.roleDisplayName} (${row.packDisplayName}) - ${row.displayMessage()}"
        }
    }

    fun buildReportText(snapshot: DeviceCapabilitySnapshot): String {
        return buildReport(snapshot).toText()
    }

    fun buildReport(snapshot: DeviceCapabilitySnapshot): ModelBrainDownloadReport {
        val recommendation = manager.selectRecommendedPack(snapshot)
        val recommendedRole = recommendedRoleFor(recommendation.packId)
        val rows = BrainModelCatalog.supportedLocalRoles().mapNotNull { role ->
            val packId = role.toModelPackIdOrNull() ?: return@mapNotNull null
            buildRow(role, packId)
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
        val pack = runCatching { manager.coordinator.packSpec(packId) }.getOrNull()
            ?: return null
        val status = manager.getUserSafeState(packId)
        val sourceMessage = sourceProvider.configurationMessage(packId)
        val sourceConfigured = sourceProvider.isConfigured(packId)

        return ModelBrainDownloadRow(
            role = role,
            roleDisplayName = entry?.displayName ?: role.name,
            packId = packId,
            packDisplayName = pack.displayName,
            status = status,
            sourceConfigured = sourceConfigured,
            sourceMessage = sourceMessage,
            canDownload = sourceConfigured && status.state != ModelUserFacingState.READY
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
