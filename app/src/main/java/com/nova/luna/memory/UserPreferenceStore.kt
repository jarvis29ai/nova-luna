package com.nova.luna.memory

interface UserPreferenceStore {
    fun getPreferences(): LocalUserPreferences

    fun setPreferences(preferences: LocalUserPreferences)

    fun updatePreferences(transform: (LocalUserPreferences) -> LocalUserPreferences): LocalUserPreferences
}
