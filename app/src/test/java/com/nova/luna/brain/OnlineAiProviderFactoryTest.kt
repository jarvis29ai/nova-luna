package com.nova.luna.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProviderFactoryTest {
    @Test
    fun `fake provider type is treated as unavailable by the production factory`() {
        val provider = OnlineAiProviderFactory.create(
            onlineAiConfig(
                enabled = true,
                providerType = OnlineAiProviderType.FAKE
            )
        )

        assertTrue(provider is UnavailableOnlineAiProvider)
        assertFalse(provider.available)
        assertEquals(OnlineAiProviderType.FAKE, provider.providerType)
    }

    @Test
    fun `real providers remain unavailable until they are wired`() {
        listOf(
            OnlineAiProviderType.CHATGPT,
            OnlineAiProviderType.GEMINI,
            OnlineAiProviderType.CLAUDE
        ).forEach { providerType ->
            val provider = OnlineAiProviderFactory.create(
                onlineAiConfig(
                    enabled = true,
                    providerType = providerType
                )
            )

            assertFalse(provider.available)
            assertEquals(providerType, provider.providerType)
        }
    }

    @Test
    fun `disabled helper always returns an unavailable provider`() {
        val provider = OnlineAiProviderFactory.create(
            onlineAiConfig(
                enabled = false,
                providerType = OnlineAiProviderType.FAKE
            )
        )

        assertFalse(provider.available)
        assertEquals(OnlineAiProviderType.FAKE, provider.providerType)
    }
}
