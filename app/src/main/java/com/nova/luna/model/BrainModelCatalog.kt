package com.nova.luna.model

data class BrainModelCatalogEntry(
    val role: BrainModelRole,
    val packWireValue: String,
    val displayName: String,
    val modelFamilies: List<String>,
    val description: String,
    val languageHints: List<String> = emptyList(),
    val notes: List<String> = emptyList()
)

object BrainModelCatalog {
    val entries: List<BrainModelCatalogEntry> = listOf(
        BrainModelCatalogEntry(
            role = BrainModelRole.CORE_BRAIN,
            packWireValue = "core",
            displayName = "Core Brain",
            modelFamilies = listOf("Gemma", "future reasoning"),
            description = "Balanced reasoning role for complex commands and planning.",
            languageHints = listOf("en"),
            notes = listOf(
                "Use for complex commands, planning, and app-control understanding.",
                "Keep candidate output local, structured, and safety-gated."
            )
        ),
        BrainModelCatalogEntry(
            role = BrainModelRole.MULTILINGUAL_BACKUP,
            packWireValue = "full",
            displayName = "Multilingual Backup",
            modelFamilies = listOf("Qwen", "future multilingual"),
            description = "Hindi, Hinglish, and translation-heavy backup role.",
            languageHints = listOf("hi", "hinglish", "multilingual", "regional"),
            notes = listOf(
                "Preserve the user's language and handle multilingual phrasing.",
                "Keep candidate output local and safety-gated."
            )
        ),
        BrainModelCatalogEntry(
            role = BrainModelRole.LITE_FALLBACK,
            packWireValue = "lite",
            displayName = "Lite Fallback",
            modelFamilies = listOf("Phi", "Gemma 3 270M", "future small models"),
            description = "Weak-phone fallback role for short, simple, or emergency requests.",
            languageHints = listOf("fallback", "simple", "basic", "lite", "low_ram"),
            notes = listOf(
                "Prefer this when the device or route needs a lightweight fallback.",
                "Keep candidate output local and safety-gated."
            )
        )
    )

    fun entryForRole(role: BrainModelRole): BrainModelCatalogEntry? {
        return entries.firstOrNull { it.role == role }
    }

    fun entryForPackWireValue(packWireValue: String?): BrainModelCatalogEntry? {
        if (packWireValue.isNullOrBlank()) return null
        return entries.firstOrNull { it.packWireValue.equals(packWireValue, ignoreCase = true) }
    }

    fun supportedLocalRoles(): List<BrainModelRole> {
        return entries.map { it.role }
    }
}
