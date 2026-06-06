package com.nova.luna.media

import org.junit.Assert.*
import org.junit.Test

class MediaIntentParserTest {
    private val parser = MediaIntentParser()

    @Test
    fun testParseOpenYouTube() {
        val request = parser.parse("Open YouTube")
        assertEquals(MediaProvider.YOUTUBE, request.provider)
        assertEquals(MediaCommandType.OPEN_APP, request.commandType)
    }

    @Test
    fun testParseSearchMrBeast() {
        val request = parser.parse("Search MrBeast latest video on YouTube")
        assertEquals(MediaProvider.YOUTUBE, request.provider)
        assertEquals(MediaCommandType.SEARCH_CONTENT, request.commandType)
        assertEquals("mrbeast latest video", request.searchQuery)
    }

    @Test
    fun testParsePlayArijitSingh() {
        val request = parser.parse("Play Arijit Singh song on YouTube")
        assertEquals(MediaProvider.YOUTUBE, request.provider)
        assertEquals(MediaCommandType.SEARCH_CONTENT, request.commandType)
        assertEquals("arijit singh song", request.searchQuery)
    }

    @Test
    fun testParseScrollDown() {
        val request = parser.parse("Scroll down")
        assertEquals(MediaCommandType.SCROLL_FEED, request.commandType)
        assertEquals(MediaScrollDirection.DOWN, request.scrollDirection)
    }

    @Test
    fun testParsePause() {
        val request = parser.parse("Pause")
        assertEquals(MediaCommandType.PAUSE, request.commandType)
        assertEquals(MediaPlaybackControl.PAUSE, request.playbackControl)
    }

    @Test
    fun testParseLike() {
        val request = parser.parse("Like this video")
        assertEquals(MediaSocialAction.LIKE, request.socialAction)
    }

    @Test
    fun testParseChangeQuality() {
        val request = parser.parse("Change quality to 1080p")
        assertEquals(MediaCommandType.CHANGE_QUALITY, request.commandType)
        assertEquals(MediaSettingAction.QUALITY, request.settingAction)
        assertEquals("1080p", request.settingValue)
    }

    @Test
    fun testParseOpenWatchlistDebug() {
        val request = parser.parse("Open my watchlist on Netflix")
        System.out.println("DEBUG: request.ottAction = ${request.ottAction}")
        System.out.println("DEBUG: lowerText = ${"Open my watchlist on Netflix".lowercase()}")
    }

    @Test
    fun testParseOpenWatchlist() {
        val request = parser.parse("Open my watchlist on Netflix")
        assertEquals(MediaProvider.NETFLIX, request.provider)
        assertEquals(MediaCommandType.OPEN_WATCHLIST, request.commandType)
        assertEquals("Ott action mismatch. Actual: ${request.ottAction}", MediaOttAction.OPEN_WATCHLIST, request.ottAction)
    }
}
