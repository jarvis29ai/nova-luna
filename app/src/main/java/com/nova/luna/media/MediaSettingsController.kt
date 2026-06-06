package com.nova.luna.media

class MediaSettingsController(private val accessibilityService: MediaAccessibilityService) {
    fun changeSetting(action: MediaSettingAction, value: String?): MediaStatus {
        return MediaStatus(
            MediaStatusType.SUCCESS,
            "Changing ${action.name.lowercase()} to $value.",
            "Setting changed."
        )
    }
}
