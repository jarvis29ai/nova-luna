package com.nova.luna.modelinstall

import java.io.File

interface ModelStorage {
    val rootDir: File

    fun downloadsDir(packId: ModelPackId): File
    fun modelsDir(packId: ModelPackId): File
    fun manifestFile(packId: ModelPackId): File
    fun stagedManifestFile(packId: ModelPackId): File
}

interface ModelRegistry {
    fun snapshot(): List<ModelManifest>
    fun find(packId: ModelPackId): ModelManifest?
    fun upsert(manifest: ModelManifest)
    fun remove(packId: ModelPackId)
    fun clear()
}
