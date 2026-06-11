package com.nova.luna.brain

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole

enum class RamGuardDecision {
    ALLOW,
    ALLOW_WITH_UNLOAD,
    BLOCK_USE_LITE_FALLBACK,
    BLOCK_NO_SAFE_MODEL
}

class ModelRamGuard(
    private val ramInfoProvider: RamInfoProvider,
    private val catalog: BrainModelCatalog = BrainModelCatalog
) {
    fun decide(requestedRole: BrainModelRole, currentLoadedRole: BrainModelRole?): RamGuardDecision {
        val entry = catalog.entryForRole(requestedRole) ?: return RamGuardDecision.ALLOW
        val requiredMb = entry.minimumRamMb ?: 0
        if (requiredMb <= 0) return RamGuardDecision.ALLOW

        val availableMb = ramInfoProvider.getAvailableRamMb()
        
        // Simple heuristic: if we have enough available RAM, allow.
        if (availableMb >= requiredMb) {
            return RamGuardDecision.ALLOW
        }

        // If unloading current model helps
        if (currentLoadedRole != null) {
            val currentEntry = catalog.entryForRole(currentLoadedRole)
            val currentUsageEstimate = currentEntry?.minimumRamMb ?: 0
            if (availableMb + currentUsageEstimate >= requiredMb) {
                return RamGuardDecision.ALLOW_WITH_UNLOAD
            }
        }

        // If we can't load the requested role, check if LITE_FALLBACK is safer
        if (requestedRole != BrainModelRole.LITE_FALLBACK) {
            val liteEntry = catalog.entryForRole(BrainModelRole.LITE_FALLBACK)
            val liteRequiredMb = liteEntry?.minimumRamMb ?: 0
            if (availableMb >= liteRequiredMb || (currentLoadedRole != null && availableMb + (catalog.entryForRole(currentLoadedRole)?.minimumRamMb ?: 0) >= liteRequiredMb)) {
                return RamGuardDecision.BLOCK_USE_LITE_FALLBACK
            }
        }

        return RamGuardDecision.BLOCK_NO_SAFE_MODEL
    }
}
