package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class ModelInstallCoordinatorTest {
    private data class FakeResponse(
        val responseCode: Int,
        val body: ByteArray = ByteArray(0),
        val message: String = when (responseCode) {
            in 200..299 -> "OK"
            500 -> "Internal Server Error"
            else -> "HTTP $responseCode"
        }
    )

    private data class TestEnvironment(
        val baseDir: File,
        val storage: PrivateAppModelStorage,
        val stateStore: ModelRuntimeStateStore,
        val registry: LocalModelRegistry,
        val downloadStateStore: DownloadStateStore,
        val sourceProvider: ModelDownloadSourceProvider,
        val coordinator: ModelInstallCoordinator
    )

    private var responses: Map<String, FakeResponse> = emptyMap()

    @Test
    fun installFlowMovesFromIdleDownloadingVerifyingToReadyAfterVerifiedDownload() {
        val corePayload = "full core payload".toByteArray()
        val multilingualPayload = "full multilingual payload".toByteArray()
        val pack = multiFilePack(
            packId = ModelPackId.FULL,
            files = listOf(
                packFile(
                    fileName = "gemma-3n-q4.gguf",
                    relativePath = "full/core",
                    payload = corePayload
                ),
                packFile(
                    fileName = "qwen-3-small-q4.gguf",
                    relativePath = "full/multilingual",
                    payload = multilingualPayload
                )
            )
        )
        responses = mapOf(
            "/full/core/gemma-3n-q4.gguf" to FakeResponse(200, corePayload),
            "/full/multilingual/qwen-3-small-q4.gguf" to FakeResponse(200, multilingualPayload)
        )

        withEnvironment(listOf(pack)) { env ->
            val statuses = mutableListOf<ModelRuntimeStatus>()
            val result = env.coordinator.startInstall(ModelPackId.FULL) { snapshot ->
                statuses += snapshot.runtimeStatus
            }

            assertEquals(ModelRuntimeStatus.READY, result.runtimeStatus)
            assertTrue(statuses.contains(ModelRuntimeStatus.DOWNLOADING))
            assertTrue(statuses.contains(ModelRuntimeStatus.VERIFYING))
            assertEquals(ModelRuntimeStatus.READY, statuses.last())
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.FULL))
            assertTrue(env.registry.hasReadyPack(ModelPackId.FULL))
            assertTrue(env.storage.packFile(ModelPackId.FULL, "core/gemma-3n-q4.gguf").exists())
            assertTrue(env.storage.packFile(ModelPackId.FULL, "multilingual/qwen-3-small-q4.gguf").exists())
        }
    }

    @Test
    fun coreFullAndLitePacksCanAllStayReadyAtTheSameTime() {
        val corePayload = "core simultaneous payload".toByteArray()
        val multilingualPayload = "multilingual simultaneous payload".toByteArray()
        val litePayload = "lite simultaneous payload".toByteArray()
        val corePack = singleFilePack(
            ModelPackId.CORE,
            "gemma-3n-E2B-it-int4.litertlm",
            "core",
            corePayload
        )
        val multilingualPack = singleFilePack(
            ModelPackId.FULL,
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "full",
            multilingualPayload
        )
        val litePack = singleFilePack(
            ModelPackId.LITE,
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "lite",
            litePayload
        )
        responses = mapOf(
            "/core/gemma-3n-E2B-it-int4.litertlm" to FakeResponse(200, corePayload),
            "/full/qwen2.5-1.5b-instruct-q4_k_m.gguf" to FakeResponse(200, multilingualPayload),
            "/lite/qwen2.5-0.5b-instruct-q4_k_m.gguf" to FakeResponse(200, litePayload)
        )

        withEnvironment(listOf(corePack, multilingualPack, litePack)) { env ->
            assertEquals(ModelRuntimeStatus.READY, env.coordinator.startInstall(ModelPackId.CORE).runtimeStatus)
            assertEquals(ModelRuntimeStatus.READY, env.coordinator.startInstall(ModelPackId.FULL).runtimeStatus)
            assertEquals(ModelRuntimeStatus.READY, env.coordinator.startInstall(ModelPackId.LITE).runtimeStatus)

            assertEquals(
                listOf(ModelPackId.LITE, ModelPackId.CORE, ModelPackId.FULL),
                env.registry.readyPacks()
            )
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.CORE))
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.FULL))
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.LITE))
        }
    }

    @Test
    fun failedDownloadStoresFailedStateAndDoesNotMarkModelReady() {
        val payload = "ignored".toByteArray()
        val pack = singleFilePack(ModelPackId.CORE, "gemma-3n-q4.gguf", "core", payload)
        responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(500, payload))

        withEnvironment(listOf(pack)) { env ->
            val result = env.coordinator.startInstall(ModelPackId.CORE)

            assertEquals(ModelRuntimeStatus.FAILED, result.runtimeStatus)
            assertFalse(env.coordinator.detectReadyModel(ModelPackId.CORE))
            assertEquals(ModelRuntimeStatus.FAILED, env.stateStore.find(ModelPackId.CORE)!!.runtimeStatus)
            assertFalse(env.registry.hasReadyPack(ModelPackId.CORE))
        }
    }

    @Test
    fun failedShaVerificationStoresCorruptStateAndDoesNotMarkModelReady() {
        val expectedPayload = "expected payload".toByteArray()
        val wrongPayload = "expected payloae".toByteArray()
        val pack = singleFilePack(ModelPackId.CORE, "gemma-3n-q4.gguf", "core", expectedPayload)
        responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, wrongPayload))

        withEnvironment(listOf(pack)) { env ->
            val result = env.coordinator.startInstall(ModelPackId.CORE)

            assertEquals(ModelRuntimeStatus.CORRUPT, result.runtimeStatus)
            assertFalse(env.coordinator.detectReadyModel(ModelPackId.CORE))
            assertEquals(ModelRuntimeStatus.CORRUPT, env.stateStore.find(ModelPackId.CORE)!!.runtimeStatus)
            assertFalse(env.registry.hasReadyPack(ModelPackId.CORE))
        }
    }

    @Test
    fun retryAfterFailureCanReachReadyState() {
        val payload = "retryable payload".toByteArray()
        val pack = singleFilePack(ModelPackId.CORE, "gemma-3n-q4.gguf", "core", payload)
        responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(500, payload))

        withEnvironment(listOf(pack)) { env ->
            val first = env.coordinator.startInstall(ModelPackId.CORE)
            assertEquals(ModelRuntimeStatus.FAILED, first.runtimeStatus)

            responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, payload))
            val second = env.coordinator.retryFailedInstall(ModelPackId.CORE)

            assertEquals(ModelRuntimeStatus.READY, second.runtimeStatus)
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.CORE))
            assertEquals(ModelRuntimeStatus.READY, env.stateStore.find(ModelPackId.CORE)!!.runtimeStatus)
        }
    }

    @Test
    fun runtimeStateSurvivesRoundTripThroughStore() {
        withEnvironment(emptyList()) { env ->
            val state = ModelRuntimeState(
                packId = ModelPackId.LITE,
                version = "v1.2.3",
                displayName = "Lite",
                runtimeStatus = ModelRuntimeStatus.READY,
                installState = ModelInstallState.READY,
                registryConfirmed = true,
                verificationPassed = true,
                ready = true,
                expectedFileCount = 1,
                verifiedFileCount = 1,
                missingFileCount = 0,
                corruptFileCount = 0,
                installedAtEpochMs = 1234L,
                updatedAtEpochMs = 5678L,
                manifestPath = env.storage.manifestFile(ModelPackId.LITE).path,
                modelRootPath = env.storage.modelsDir(ModelPackId.LITE).path,
                message = "ready"
            )

            env.stateStore.upsert(state)
            val reloaded = ModelRuntimeStateStore(env.storage).find(ModelPackId.LITE)

            assertNotNull(reloaded)
            assertEquals(state.normalized(), reloaded)
        }
    }

    @Test
    fun missingModelFileIsReportedAsNotReady() {
        val payload = "missing-file payload".toByteArray()
        val pack = singleFilePack(ModelPackId.CORE, "gemma-3n-q4.gguf", "core", payload)
        responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, payload))

        withEnvironment(listOf(pack)) { env ->
            val result = env.coordinator.startInstall(ModelPackId.CORE)
            assertEquals(ModelRuntimeStatus.READY, result.runtimeStatus)

            env.storage.packFile(ModelPackId.CORE, "gemma-3n-q4.gguf").delete()

            val status = env.coordinator.getInstallStatus(ModelPackId.CORE)

            assertEquals(ModelRuntimeStatus.MISSING, status.runtimeStatus)
            assertFalse(env.coordinator.detectReadyModel(ModelPackId.CORE))
            assertTrue(env.coordinator.detectMissingOrCorruptModel(ModelPackId.CORE))
        }
    }

    @Test
    fun coordinatorUsesPrivateAppModelStorageOnly() {
        val payload = "private storage payload".toByteArray()
        val pack = singleFilePack(ModelPackId.CORE, "gemma-3n-q4.gguf", "core", payload)
        responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, payload))

        withEnvironment(listOf(pack)) { env ->
            env.coordinator.startInstall(ModelPackId.CORE)

            val rootPath = env.storage.rootDir.canonicalPath
            assertTrue(env.storage.packFile(ModelPackId.CORE, "gemma-3n-q4.gguf").canonicalPath.startsWith(rootPath))
            assertTrue(env.stateStore.stateFile.canonicalPath.startsWith(rootPath))
            assertTrue(env.storage.registryIndexFile.canonicalPath.startsWith(rootPath))
            assertTrue(File(env.storage.rootDir, "download_state.json").canonicalPath.startsWith(rootPath))
        }
    }

    @Test
    fun noModelPackIsRuntimeReadyUntilVerificationAndStateBothPass() {
        val payload = "state confirmation payload".toByteArray()
        val pack = singleFilePack(ModelPackId.CORE, "gemma-3n-q4.gguf", "core", payload)
        responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, payload))

        withEnvironment(listOf(pack)) { env ->
            val result = env.coordinator.startInstall(ModelPackId.CORE)
            assertEquals(ModelRuntimeStatus.READY, result.runtimeStatus)

            env.registry.remove(ModelPackId.CORE)

            val status = env.coordinator.getInstallStatus(ModelPackId.CORE)

            assertEquals(ModelRuntimeStatus.MISSING, status.runtimeStatus)
            assertFalse(status.ready)
            assertFalse(env.coordinator.detectReadyModel(ModelPackId.CORE))
        }
    }

    private inline fun <T> withEnvironment(
        catalog: List<ModelPackSpec>,
        block: (TestEnvironment) -> T
    ): T {
        val baseDir = Files.createTempDirectory("nova_luna_model_install_coordinator_test").toFile()
        val env = buildEnvironment(baseDir, catalog)
        return try {
            block(env)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    private fun buildEnvironment(
        baseDir: File,
        catalog: List<ModelPackSpec>
    ): TestEnvironment {
        val storage = PrivateAppModelStorage.from(baseDir)
        val stateStore = ModelRuntimeStateStore(storage)
        val registry = LocalModelRegistry(storage)
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
            connectionFactory = { url ->
                FakeHttpURLConnection(url, responses[url.path] ?: error("Unexpected path: ${url.path}"))
            }
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

        return TestEnvironment(
            baseDir = baseDir,
            storage = storage,
            stateStore = stateStore,
            registry = registry,
            downloadStateStore = downloadStateStore,
            sourceProvider = sourceProvider,
            coordinator = coordinator
        )
    }

    private fun singleFilePack(
        packId: ModelPackId,
        fileName: String,
        relativePath: String,
        payload: ByteArray
    ): ModelPackSpec {
        return multiFilePack(
            packId = packId,
            files = listOf(packFile(fileName, relativePath, payload))
        )
    }

    private fun multiFilePack(
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

    private fun packFile(
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

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
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
}
