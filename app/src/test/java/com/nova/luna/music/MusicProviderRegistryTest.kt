package com.nova.luna.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicProviderRegistryTest {
    private lateinit var context: Context
    private lateinit var registry: MusicProviderRegistry

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = MusicProviderRegistry(context.packageManager)
    }

    @Test
    fun testGetProviders() {
        val providers = registry.getProviders()
        assertNotNull(providers)
        // Should contain at least Spotify and YT Music in the list of supported providers
        assert(providers.any { it.provider == MusicProvider.SPOTIFY })
        assert(providers.any { it.provider == MusicProvider.YOUTUBE_MUSIC })
    }
}
