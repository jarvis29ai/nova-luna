package com.nova.luna.modelinstall

import com.nova.luna.brain.BrainRequest
import com.nova.luna.brain.BrainRoleReadinessProvider
import com.nova.luna.brain.BrainRouterBridge
import com.nova.luna.brain.FailoverOverrideMarkers
import com.nova.luna.brain.MultiModelRoleSelector
import com.nova.luna.brain.ModelRuntimeFailureTracker
import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import java.io.File

class ModelInstallBrainRouterBridge(
    private val modelInstallService: ModelInstallService,
    private val catalog: BrainModelCatalog = BrainModelCatalog,
    private val failureTracker: ModelRuntimeFailureTracker = ModelRuntimeFailureTracker(),
    private val coreRuntimeAvailable: (() -> Boolean)? = null,
    private val filesDir: File? = null
) : BrainRouterBridge, BrainRoleReadinessProvider {
    private val roleSelector = MultiModelRoleSelector(
        catalog = catalog,
        failureTracker = failureTracker
    )

    override fun isReady(role: BrainModelRole): Boolean {
        if (isForcedUnavailable(role)) {
            return false
        }

        val modelId = modelIdForRole(role) ?: return false
        if (failureTracker.isSuppressed(role)) {
            return false
        }

        val installedAndVerified = modelInstallService.getReadyModelPath(modelId) != null ||
                modelInstallService.repairModelPath(modelId).ready

        if (!installedAndVerified) {
            return false
        }

        return when (role) {
            BrainModelRole.CORE_BRAIN -> coreRuntimeAvailable?.invoke() ?: true
            else -> true
        }
    }

    private fun isForcedUnavailable(role: BrainModelRole): Boolean {
        val dir = filesDir ?: return false
        val markerName = FailoverOverrideMarkers.markerFileName(role) ?: return false
        return File(dir, markerName).exists()
    }

    override fun selectLocalRoute(
        request: BrainRequest,
        allowOnlineHelper: Boolean
    ): BrainRouteDecision? {
        val selection = roleSelector.select(
            request = request,
            readinessProvider = this,
            allowOnlineHelper = allowOnlineHelper
        ) ?: return null

        val entry = catalog.entryForRole(selection.selectedRole) ?: return null

        return BrainRouteDecision(
            selectedRole = selection.selectedRole,
            reason = selection.reason,
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = selection.safetyNotes + listOf(
                "${entry.displayName} is the selected private local role."
            )
        )
    }

    override fun recordModelOutcome(
        role: BrainModelRole,
        available: Boolean,
        reason: String?
    ) {
        roleSelector.recordOutcome(role, available, reason)
    }

    private fun modelIdForRole(role: BrainModelRole): String? {
        return when (role) {
            BrainModelRole.CORE_BRAIN -> "core"
            BrainModelRole.MULTILINGUAL_BACKUP -> "full"
            BrainModelRole.LITE_FALLBACK -> "lite"
            else -> null
        }
    }
}
