package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        val source = provider.sourceFor(ModelPackId.LITE)
        assertNotNull(source)
        assertTrue(source!!.downloadUrl.isNullOrBlank())
    }
}
