package com.nova.luna.media

class MediaProfileController(private val accessibilityService: MediaAccessibilityService) {
    fun openProfile(): MediaStatus {
        return MediaStatus(
            MediaStatusType.SUCCESS,
            "Opening creator profile.",
            "Opening profile."
        )
    }
}
