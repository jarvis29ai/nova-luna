package com.nova.luna.media

class MediaScrollController(private val accessibilityService: MediaAccessibilityService) {
    fun scroll(direction: MediaScrollDirection): MediaStatus {
        val success = accessibilityService.scroll(direction)
        return if (success) {
            MediaStatus(
                MediaStatusType.SUCCESS,
                "Scrolling ${direction.name.lowercase()}.",
                "Scrolling."
            )
        } else {
            MediaStatus(
                MediaStatusType.FAILED,
                "Failed to scroll.",
                "Couldn't scroll."
            )
        }
    }
}
