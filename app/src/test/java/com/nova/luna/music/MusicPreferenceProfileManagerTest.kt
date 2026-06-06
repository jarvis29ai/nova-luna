package com.nova.luna.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPreferenceProfileManagerTest {
    private val manager = MusicPreferenceProfileManager()

    @Test
    fun testUpdateProfile() {
        manager.updatePreferredApp(MusicProvider.SPOTIFY)
        assertEquals(MusicProvider.SPOTIFY, manager.getProfile().preferredApp)
        
        manager.updateExplicitPreference(true)
        assertEquals(true, manager.getProfile().explicitContentPreference)
    }
}
