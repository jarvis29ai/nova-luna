package com.nova.luna.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicIntentParserTest {
    private val parser = MusicIntentParser()

    @Test
    fun testParsePlaySong() {
        val request = parser.parse("play Tum Hi Ho")
        assertEquals(MusicCommandType.PLAY_SPECIFIC_SONG, request.commandType)
        assertEquals("tum hi ho", request.songName)
    }

    @Test
    fun testParsePlayArtist() {
        val request = parser.parse("play Arijit Singh songs")
        assertEquals(MusicCommandType.PLAY_ARTIST, request.commandType)
        assertEquals("arijit singh", request.artistName)
    }

    @Test
    fun testParseMood() {
        val request = parser.parse("play happy songs")
        assertEquals(MusicCommandType.PLAY_MOOD_PLAYLIST, request.commandType)
        assertEquals(MusicMood.HAPPY, request.mood)
    }

    @Test
    fun testParseControl() {
        val request = parser.parse("pause music")
        assertEquals(MusicCommandType.PAUSE, request.commandType)
        
        val nextRequest = parser.parse("next song")
        assertEquals(MusicCommandType.NEXT, nextRequest.commandType)
    }

    @Test
    fun testParsePlaylist() {
        val request = parser.parse("create playlist named gym")
        assertEquals(MusicCommandType.CREATE_PLAYLIST, request.commandType)
        assertEquals("gym", request.playlistName)
    }
}
