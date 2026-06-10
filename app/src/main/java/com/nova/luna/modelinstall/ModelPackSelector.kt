package com.nova.luna.modelinstall

class ModelPackSelector(
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks()
) {
    fun select(snapshot: DeviceCapabilitySnapshot): ModelPackSelection {
        val packsById = catalog.associateBy { it.id }
        val corePack = packsById[ModelPackId.CORE]
        val multilingualPack = packsById[ModelPackId.FULL]
        val litePack = packsById[ModelPackId.LITE]
        val availablePacks = listOf(multilingualPack, corePack, litePack).filterNotNull()
        val fallbackPack = availablePacks.firstOrNull()
            ?: error("No model packs are available.")

        val selected = when {
            !snapshot.isArm64 -> litePack ?: fallbackPack
            else -> availablePacks.firstOrNull { it.requirement.matches(snapshot) }
                ?: litePack
                ?: fallbackPack
        }

        val warnings = mutableListOf<String>()
        if (!snapshot.isArm64) {
            warnings += "CPU ABI ${snapshot.cpuAbi} is not arm64; using the lightweight fallback."
        }

        warnings += selected.requirement.warnings(snapshot)
        if (!snapshot.networkAvailable) {
            warnings += "Network is unavailable; downloads should stay fully offline-safe."
        }

        val reason = when (selected.id) {
            ModelPackId.LITE -> "Lite / lightweight fallback fits the current RAM and storage budget."
            ModelPackId.CORE -> "Core Brain balances memory use with stronger local reasoning."
            ModelPackId.FULL -> "Full / Multilingual Backup is available as a secondary language role."
        }

        return ModelPackSelection(
            packId = selected.id,
            displayName = selected.displayName,
            reason = reason,
            warnings = warnings.distinct()
        )
    }
}
