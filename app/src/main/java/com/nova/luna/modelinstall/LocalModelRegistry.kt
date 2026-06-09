package com.nova.luna.modelinstall

class LocalModelRegistry(
    private val storage: PrivateAppModelStorage
) : ModelRegistry {
    private val lock = Any()

    private val registryFile
        get() = storage.registryIndexFile

    override fun snapshot(): List<ModelManifest> {
        return synchronized(lock) { readFromDisk() }
    }

    override fun find(packId: ModelPackId): ModelManifest? {
        return snapshot().firstOrNull { it.packId == packId }
    }

    override fun upsert(manifest: ModelManifest) {
        synchronized(lock) {
            val normalized = manifest.normalized()
            val updated = readFromDisk()
                .filterNot { it.packId == normalized.packId }
                .plus(normalized)
                .sortedBy { it.packId.priority }
            writeToDisk(updated)
        }
    }

    override fun remove(packId: ModelPackId) {
        synchronized(lock) {
            val updated = readFromDisk().filterNot { it.packId == packId }
            writeToDisk(updated)
        }
    }

    override fun clear() {
        synchronized(lock) {
            if (registryFile.exists()) {
                registryFile.delete()
            }
        }
    }

    fun hasInstalledPack(packId: ModelPackId): Boolean {
        return find(packId)?.state in setOf(ModelInstallState.INSTALLED, ModelInstallState.READY)
    }

    fun hasReadyPack(packId: ModelPackId): Boolean {
        return find(packId)?.state == ModelInstallState.READY
    }

    fun installedPacks(): List<ModelPackId> {
        return snapshot()
            .filter { it.state in setOf(ModelInstallState.INSTALLED, ModelInstallState.READY) }
            .map { it.packId }
    }

    fun readyPacks(): List<ModelPackId> {
        return snapshot()
            .filter { it.state == ModelInstallState.READY }
            .map { it.packId }
    }

    private fun readFromDisk(): List<ModelManifest> {
        if (!registryFile.exists()) return emptyList()

        val text = registryFile.readText().trim()
        if (text.isBlank()) return emptyList()

        val root = SimpleJson.parseObject(text)
        val manifests = root.jsonArray("manifests")
        return buildList {
            for (item in manifests) {
                @Suppress("UNCHECKED_CAST")
                add(ModelManifest.fromJsonMap(item as? Map<String, Any?>
                    ?: error("Expected manifest object in registry")))
            }
        }
    }

    private fun writeToDisk(manifests: List<ModelManifest>) {
        registryFile.parentFile?.mkdirs()
        val root = linkedMapOf(
            "version" to 1,
            "manifests" to manifests.map { it.toJsonValue() }
        )
        registryFile.writeText(SimpleJson.stringify(root, indentSpaces = 2))
    }
}
