package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalRuntimeHealthCheckFlowTest {
    @Test
    fun runtimeBecomesReadyWhenVerifiedPrivateModelLoadsAndHealthCheckPasses() {
        val payload = "runtime ready payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )
        val runtimeBackend = RecordingBrainModelRuntime()

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = runtimeBackend
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val result = env.manager.inspectRuntime(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.READY, result.status)
            assertTrue(result.ready)
            assertFalse(result.unavailable)
            assertEquals(LocalRuntimeLoadStatus.READY, result.runtimeLoadResult!!.status)
            assertNotNull(result.healthCheckResult)
            assertTrue(result.healthCheckResult!!.passed)
            assertEquals(1, runtimeBackend.observedLoadRefs.size)
            assertEquals(1, runtimeBackend.observedHealthChecks.size)
            assertTrue(env.manager.getInstallStatus(ModelPackId.CORE).ready)
        }
    }

    @Test
    fun runtimeIsUnavailableWhenVerifiedModelFileIsMissing() {
        val payload = "missing runtime payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )
            env.storage.packFile(ModelPackId.CORE, pack.files.single().storageFileKey(pack.id)).delete()

            val result = env.manager.inspectRuntime(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.MISSING, result.status)
            assertFalse(result.ready)
            assertTrue(result.unavailable)
            assertEquals(LocalRuntimeLoadStatus.MISSING, result.runtimeLoadResult!!.status)
            assertTrue(result.reason.contains("missing", ignoreCase = true))
        }
    }

    @Test
    fun runtimeIsUnavailableWhenInstallStateIsNotVerified() {
        val payload = "not verified runtime payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            val file = env.storage.packFile(ModelPackId.CORE, pack.files.single().storageFileKey(pack.id))
            file.parentFile?.mkdirs()
            file.writeBytes(payload)
            env.stateStore.markDownloading(pack, message = "install in progress")

            val result = env.manager.inspectRuntime(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.NOT_READY, result.status)
            assertFalse(result.ready)
            assertTrue(result.unavailable)
            assertEquals(LocalModelLoadStatus.NOT_READY, result.loadResult.status)
            assertEquals(LocalRuntimeLoadStatus.NOT_READY, result.runtimeLoadResult!!.status)
        }
    }

    @Test
    fun runtimeIsUnavailableWhenLoaderFails() {
        val payload = "loader failure payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )
        val runtimeBackend = RecordingBrainModelRuntime(loadBehavior = { null })

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = runtimeBackend
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val result = env.manager.inspectRuntime(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.UNAVAILABLE, result.status)
            assertFalse(result.ready)
            assertTrue(result.unavailable)
            assertEquals(LocalRuntimeLoadStatus.UNAVAILABLE, result.runtimeLoadResult!!.status)
            assertEquals(1, runtimeBackend.observedLoadRefs.size)
            assertEquals(0, runtimeBackend.observedHealthChecks.size)
            assertEquals(ModelRuntimeStatus.UNAVAILABLE, env.coordinator.getInstallStatus(ModelPackId.CORE).runtimeStatus)
        }
    }

    @Test
    fun runtimeIsUnavailableWhenHealthCheckFails() {
        val payload = "health-check payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )
        val runtimeBackend = RecordingBrainModelRuntime(
            healthBehavior = { _, prompt ->
                RuntimeHealthCheckResult.failed(
                    prompt = prompt,
                    reason = "health-check ping failed"
                )
            }
        )

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = runtimeBackend
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val result = env.manager.inspectRuntime(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.UNAVAILABLE, result.status)
            assertFalse(result.ready)
            assertTrue(result.unavailable)
            assertEquals(LocalRuntimeLoadStatus.UNAVAILABLE, result.runtimeLoadResult!!.status)
            assertNotNull(result.healthCheckResult)
            assertFalse(result.healthCheckResult!!.passed)
            assertEquals(1, runtimeBackend.observedLoadRefs.size)
            assertEquals(1, runtimeBackend.observedHealthChecks.size)
        }
    }

    @Test
    fun runtimeFailureDoesNotCrashManagerOrCoordinator() {
        val payload = "runtime failure payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )
        val runtimeBackend = RecordingBrainModelRuntime(
            healthBehavior = { _, _ ->
                throw RuntimeException("boom")
            }
        )

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = runtimeBackend
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val result = env.manager.inspectRuntime(ModelPackId.CORE)
            val status = env.coordinator.getInstallStatus(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.UNAVAILABLE, result.status)
            assertFalse(result.ready)
            assertTrue(result.unavailable)
            assertEquals(ModelRuntimeStatus.UNAVAILABLE, status.runtimeStatus)
            assertFalse(status.ready)
        }
    }

    @Test
    fun loaderRefusesNonPrivatePath() {
        val runtimeBackend = RecordingBrainModelRuntime()
        val runtimeLoader = LocalRuntimeLoader(runtime = runtimeBackend)
        val tempRoot = Files.createTempDirectory("nova_luna_non_private_runtime_test").toFile()
        val outsideFile = File(tempRoot.parentFile, "rogue-runtime.gguf")
        outsideFile.writeText("rogue runtime payload")

        return try {
            val ref = LoadedModelRef(
                packId = ModelPackId.CORE,
                displayName = "Core",
                version = "v1",
                modelRootPath = tempRoot.path,
                modelFileKeys = listOf("rogue-runtime.gguf"),
                modelFiles = listOf(outsideFile)
            )

            val result = runtimeLoader.load(ref)

            assertEquals(LocalRuntimeLoadStatus.REFUSED, result.status)
            assertTrue(result.reason.contains("private app storage", ignoreCase = true))
            assertEquals(0, runtimeBackend.observedLoadRefs.size)
            assertEquals(0, runtimeBackend.observedHealthChecks.size)
        } finally {
            tempRoot.deleteRecursively()
            outsideFile.delete()
        }
    }

    @Test
    fun healthCheckUsesFakeBackendAndNoRealModelBinaryIsNeeded() {
        val payload = "fake backend runtime payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )
        val runtimeBackend = RecordingBrainModelRuntime()

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = runtimeBackend
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val result = env.manager.loadRuntime(ModelPackId.CORE)

            assertEquals(LocalRuntimeLoadStatus.READY, result.status)
            assertTrue(result.ready)
            assertNotNull(result.healthCheckResult)
            assertEquals("ping", result.healthCheckResult!!.prompt)
            assertEquals(1, runtimeBackend.observedLoadRefs.size)
            assertEquals(1, runtimeBackend.observedHealthChecks.size)
        }
    }

    @Test
    fun readyStateRequiresVerifiedFileSuccessfulLoadAndSuccessfulHealthCheck() {
        val payload = "ready-state payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = RecordingBrainModelRuntime()
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val readyResult = env.manager.inspectRuntime(ModelPackId.CORE)
            assertEquals(LocalRuntimeReadinessStatus.READY, readyResult.status)
            assertTrue(readyResult.ready)

            env.storage.packFile(ModelPackId.CORE, pack.files.single().storageFileKey(pack.id)).delete()
            val missingResult = env.manager.inspectRuntime(ModelPackId.CORE)
            assertFalse(missingResult.ready)
            assertEquals(LocalRuntimeReadinessStatus.MISSING, missingResult.status)
        }
    }

    @Test
    fun runtimeReadyStateCanBeQueriedThroughManagerAndReadinessApi() {
        val payload = "queryable runtime payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.LITE,
            fileName = "gemma-3-270m-q4.gguf",
            relativePath = "lite",
            payload = payload
        )

        withLocalRuntimeEnvironment(
            catalog = listOf(pack),
            runtimeBackend = RecordingBrainModelRuntime()
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("lite/gemma-3-270m-q4.gguf" to payload)
            )

            val managerResult = env.manager.inspectRuntime(
                DeviceCapabilitySnapshot(
                    totalRamMb = 2048,
                    freeStorageMb = 512,
                    androidVersion = 34,
                    cpuAbi = "arm64-v8a"
                )
            )
            val readinessResult = env.readinessChecker.inspect(ModelPackId.LITE)

            assertEquals(ModelPackId.LITE, managerResult.packId)
            assertEquals(LocalRuntimeReadinessStatus.READY, managerResult.status)
            assertTrue(managerResult.ready)
            assertTrue(env.manager.getInstallStatus(ModelPackId.LITE).ready)
            assertEquals(LocalRuntimeReadinessStatus.READY, readinessResult.status)
            assertTrue(readinessResult.ready)
        }
    }
}
