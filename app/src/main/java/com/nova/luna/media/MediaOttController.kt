package com.nova.luna.media

class MediaOttController(private val accessibilityService: MediaAccessibilityService) {
    fun handleAction(action: MediaOttAction, confirmed: Boolean = false): MediaStatus {
        return when (action) {
            MediaOttAction.ADD_TO_WATCHLIST, MediaOttAction.REMOVE_FROM_WATCHLIST -> {
                if (!confirmed) {
                    MediaStatus(
                        MediaStatusType.NEEDS_CONFIRMATION,
                        "Should I ${action.name.replace("_", " ").lowercase()}?",
                        "Confirm?"
                    )
                } else {
                    MediaStatus(MediaStatusType.SUCCESS, "Updated watchlist.", "Done.")
                }
            }
            MediaOttAction.DOWNLOAD -> {
                if (!confirmed) {
                    MediaStatus(
                        MediaStatusType.NEEDS_CONFIRMATION,
                        "Do you want to download this?",
                        "Download?"
                    )
                } else {
                    // Check Wi-Fi/Storage logic would go here
                    MediaStatus(MediaStatusType.SUCCESS, "Download started.", "Downloading.")
                }
            }
            MediaOttAction.OPEN_WATCHLIST -> {
                MediaStatus(MediaStatusType.SUCCESS, "Opening watchlist.", "Opening list.")
            }
            MediaOttAction.NONE -> MediaStatus(MediaStatusType.FAILED, "No OTT action.", "")
        }
    }
}
