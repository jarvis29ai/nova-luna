package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadSourceProviderTest {
    @Test
    fun defaultCatalogStaysBlockedWhenNoSourceIsConfigured() {
        val provider = ModelDownloadSourceProvider()

        assertFalse(provider.isConfigured(ModelPackId.LITE))
        assertEquals("SOURCE_NOT_CONFIGURED", provider.configurationMessage(ModelPackId.LITE))
        assertEquals(
            ModelSourceConfigurationState.SOURCE_NOT_CONFIGURED,
            provider.configurationStatus(ModelPackId.LITE).state
        )
        assertNull(provider.sourceFor(ModelPackId.LITE))
        assertTrue(provider.sourcesFor(ModelPackId.LITE).isEmpty())
    }

    @Test
    fun configuredManifestExposesRealDownloadFields() {
        val pack = ModelPackCatalog.requirePack(ModelPackId.LITE)
        val sourceEntry = ModelSourceEntry(
            role = BrainModelRole.LITE_FALLBACK,
            displayName = pack.displayName,
            fileName = pack.files.first().fileName,
            relativePath = pack.files.first().relativePath,
            downloadUrl = "https://example.com/models/lite.gguf",
            expectedSha256 = "abc123",
            expectedByteCount = 123L,
            enabled = true,
            minimumRamMb = pack.requirement.minRamMb,
            minimumFreeStorageMb = pack.requirement.minFreeStorageMb
        ).normalized()
        val provider = ModelDownloadSourceProvider(
            catalog = listOf(pack),
            sourceManifest = ModelSourceManifest(entries = listOf(sourceEntry))
        )

        assertTrue(provider.isConfigured(ModelPackId.LITE))
        assertEquals("Download available.", provider.configurationMessage(ModelPackId.LITE))

        val source = provider.sourceFor(ModelPackId.LITE)
        assertNotNull(source)
        assertEquals(sourceEntry.downloadUrl, source!!.downloadUrl)
        assertEquals(sourceEntry.expectedSha256, source.expectedSha256)
        assertEquals(sourceEntry.expectedByteCount, source.expectedByteCount)
    }

    @Test
    fun configuredManifestReportsHashMissingWhenShaIsAbsent() {
        val pack = ModelPackCatalog.requirePack(ModelPackId.CORE)
        val sourceEntry = ModelSourceEntry(
            role = BrainModelRole.CORE_BRAIN,
            displayName = pack.displayName,
            fileName = pack.files.first().fileName,
            relativePath = pack.files.first().relativePath,
            downloadUrl = "https://example.com/models/core.litertlm",
            expectedSha256 = null,
            expectedByteCount = 123L,
            enabled = true,
            minimumRamMb = pack.requirement.minRamMb,
            minimumFreeStorageMb = pack.requirement.minFreeStorageMb
        ).normalized()
        val provider = ModelDownloadSourceProvider(
            catalog = listOf(pack),
            sourceManifest = ModelSourceManifest(entries = listOf(sourceEntry))
        )

        assertFalse(provider.isConfigured(ModelPackId.CORE))
        assertEquals("HASH_NOT_CONFIGURED", provider.configurationStatus(ModelPackId.CORE).message)
        assertTrue(provider.sourcesFor(ModelPackId.CORE).isEmpty())
    }
}
