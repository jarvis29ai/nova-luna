package com.nova.luna.media

class MediaSocialActionController(private val accessibilityService: MediaAccessibilityService) {
    fun handleAction(action: MediaSocialAction, confirmed: Boolean = false): MediaStatus {
        return when (action) {
            MediaSocialAction.LIKE, MediaSocialAction.SAVE -> {
                // Typically safe to auto-perform if user asked
                MediaStatus(MediaStatusType.SUCCESS, "${action.name} performed.", "Done.")
            }
            MediaSocialAction.SUBSCRIBE, MediaSocialAction.FOLLOW -> {
                if (!confirmed) {
                    MediaStatus(
                        MediaStatusType.NEEDS_CONFIRMATION,
                        "Do you want to ${action.name.lowercase()} this creator?",
                        "Should I ${action.name.lowercase()}?"
                    )
                } else {
                    MediaStatus(MediaStatusType.SUCCESS, "${action.name} confirmed.", "Confirmed.")
                }
            }
            MediaSocialAction.COMMENT -> {
                if (!confirmed) {
                    MediaStatus(
                        MediaStatusType.NEEDS_USER_INPUT,
                        "What should I comment?",
                        "What's your comment?"
                    )
                } else {
                    MediaStatus(MediaStatusType.SUCCESS, "Comment posted.", "Posted.")
                }
            }
            MediaSocialAction.NONE -> MediaStatus(MediaStatusType.FAILED, "No social action specified.", "")
        }
    }
}
