package com.nova.luna.modelinstall

import com.nova.luna.brain.BrainRequest
import com.nova.luna.brain.BrainRoleReadinessProvider
import com.nova.luna.brain.BrainRouterBridge
import com.nova.luna.brain.MultiModelRoleSelector
import com.nova.luna.brain.ModelRuntimeFailureTracker
import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class ModelInstallBrainRouterBridge(
    private val runtimeReadinessChecker: LocalRuntimeReadinessChecker,
    private val catalog: BrainModelCatalog = BrainModelCatalog,
    private val failureTracker: ModelRuntimeFailureTracker = ModelRuntimeFailureTracker()
) : BrainRouterBridge, BrainRoleReadinessProvider {
    private val roleSelector = MultiModelRoleSelector(
        catalog = catalog,
        failureTracker = failureTracker
    )

    override fun isReady(role: BrainModelRole): Boolean {
        val packId = packIdForRole(role) ?: return false
        if (failureTracker.isSuppressed(role)) {
            return false
        }

        return runCatching { runtimeReadinessChecker.installReady(packId) }
            .getOrDefault(false)
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

    private fun packIdForRole(role: BrainModelRole): ModelPackId? {
        return when (role) {
            BrainModelRole.CORE_BRAIN -> ModelPackId.CORE
            BrainModelRole.MULTILINGUAL_BACKUP -> ModelPackId.FULL
            BrainModelRole.LITE_FALLBACK -> ModelPackId.LITE
            else -> null
        }
    }
}
