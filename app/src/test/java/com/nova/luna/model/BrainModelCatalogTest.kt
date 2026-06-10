package com.nova.luna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainModelCatalogTest {
    @Test
    fun catalogContainsTheThreeNovaLunaBrainRoles() {
        val roles = BrainModelCatalog.entries.map { it.role }

        assertEquals(
            listOf(
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.MULTILINGUAL_BACKUP,
                BrainModelRole.LITE_FALLBACK
            ),
            roles
        )
    }

    @Test
    fun catalogKeepsTheExpectedIdsAndFilenames() {
        val core = BrainModelCatalog.entryForRole(BrainModelRole.CORE_BRAIN)
        val multilingual = BrainModelCatalog.entryForRole(BrainModelRole.MULTILINGUAL_BACKUP)
        val lite = BrainModelCatalog.entryForRole(BrainModelRole.LITE_FALLBACK)

        assertTrue(core != null)
        assertTrue(multilingual != null)
        assertTrue(lite != null)

        assertEquals("gemma-3n-E2B-it-litert", core!!.modelId)
        assertEquals("gemma-3n-E2B-it-int4.litertlm", core.fileName)

        assertEquals("qwen2.5-1.5b-instruct-q4-k-m", multilingual!!.modelId)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", multilingual.fileName)

        assertEquals("qwen2.5-0.5b-instruct-q4-k-m", lite!!.modelId)
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m.gguf", lite.fileName)
    }
}
