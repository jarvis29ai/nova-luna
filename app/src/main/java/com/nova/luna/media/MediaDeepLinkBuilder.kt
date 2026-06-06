package com.nova.luna.media

import android.net.Uri

class MediaDeepLinkBuilder {
    fun build(provider: MediaProvider, searchQuery: String?): Uri? {
        if (searchQuery == null) return null
        
        return when (provider) {
            MediaProvider.YOUTUBE -> Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}")
            MediaProvider.YOUTUBE_SHORTS -> Uri.parse("https://www.youtube.com/hashtag/shorts") // Simplified
            MediaProvider.INSTAGRAM -> Uri.parse("https://www.instagram.com/explore/tags/${Uri.encode(searchQuery)}/")
            MediaProvider.INSTAGRAM_REELS -> Uri.parse("https://www.instagram.com/reels/")
            MediaProvider.NETFLIX -> Uri.parse("https://www.netflix.com/search?q=${Uri.encode(searchQuery)}")
            MediaProvider.JIOHOTSTAR -> Uri.parse("https://www.hotstar.com/search?q=${Uri.encode(searchQuery)}")
            MediaProvider.PRIME_VIDEO -> Uri.parse("https://www.primevideo.com/search?phrase=${Uri.encode(searchQuery)}")
            else -> null
        }
    }
}
