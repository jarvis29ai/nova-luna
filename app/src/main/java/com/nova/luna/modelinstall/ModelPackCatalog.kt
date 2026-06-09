package com.nova.luna.modelinstall

object ModelPackCatalog {
    private val packs: List<ModelPackSpec> = listOf(
        ModelPackSpec(
            id = ModelPackId.LITE,
            displayName = "Lite",
            description = "Smallest offline brain pack for low-memory phones.",
            requirement = ModelPackRequirement(
                minRamMb = 2048,
                minFreeStorageMb = 512
            ),
            files = listOf(
                ModelFileSpec(fileName = "gemma-3-270m-q4.gguf", relativePath = "lite")
            ),
            notes = listOf(
                "Best for entry-level phones.",
                "Keeps the offline brain available when memory is tight."
            )
        ),
        ModelPackSpec(
            id = ModelPackId.CORE,
            displayName = "Core",
            description = "Balanced core pack for everyday offline reasoning.",
            requirement = ModelPackRequirement(
                minRamMb = 4096,
                minFreeStorageMb = 1536,
                requiredAbi = "arm64"
            ),
            files = listOf(
                ModelFileSpec(fileName = "gemma-3n-q4.gguf", relativePath = "core")
            ),
            notes = listOf(
                "Good default choice for modern phones.",
                "Designed to keep the main brain role available offline."
            )
        ),
        ModelPackSpec(
            id = ModelPackId.FULL,
            displayName = "Full",
            description = "Largest pack with core and multilingual support.",
            requirement = ModelPackRequirement(
                minRamMb = 6144,
                minFreeStorageMb = 2560,
                requiredAbi = "arm64"
            ),
            files = listOf(
                ModelFileSpec(fileName = "gemma-3n-q4.gguf", relativePath = "full/core"),
                ModelFileSpec(fileName = "qwen-3-small-q4.gguf", relativePath = "full/multilingual")
            ),
            notes = listOf(
                "Best if the device has plenty of RAM and storage.",
                "Useful when the multilingual backup should stay available offline."
            )
        )
    )

    fun defaultPacks(): List<ModelPackSpec> {
        return packs.map { it.normalized() }.sortedBy { it.id.priority }
    }

    fun find(packId: ModelPackId): ModelPackSpec? {
        return defaultPacks().firstOrNull { it.id == packId }
    }

    fun requirePack(packId: ModelPackId): ModelPackSpec {
        return find(packId) ?: error("Unknown model pack: $packId")
    }
}
