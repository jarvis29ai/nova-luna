package com.nova.luna.modelinstall

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

internal data class FakeResponse(
    val responseCode: Int,
    val body: ByteArray = ByteArray(0),
    val message: String = when (responseCode) {
        in 200..299 -> "OK"
        500 -> "Internal Server Error"
        else -> "HTTP $responseCode"
    }
)

internal data class Phase8MaintenanceEnvironment(
    val baseDir: File,
    val storage: PrivateAppModelStorage,
    val registry: LocalModelRegistry,
    val stateStore: ModelRuntimeStateStore,
    val downloadStateStore: DownloadStateStore,
    val sourceProvider: ModelDownloadSourceProvider,
    val coordinator: ModelInstallCoordinator,
    val healthScanner: ModelHealthScanner,
    val cleanupPolicy: ModelCleanupPolicy,
    val updateManager: ModelUpdateManager,
    val repairManager: ModelRepairManager,
    val manager: DefaultModelManager
)

internal fun <T> withMaintenanceEnvironment(
    catalog: List<ModelPackSpec> = ModelPackCatalog.defaultPacks(),
    responses: Map<String, FakeResponse> = emptyMap(),
    block: (Phase8MaintenanceEnvironment) -> T
): T {
    val baseDir = Files.createTempDirectory("nova_luna_phase8_model_install_test").toFile()
    val storage = PrivateAppModelStorage.from(baseDir)
    val registry = LocalModelRegistry(storage)
    val stateStore = ModelRuntimeStateStore(storage)
    val downloadStateStore = DownloadStateStore(storage)
    val sourceProvider = ModelDownloadSourceProvider(
        catalog = catalog,
        baseDownloadUrl = "http://local"
    )
    val verifier = Sha256ModelVerifier()
    val downloader = HttpModelDownloader(
        storage = storage,
        stateStore = downloadStateStore,
        registry = registry,
        verifier = verifier,
        connectionFactory = { url -> FakeHttpURLConnection(url, responses[url.path] ?: error("Unexpected path: ${url.path}")) }
    )
    val coordinator = ModelInstallCoordinator(
        storage = storage,
        catalog = catalog,
        downloadSourceProviderOverride = sourceProvider,
        downloaderOverride = downloader,
        verifierOverride = verifier,
        registryOverride = registry,
        runtimeStateStoreOverride = stateStore
    )
    val env = Phase8MaintenanceEnvironment(
        baseDir = baseDir,
        storage = storage,
        registry = registry,
        stateStore = stateStore,
        downloadStateStore = downloadStateStore,
        sourceProvider = sourceProvider,
        coordinator = coordinator,
        healthScanner = coordinator.healthScanner,
        cleanupPolicy = coordinator.cleanupPolicy,
        updateManager = coordinator.updateManager,
        repairManager = coordinator.repairManager,
        manager = DefaultModelManager(coordinator)
    )

    return try {
        block(env)
    } finally {
        baseDir.deleteRecursively()
    }
}

internal fun seedReadyPack(
    env: Phase8MaintenanceEnvironment,
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

internal fun payloadFile(
    fileName: String,
    relativePath: String,
    payload: ByteArray
): ModelFileSpec {
    return ModelFileSpec(
        fileName = fileName,
        relativePath = relativePath,
        sha256 = sha256Hex(payload),
        byteCount = payload.size.toLong()
    ).normalized()
}

internal fun fileBytes(text: String): ByteArray {
    return text.toByteArray()
}

private class FakeHttpURLConnection(
    url: URL,
    private val response: FakeResponse
) : HttpURLConnection(url) {
    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun connect() {
        connected = true
    }

    override fun getInputStream(): InputStream {
        if (response.responseCode !in 200..299) {
            throw IOException("Cannot read body for HTTP ${response.responseCode}")
        }
        return ByteArrayInputStream(response.body)
    }

    override fun getResponseCode(): Int {
        return response.responseCode
    }

    override fun getResponseMessage(): String {
        return response.message
    }

    override fun getContentLengthLong(): Long {
        return response.body.size.toLong()
    }
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
