package com.nova.luna.music

import org.junit.Assert.assertFalse
import org.junit.Test

class MusicPlaylistControllerTest {
    private val controller = MusicPlaylistController()

    @Test
    fun testCreatePlaylist() {
        // Should be false by default in this scaffolded version
        assertFalse(controller.createPlaylist("test"))
    }
}
