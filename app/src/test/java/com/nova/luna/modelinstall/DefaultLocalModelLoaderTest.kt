package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DefaultLocalModelLoaderTest {
    @Test
    fun loadsVerifiedModelFromPrivateStorageAndKeepsPathsPrivate() {
        val payload = "local runtime payload".toByteArray()
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

            val backend = RecordingLocalRuntimeBackend(ready = true)
            val loader = DefaultLocalModelLoader(
                storage = env.storage,
                coordinator = env.coordinator,
                backend = backend
            )

            val result = loader.load(ModelPackId.CORE)

            assertEquals(LocalModelLoadStatus.READY, result.status)
            assertTrue(result.ready)
            assertEquals(1, backend.observedBatches.size)
            assertTrue(backend.observedBatches.single().all {
                it.canonicalPath.startsWith(env.storage.rootDir.canonicalPath)
            })
            assertTrue(result.modelFiles.all {
                it.canonicalPath.startsWith(env.storage.rootDir.canonicalPath)
            })
            assertNotNull(result.installStatus)
            assertTrue(env.coordinator.detectReadyModel(ModelPackId.CORE))
        }
    }

    @Test
    fun reportsNotReadyWhenInstallStateIsOnlyDownloading() {
        val payload = "downloading payload".toByteArray()
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
            env.stateStore.markDownloading(pack, message = "still downloading")

            val loader = DefaultLocalModelLoader(storage = env.storage, coordinator = env.coordinator)
            val result = loader.load(ModelPackId.CORE)

            assertEquals(LocalModelLoadStatus.NOT_READY, result.status)
            assertFalse(result.ready)
            assertEquals("still downloading", result.reason)
        }
    }

    @Test
    fun reportsMissingWhenVerifiedFileIsDeleted() {
        val payload = "verified payload".toByteArray()
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

            val loader = DefaultLocalModelLoader(storage = env.storage, coordinator = env.coordinator)
            val result = loader.load(ModelPackId.CORE)

            assertEquals("install=${result.installStatus?.runtimeStatus} load=${result.status} reason=${result.reason}", LocalModelLoadStatus.MISSING, result.status)
            assertFalse(result.ready)
            assertTrue(result.reason.contains("missing", ignoreCase = true))
        }
    }

    @Test
    fun reportsFailedWhenBackendRefusesToLoad() {
        val payload = "backend failure payload".toByteArray()
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

            val backend = RecordingLocalRuntimeBackend(ready = false)
            val loader = DefaultLocalModelLoader(
                storage = env.storage,
                coordinator = env.coordinator,
                backend = backend
            )
            val result = loader.load(ModelPackId.CORE)

            assertEquals(LocalModelLoadStatus.FAILED, result.status)
            assertFalse(result.ready)
            assertTrue(result.reason.contains("refused", ignoreCase = true))
            assertEquals(1, backend.observedBatches.size)
        }
    }

    @Test
    fun reportsCorruptWhenInstallStateIsCorrupt() {
        val payload = "corrupt payload".toByteArray()
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
            env.stateStore.markCorrupt(
                pack = pack,
                message = "sha failed",
                corruptFileCount = 1
            )

            val loader = DefaultLocalModelLoader(storage = env.storage, coordinator = env.coordinator)
            val result = loader.load(ModelPackId.CORE)

            assertEquals(LocalModelLoadStatus.CORRUPT, result.status)
            assertFalse(result.ready)
            assertTrue(result.reason.contains("sha", ignoreCase = true))
        }
    }
}
