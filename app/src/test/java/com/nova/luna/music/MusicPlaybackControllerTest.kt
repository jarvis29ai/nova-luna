package com.nova.luna.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicPlaybackControllerTest {
    private lateinit var context: Context
    private lateinit var controller: MusicPlaybackController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = MusicPlaybackController(context)
    }

    @Test
    fun testVolumeControls() {
        // Just verify it doesn't crash as it uses system services
        controller.adjustVolume(true)
        controller.setVolume(50)
    }
}
