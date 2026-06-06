package com.nova.luna.media

class MediaSearchEngine(private val accessibilityService: MediaAccessibilityService) {
    fun search(query: String): MediaStatus {
        val success = accessibilityService.search(query)
        return if (success) {
            MediaStatus(
                MediaStatusType.SUCCESS,
                "Searching for \"$query\".",
                "Searching."
            )
        } else {
            MediaStatus(
                MediaStatusType.MANUAL_ACTION_REQUIRED,
                "I couldn't find the search box. Please type your search manually.",
                "Please search manually."
            )
        }
    }

    fun readResults(): List<MediaSearchResult> {
        val visibleItems = accessibilityService.readVisibleResults()
        return visibleItems.map { item ->
            MediaSearchResult(
                title = item.title,
                creator = item.creator,
                app = MediaProvider.UNKNOWN_APP, // Should be passed in
                index = item.index
            )
        }
    }
}
