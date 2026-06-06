package com.nova.luna.media

class MediaPlaybackController(private val accessibilityService: MediaAccessibilityService) {
    fun handleAction(action: MediaPlaybackControl): MediaStatus {
        val success = accessibilityService.performPlaybackAction(action)
        return if (success) {
            MediaStatus(
                MediaStatusType.SUCCESS,
                "Playback action ${action.name} performed.",
                "Action performed."
            )
        } else {
            MediaStatus(
                MediaStatusType.MANUAL_ACTION_REQUIRED,
                "I couldn't control playback automatically. Please do it manually.",
                "Please do it manually."
            )
        }
    }
}
