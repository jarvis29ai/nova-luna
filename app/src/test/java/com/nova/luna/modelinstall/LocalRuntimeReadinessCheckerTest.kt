package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuntimeReadinessCheckerTest {
    @Test
    fun runtimeBecomesReadyOnlyAfterVerifiedInstallAndSuccessfulLoad() {
        val payload = "runtime ready payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.FULL,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "full/core",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("full/core/gemma-3n-q4.gguf" to payload)
            )

            val readinessChecker = LocalRuntimeReadinessChecker(
                storage = env.storage,
                coordinator = env.coordinator,
                loader = DefaultLocalModelLoader(
                    storage = env.storage,
                    coordinator = env.coordinator,
                    backend = RecordingLocalRuntimeBackend(ready = true)
                )
            )

            val result = readinessChecker.inspect(
                DeviceCapabilitySnapshot(
                    totalRamMb = 8192,
                    freeStorageMb = 4096,
                    androidVersion = 34,
                    cpuAbi = "arm64-v8a"
                )
            )

            assertEquals(ModelPackId.FULL, result.packId)
            assertEquals(LocalRuntimeReadinessStatus.READY, result.status)
            assertTrue(result.ready)
            assertEquals(LocalModelLoadStatus.READY, result.loadResult.status)
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.FULL))
        }
    }

    @Test
    fun runtimeStaysNotReadyWhenInstallStateIsNotVerified() {
        val payload = "not verified payload".toByteArray()
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

            val readinessChecker = LocalRuntimeReadinessChecker(
                storage = env.storage,
                coordinator = env.coordinator,
                loader = DefaultLocalModelLoader(storage = env.storage, coordinator = env.coordinator)
            )

            val result = readinessChecker.inspect(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.NOT_READY, result.status)
            assertFalse(result.ready)
            assertEquals(LocalModelLoadStatus.NOT_READY, result.loadResult.status)
        }
    }

    @Test
    fun runtimeReportsMissingWhenVerifiedFileDisappears() {
        val payload = "missing after install".toByteArray()
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

            val readinessChecker = LocalRuntimeReadinessChecker(
                storage = env.storage,
                coordinator = env.coordinator
            )

            val result = readinessChecker.inspect(ModelPackId.CORE)

            assertEquals(LocalRuntimeReadinessStatus.MISSING, result.status)
            assertFalse(result.ready)
            assertTrue(result.reason.contains("missing", ignoreCase = true))
        }
    }

    @Test
    fun capabilityCheckerChoosesPackWithoutMarkingRuntimeReadyTooEarly() {
        val payload = "capability payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.LITE,
            fileName = "gemma-3-270m-q4.gguf",
            relativePath = "lite",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("lite/gemma-3-270m-q4.gguf" to payload)
            )

            val readinessChecker = LocalRuntimeReadinessChecker(
                storage = env.storage,
                coordinator = env.coordinator,
                loader = DefaultLocalModelLoader(
                    storage = env.storage,
                    coordinator = env.coordinator,
                    backend = RecordingLocalRuntimeBackend(ready = true)
                )
            )

            val selection = readinessChecker.select(
                DeviceCapabilitySnapshot(
                    totalRamMb = 2048,
                    freeStorageMb = 512,
                    androidVersion = 34,
                    cpuAbi = "arm64-v8a"
                )
            )

            val result = readinessChecker.inspect(
                DeviceCapabilitySnapshot(
                    totalRamMb = 2048,
                    freeStorageMb = 512,
                    androidVersion = 34,
                    cpuAbi = "arm64-v8a"
                )
            )

            assertEquals(ModelPackId.LITE, selection.packId)
            assertEquals(ModelPackId.LITE, result.packId)
            assertTrue(result.ready)
        }
    }
}
