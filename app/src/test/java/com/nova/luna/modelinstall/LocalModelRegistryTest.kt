package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalModelRegistryTest {
    @Test
    fun savesLoadsAndRemovesManifests() {
        val baseDir = Files.createTempDirectory("nova_luna_model_registry_test").toFile()
        val storage = PrivateAppModelStorage.from(baseDir)
        val registry = LocalModelRegistry(storage)

        val manifest = ModelManifest(
            packId = ModelPackId.CORE,
            version = "1.0.0",
            state = ModelInstallState.READY,
            installedAtEpochMs = 1234L,
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3n-q4.gguf",
                    relativePath = "core",
                    sha256 = "abc123",
                    byteCount = 42L
                )
            )
        )

        registry.upsert(manifest)

        assertTrue(registry.hasInstalledPack(ModelPackId.CORE))
        assertTrue(registry.hasReadyPack(ModelPackId.CORE))
        assertEquals(listOf(ModelPackId.CORE), registry.readyPacks())
        assertEquals(manifest.normalized(), registry.find(ModelPackId.CORE))

        registry.remove(ModelPackId.CORE)
        assertFalse(registry.hasInstalledPack(ModelPackId.CORE))
        assertTrue(registry.snapshot().isEmpty())
    }
}
