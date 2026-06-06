package com.nova.luna.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicResultMatcherTest {
    private val matcher = MusicResultMatcher()

    @Test
    fun testFindExactMatch() {
        val request = MusicRequest(songName = "Tum Hi Ho")
        val results = listOf(
            MusicSearchResult("Tum Hi Ho", provider = MusicProvider.SPOTIFY, matchType = MusicMatchType.EXACT),
            MusicSearchResult("Tum Hi Ho Remix", provider = MusicProvider.SPOTIFY, matchType = MusicMatchType.REMIX)
        )
        val match = matcher.findBestMatch(request, results)
        assertEquals("Tum Hi Ho", match?.songName)
    }

    @Test
    fun testNoExactMatch() {
        val request = MusicRequest(songName = "Tum Hi Ho")
        val results = listOf(
            MusicSearchResult("Tum Hi Ho Remix", provider = MusicProvider.SPOTIFY, matchType = MusicMatchType.REMIX)
        )
        val match = matcher.findBestMatch(request, results)
        assertNull(match)
    }
}
