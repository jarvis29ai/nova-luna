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
        assertEquals(
            MODEL_SOURCE_NOT_CONFIGURED_MESSAGE,
            provider.configurationMessage(ModelPackId.LITE)
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
}
