package com.nova.luna.media

class MediaResultRanker {
    fun rank(results: List<MediaSearchResult>, query: String?): List<MediaSearchResult> {
        if (query == null) return results
        
        return results.sortedByDescending { result ->
            var score = 0
            if (result.title.contains(query, ignoreCase = true)) score += 10
            if (result.isOfficial) score += 5
            score
        }
    }
}
