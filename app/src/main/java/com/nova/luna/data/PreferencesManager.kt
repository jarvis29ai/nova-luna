package com.nova.luna.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nova.luna.model.VoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nova_luna_preferences")

class PreferencesManager(private val context: Context) {
    private object Keys {
        val voiceProfile = stringPreferencesKey("voice_profile")
        val wakePhrase = stringPreferencesKey("wake_phrase")
        val autoStartOnBoot = booleanPreferencesKey("auto_start_on_boot")
        val assistantEnabled = booleanPreferencesKey("assistant_enabled")
    }

    val voiceProfileFlow: Flow<VoiceProfile> = context.dataStore.data.map { preferences ->
        VoiceProfile.fromStoredValue(preferences[Keys.voiceProfile])
    }

    val wakePhraseFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.wakePhrase] ?: DEFAULT_WAKE_PHRASE
    }

    val autoStartOnBootFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.autoStartOnBoot] ?: false
    }

    val assistantEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.assistantEnabled] ?: true
    }

    suspend fun getVoiceProfile(): VoiceProfile {
        return context.dataStore.data.first().let { preferences ->
            VoiceProfile.fromStoredValue(preferences[Keys.voiceProfile])
        }
    }

    suspend fun setVoiceProfile(profile: VoiceProfile) {
        context.dataStore.edit { preferences ->
            preferences[Keys.voiceProfile] = profile.name
        }
    }

    suspend fun getWakePhrase(): String {
        return context.dataStore.data.first().let { preferences ->
            preferences[Keys.wakePhrase] ?: DEFAULT_WAKE_PHRASE
        }
    }

    suspend fun setWakePhrase(wakePhrase: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.wakePhrase] = wakePhrase.ifBlank { DEFAULT_WAKE_PHRASE }
        }
    }

    suspend fun isAutoStartOnBootEnabled(): Boolean {
        return context.dataStore.data.first().let { preferences ->
            preferences[Keys.autoStartOnBoot] ?: false
        }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.autoStartOnBoot] = enabled
        }
    }

    suspend fun isAssistantEnabled(): Boolean {
        return context.dataStore.data.first().let { preferences ->
            preferences[Keys.assistantEnabled] ?: true
        }
    }

    suspend fun setAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.assistantEnabled] = enabled
        }
    }

    companion object {
        const val DEFAULT_WAKE_PHRASE = "Nova"
    }
}
