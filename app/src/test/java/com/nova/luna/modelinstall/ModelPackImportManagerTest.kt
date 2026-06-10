package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelPackImportManagerTest {
    @Test
    fun importUsesDetectedFileSizeInsteadOfAPlaceholderMetadataSize() {
        val payload = "Nova Luna imported payload".toByteArray()
        val pack = ModelPackSpec(
            id = ModelPackId.CORE,
            displayName = "Core Brain",
            description = "Core import test pack.",
            requirement = ModelPackRequirement(
                minRamMb = 1,
                minFreeStorageMb = 1
            ),
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3n-E2B-it-int4.litertlm",
                    relativePath = "core",
                    sha256 = sha256Hex(payload),
                    byteCount = 123L
                )
            )
        ).normalized()

        val env = importEnvironment(pack)
        try {
            val sourceFile = File(env.sourceDir, pack.files.first().fileName)
            sourceFile.writeBytes(payload)

            val result = env.importer.importPack(ModelPackId.CORE, env.sourceDir)

            assertTrue(result.ready)
            assertEquals(ModelRuntimeStatus.READY, result.installStatus.runtimeStatus)
            assertTrue(result.warnings.any { it.contains("detected size", ignoreCase = true) })

            val storedManifest = env.registry.find(ModelPackId.CORE)
            assertTrue(storedManifest != null)
            assertEquals(payload.size.toLong(), storedManifest!!.files.first().byteCount)
        } finally {
            env.cleanup()
        }
    }

    @Test
    fun importDoesNotMarkReadyWhenShaVerificationFails() {
        val goodPayload = "Nova Luna verified payload".toByteArray()
        val badPayload = "Nova Luna corrupted payload".toByteArray()
        val pack = ModelPackSpec(
            id = ModelPackId.CORE,
            displayName = "Core Brain",
            description = "Core import test pack.",
            requirement = ModelPackRequirement(
                minRamMb = 1,
                minFreeStorageMb = 1
            ),
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3n-E2B-it-int4.litertlm",
                    relativePath = "core",
                    sha256 = sha256Hex(goodPayload),
                    byteCount = goodPayload.size.toLong()
                )
            )
        ).normalized()

        val env = importEnvironment(pack)
        try {
            val sourceFile = File(env.sourceDir, pack.files.first().fileName)
            sourceFile.writeBytes(badPayload)

            val result = env.importer.importPack(ModelPackId.CORE, env.sourceDir)

            assertFalse(result.ready)
            assertEquals(ModelRuntimeStatus.CORRUPT, result.installStatus.runtimeStatus)
            assertTrue(env.registry.readyPacks().isEmpty())
        } finally {
            env.cleanup()
        }
    }

    private fun importEnvironment(pack: ModelPackSpec): ImportTestEnvironment {
        val baseDir = Files.createTempDirectory("nova_luna_model_import_test").toFile()
        val sourceDir = Files.createTempDirectory("nova_luna_model_import_source").toFile()
        val storage = PrivateAppModelStorage.from(baseDir)
        val registry = LocalModelRegistry(storage)
        val stateStore = ModelRuntimeStateStore(storage)
        val coordinator = ModelInstallCoordinator(
            storage = storage,
            catalog = listOf(pack),
            registryOverride = registry,
            runtimeStateStoreOverride = stateStore
        )
        val importer = ModelPackImportManager(
            storage = storage,
            catalog = listOf(pack),
            registry = registry,
            runtimeStateStore = stateStore,
            coordinatorOverride = coordinator
        )

        return ImportTestEnvironment(
            baseDir = baseDir,
            sourceDir = sourceDir,
            storage = storage,
            registry = registry,
            stateStore = stateStore,
            importer = importer
        )
    }

    private data class ImportTestEnvironment(
        val baseDir: File,
        val sourceDir: File,
        val storage: PrivateAppModelStorage,
        val registry: LocalModelRegistry,
        val stateStore: ModelRuntimeStateStore,
        val importer: ModelPackImportManager
    ) {
        fun cleanup() {
            sourceDir.deleteRecursively()
            baseDir.deleteRecursively()
        }
    }
}
