package com.nova.luna.music

/**
 * Matches search results against user requests to find exact or close matches.
 */
class MusicResultMatcher {

    fun findBestMatch(request: MusicRequest, results: List<MusicSearchResult>): MusicSearchResult? {
        if (results.isEmpty()) return null
        
        // If there's an exact match, return it
        val exactMatch = results.find { it.matchType == MusicMatchType.EXACT }
        if (exactMatch != null) return exactMatch

        // Otherwise, the orchestrator will likely show close matches to the user
        return null
    }

    fun getCloseMatches(results: List<MusicSearchResult>): List<MusicSearchResult> {
        return results.filter { it.matchType != MusicMatchType.EXACT }
    }
}
