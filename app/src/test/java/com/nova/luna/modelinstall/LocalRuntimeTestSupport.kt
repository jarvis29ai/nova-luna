package com.nova.luna.modelinstall

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

internal data class LocalRuntimeTestEnvironment(
    val baseDir: File,
    val storage: PrivateAppModelStorage,
    val registry: LocalModelRegistry,
    val stateStore: ModelRuntimeStateStore,
    val coordinator: ModelInstallCoordinator,
    val runtimeBackend: BrainModelRuntime,
    val runtimeLoader: LocalRuntimeLoader,
    val readinessChecker: LocalRuntimeReadinessChecker,
    val manager: DefaultModelManager
)

internal class RecordingLocalRuntimeBackend(
    private val ready: Boolean = true
) : LocalRuntimeBackend {
    val observedBatches: MutableList<List<File>> = mutableListOf()

    override fun load(modelFiles: List<File>): Boolean {
        observedBatches += modelFiles.map { it.canonicalFile }
        return ready
    }
}

internal class RecordingBrainModelRuntime(
    private val loadBehavior: (LoadedModelRef) -> LoadedModel? = { LoadedModel(it) },
    private val healthBehavior: (LoadedModel, String) -> RuntimeHealthCheckResult = { _, prompt ->
        RuntimeHealthCheckResult.passed(prompt = prompt, response = "pong")
    }
) : BrainModelRuntime {
    val observedLoadRefs: MutableList<LoadedModelRef> = mutableListOf()
    val observedHealthChecks: MutableList<Pair<LoadedModel, String>> = mutableListOf()

    override fun load(modelRef: LoadedModelRef): LoadedModel? {
        observedLoadRefs += modelRef
        return loadBehavior(modelRef)
    }

    override fun healthCheck(
        loadedModel: LoadedModel,
        prompt: String
    ): RuntimeHealthCheckResult {
        observedHealthChecks += loadedModel to prompt
        return healthBehavior(loadedModel, prompt)
    }
}

internal inline fun <T> withLocalRuntimeEnvironment(
    catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    runtimeBackend: BrainModelRuntime = NoOpBrainModelRuntime,
    block: (LocalRuntimeTestEnvironment) -> T
): T {
    val baseDir = Files.createTempDirectory("nova_luna_local_runtime_test").toFile()
    val storage = PrivateAppModelStorage.from(baseDir)
    val registry = LocalModelRegistry(storage)
    val stateStore = ModelRuntimeStateStore(storage)
    val coordinator = ModelInstallCoordinator(
        storage = storage,
        catalog = catalog,
        registryOverride = registry,
        runtimeStateStoreOverride = stateStore
    )
    val runtimeLoader = LocalRuntimeLoader(runtime = runtimeBackend)
    val readinessChecker = LocalRuntimeReadinessChecker(
        storage = storage,
        coordinator = coordinator,
        runtimeLoader = runtimeLoader
    )
    val manager = DefaultModelManager(
        coordinator = coordinator,
        runtimeReadinessChecker = readinessChecker
    )
    val env = LocalRuntimeTestEnvironment(
        baseDir = baseDir,
        storage = storage,
        registry = registry,
        stateStore = stateStore,
        coordinator = coordinator,
        runtimeBackend = runtimeBackend,
        runtimeLoader = runtimeLoader,
        readinessChecker = readinessChecker,
        manager = manager
    )

    return try {
        block(env)
    } finally {
        baseDir.deleteRecursively()
    }
}

internal fun singleFilePack(
    packId: ModelPackId,
    fileName: String,
    relativePath: String,
    payload: ByteArray
): ModelPackSpec {
    return multiFilePack(
        packId = packId,
        files = listOf(
            ModelFileSpec(
                fileName = fileName,
                relativePath = relativePath,
                sha256 = sha256Hex(payload),
                byteCount = payload.size.toLong()
            )
        )
    )
}

internal fun multiFilePack(
    packId: ModelPackId,
    files: List<ModelFileSpec>
): ModelPackSpec {
    return ModelPackSpec(
        id = packId,
        displayName = packId.displayName,
        description = "Test pack for ${packId.displayName}",
        requirement = ModelPackRequirement(
            minRamMb = 1,
            minFreeStorageMb = 1
        ),
        files = files
    ).normalized()
}

internal fun seedReadyPack(
    env: LocalRuntimeTestEnvironment,
    pack: ModelPackSpec,
    payloads: Map<String, ByteArray>
) {
    val installedFiles = pack.files.map { fileSpec ->
        val normalized = fileSpec.forPackStorage(pack.id)
        val storageKey = normalized.storageFileKey(pack.id)
        val payload = payloads[storageKey]
            ?: payloads[fileSpec.originalFileKey()]
            ?: payloads[normalized.originalFileKey()]
            ?: error("Missing payload for $storageKey")
        val file = env.storage.packFile(pack.id, storageKey)
        file.parentFile?.mkdirs()
        file.writeBytes(payload)
        normalized.copy(byteCount = payload.size.toLong())
    }

    env.registry.upsert(
        pack.toReadyManifest(
            installedFiles = installedFiles,
            installedAtEpochMs = 1234L
        )
    )
    env.stateStore.markReady(
        pack = pack,
        registryConfirmed = true,
        verificationPassed = true,
        expectedFileCount = pack.files.size,
        verifiedFileCount = pack.files.size,
        manifestPath = env.storage.manifestFile(pack.id).path,
        modelRootPath = env.storage.modelsDir(pack.id).path,
        message = "ready"
    )
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun ModelFileSpec.originalFileKey(): String {
    val relative = relativePath.trim().trim('/')
    val name = fileName.trim()
    return if (relative.isBlank()) {
        name
    } else {
        "$relative/$name"
    }
}
