package com.nova.luna.model

import com.nova.luna.BuildConfig

enum class BrainModelEngineType {
    LITERT_LM,
    GGUF,
    PLACEHOLDER
}

data class BrainModelCatalogEntry(
    val role: BrainModelRole,
    val packWireValue: String,
    val modelId: String,
    val displayName: String,
    val fileName: String,
    val storageRelativePath: String = "",
    val engineType: BrainModelEngineType,
    val modelFamilies: List<String>,
    val description: String,
    val languageHints: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val downloadUrl: String? = null,
    val sha256: String? = null,
    val expectedByteCount: Long? = null,
    val minimumRamMb: Int? = null,
    val minimumFreeStorageMb: Int? = null,
    val sourceEnabled: Boolean = false
) {
    val packId: String
        get() = packWireValue.trim()

    val storagePathLabel: String
        get() = if (storageRelativePath.isBlank()) {
            fileName.trim()
        } else {
            "${storageRelativePath.trim().trim('/')}/${fileName.trim()}"
        }

    val sourceConfigured: Boolean
        get() = sourceEnabled &&
            !downloadUrl.isNullOrBlank() &&
            !sha256.isNullOrBlank() &&
            (expectedByteCount ?: 0L) > 0L

    val sourceProblems: List<String>
        get() = buildList {
            if (!sourceEnabled) add("disabled")
            if (downloadUrl.isNullOrBlank()) add("source")
            if (sha256.isNullOrBlank()) add("hash")
            if (expectedByteCount == null || expectedByteCount <= 0L) add("size")
        }

    fun normalized(): BrainModelCatalogEntry {
        return copy(
            packWireValue = packWireValue.trim(),
            modelId = modelId.trim(),
            displayName = displayName.trim(),
            fileName = fileName.trim(),
            storageRelativePath = storageRelativePath.trim().trim('/'),
            modelFamilies = modelFamilies.map { it.trim() }.filter { it.isNotBlank() },
            description = description.trim(),
            languageHints = languageHints.map { it.trim() }.filter { it.isNotBlank() },
            notes = notes.map { it.trim() }.filter { it.isNotBlank() },
            downloadUrl = downloadUrl?.trim()?.takeIf { it.isNotBlank() },
            sha256 = sha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
            expectedByteCount = expectedByteCount?.coerceAtLeast(0L),
            minimumRamMb = minimumRamMb?.coerceAtLeast(0),
            minimumFreeStorageMb = minimumFreeStorageMb?.coerceAtLeast(0)
        )
    }
}

object BrainModelCatalog {
    val entries: List<BrainModelCatalogEntry> = listOf(
        BrainModelCatalogEntry(
            role = BrainModelRole.CORE_BRAIN,
            packWireValue = "core",
            modelId = "gemma-3n-E2B-it-litert",
            displayName = "Core Brain",
            fileName = "gemma-3n-E2B-it-int4.litertlm",
            engineType = BrainModelEngineType.LITERT_LM,
            modelFamilies = listOf("Gemma 3n", "LiteRT LM"),
            description = "Primary reasoning role for complex commands, planning, and app-control understanding.",
            languageHints = listOf("en"),
            notes = listOf(
                "Use this as the main on-device brain.",
                "All final actions must still pass SafetyGate."
            ),
            downloadUrl = BuildConfig.NOVA_LUNA_CORE_MODEL_DOWNLOAD_URL,
            sha256 = BuildConfig.NOVA_LUNA_CORE_MODEL_SHA256,
            expectedByteCount = BuildConfig.NOVA_LUNA_CORE_MODEL_BYTES.takeIf { it > 0L },
            minimumRamMb = 4096,
            minimumFreeStorageMb = 2048,
            sourceEnabled = BuildConfig.NOVA_LUNA_CORE_MODEL_ENABLED
        ),
        BrainModelCatalogEntry(
            role = BrainModelRole.MULTILINGUAL_BACKUP,
            packWireValue = "full",
            modelId = "qwen2.5-1.5b-instruct-q4-k-m",
            displayName = "Multilingual Backup",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            engineType = BrainModelEngineType.GGUF,
            modelFamilies = listOf("Qwen 2.5", "GGUF"),
            description = "Hindi, Hinglish, and regional-language backup role.",
            languageHints = listOf("hi", "hinglish", "multilingual", "regional"),
            notes = listOf(
                "Use this when the user speaks in Hindi, Hinglish, or another regional language.",
                "This is a backup role, not the primary installer recommendation."
            ),
            downloadUrl = BuildConfig.NOVA_LUNA_MULTILINGUAL_MODEL_DOWNLOAD_URL,
            sha256 = BuildConfig.NOVA_LUNA_MULTILINGUAL_MODEL_SHA256,
            expectedByteCount = BuildConfig.NOVA_LUNA_MULTILINGUAL_MODEL_BYTES.takeIf { it > 0L },
            minimumRamMb = 8192,
            minimumFreeStorageMb = 4096,
            sourceEnabled = BuildConfig.NOVA_LUNA_MULTILINGUAL_MODEL_ENABLED
        ),
        BrainModelCatalogEntry(
            role = BrainModelRole.LITE_FALLBACK,
            packWireValue = "lite",
            modelId = "qwen2.5-0.5b-instruct-q4-k-m",
            displayName = "Lightweight Fallback",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            engineType = BrainModelEngineType.GGUF,
            modelFamilies = listOf("Qwen 2.5", "GGUF"),
            description = "Tiny emergency fallback for simple commands and low-RAM devices.",
            languageHints = listOf("simple", "basic", "fallback", "low_ram"),
            notes = listOf(
                "Use this for simple commands or when memory is tight.",
                "Keep it ready as the emergency model when the core brain is unavailable."
            ),
            downloadUrl = BuildConfig.NOVA_LUNA_LIGHTWEIGHT_MODEL_DOWNLOAD_URL,
            sha256 = BuildConfig.NOVA_LUNA_LIGHTWEIGHT_MODEL_SHA256,
            expectedByteCount = BuildConfig.NOVA_LUNA_LIGHTWEIGHT_MODEL_BYTES.takeIf { it > 0L },
            minimumRamMb = 2048,
            minimumFreeStorageMb = 1024,
            sourceEnabled = BuildConfig.NOVA_LUNA_LIGHTWEIGHT_MODEL_ENABLED
        )
    ).map { it.normalized() }

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
