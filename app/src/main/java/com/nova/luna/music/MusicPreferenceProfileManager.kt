package com.nova.luna.music

/**
 * Manages user music preferences and profiles.
 */
class MusicPreferenceProfileManager {
    private var profile = MusicPreferenceProfile()

    fun getProfile(): MusicPreferenceProfile = profile

    fun updatePreferredApp(provider: MusicProvider) {
        profile = profile.copy(preferredApp = provider)
    }

    fun updateExplicitPreference(allowed: Boolean) {
        profile = profile.copy(explicitContentPreference = allowed)
    }

    fun updateLastPlayed(song: String, app: MusicProvider) {
        profile = profile.copy(lastPlayedSong = song, activeMusicApp = app)
    }
}
