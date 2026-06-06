package com.nova.luna.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSearchEngineTest {
    private val engine = MusicSearchEngine()

    @Test
    fun testBuildSearchQuery() {
        val request = MusicRequest(songName = "Tum Hi Ho", artistName = "Arijit Singh")
        val query = engine.buildSearchQuery(request)
        assertEquals("Tum Hi Ho by Arijit Singh", query)
    }

    @Test
    fun testBuildMoodQuery() {
        val request = MusicRequest(commandType = MusicCommandType.PLAY_MOOD_PLAYLIST, mood = MusicMood.HAPPY)
        val query = engine.buildSearchQuery(request)
        assertEquals("happy songs", query)
    }
}
