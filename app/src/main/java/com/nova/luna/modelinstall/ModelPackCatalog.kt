package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelCatalog

object ModelPackCatalog {
    private val packs: List<ModelPackSpec> = BrainModelCatalog.entries.map { it.toModelPackSpec() }

    fun defaultPacks(): List<ModelPackSpec> {
        return packs.map { it.normalized() }
    }

    fun find(packId: ModelPackId): ModelPackSpec? {
        return defaultPacks().firstOrNull { it.id == packId }
    }

    fun requirePack(packId: ModelPackId): ModelPackSpec {
        return find(packId) ?: error("Unknown model pack: $packId")
    }
}
