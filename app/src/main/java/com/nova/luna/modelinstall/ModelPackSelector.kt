package com.nova.luna.modelinstall

class ModelPackSelector(
    private val catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks()
) {
    fun select(snapshot: DeviceCapabilitySnapshot): ModelPackSelection {
        val packsByPriority = catalog.sortedByDescending { it.id.priority }
        var selected = packsByPriority.firstOrNull { it.requirement.matches(snapshot) }
            ?: packsByPriority.lastOrNull()
            ?: error("No model packs are available.")

        val warnings = mutableListOf<String>()
        if (!snapshot.isArm64) {
            warnings += "CPU ABI ${snapshot.cpuAbi} is not arm64; using the Lite pack fallback."
            if (selected.id != ModelPackId.LITE) {
                selected = packsByPriority.first { it.id == ModelPackId.LITE }
            }
        }

        warnings += selected.requirement.warnings(snapshot)
        if (!snapshot.networkAvailable) {
            warnings += "Network is unavailable; downloads should stay fully offline-safe."
        }

        val reason = when (selected.id) {
            ModelPackId.LITE -> "Lite fits the current RAM and storage budget."
            ModelPackId.CORE -> "Core balances memory use with stronger local reasoning."
            ModelPackId.FULL -> "Full fits the device and keeps multilingual support ready."
        }

        return ModelPackSelection(
            packId = selected.id,
            displayName = selected.displayName,
            reason = reason,
            warnings = warnings.distinct()
        )
    }
}
