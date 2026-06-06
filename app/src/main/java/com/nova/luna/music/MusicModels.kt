package com.nova.luna.music

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult

/**
 * Core models for the Music Assistant domain.
 */

data class MusicRequest(
    val commandType: MusicCommandType = MusicCommandType.UNKNOWN,
    val songName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val mood: MusicMood = MusicMood.UNKNOWN,
    val language: MusicLanguage = MusicLanguage.UNKNOWN,
    val genre: MusicGenre = MusicGenre.UNKNOWN,
    val playlistName: String? = null,
    val preferredProvider: MusicProvider = MusicProvider.UNKNOWN,
    val onlinePreference: Boolean = true,
    val explicitAllowed: Boolean? = null,
    val outputPreference: MusicDeviceOutputPreference = MusicDeviceOutputPreference.AUTO,
    val volumeTarget: Int? = null,
    val closeMatchSelection: Int? = null,
    val confirmation: Boolean? = null
)

data class MusicSession(
    val sessionId: String,
    val flowState: MusicFlowState = MusicFlowState.IDLE,
    val currentRequest: MusicRequest = MusicRequest(),
    val providerStatus: Map<MusicProvider, MusicProviderStatus> = emptyMap(),
    val searchResults: List<MusicSearchResult> = emptyList(),
    val selectedResult: MusicSearchResult? = null,
    val playbackState: MusicPlaybackState = MusicPlaybackState.STOPPED,
    val activeApp: MusicProvider = MusicProvider.UNKNOWN,
    val profile: MusicPreferenceProfile = MusicPreferenceProfile()
)

enum class MusicCommandType {
    PLAY_SPECIFIC_SONG,
    PLAY_ARTIST,
    PLAY_ALBUM,
    PLAY_MOOD_PLAYLIST,
    PLAY_LANGUAGE_OR_GENRE,
    CONTROL_MUSIC,
    PAUSE,
    RESUME,
    NEXT,
    PREVIOUS,
    INCREASE_VOLUME,
    DECREASE_VOLUME,
    SET_VOLUME,
    STOP_MUSIC,
    CREATE_PLAYLIST,
    FIND_NEW_SONGS,
    SEARCH_MUSIC,
    SELECT_CLOSE_MATCH,
    CHANGE_APP,
    CHANGE_OUTPUT_DEVICE,
    CANCEL,
    UNKNOWN
}

enum class MusicFlowState {
    IDLE,
    PARSING_REQUEST,
    DETECTING_COMMAND_TYPE,
    EXTRACTING_DETAILS,
    ASKING_MISSING_DETAILS,
    CREATING_PREFERENCE_PROFILE,
    CHECKING_AVAILABLE_MUSIC_APPS,
    ASKING_APP_CHOICE,
    OPENING_SELECTED_APP,
    SEARCHING_REQUESTED_MUSIC,
    CHECKING_EXACT_RESULT,
    SHOWING_CLOSE_MATCHES,
    WAITING_FOR_MATCH_SELECTION,
    PREPARING_PLAYBACK,
    CHECKING_MUSIC_SAFETY,
    WARNING_EXPLICIT_CONTENT,
    ASKING_EXPLICIT_PERMISSION,
    SEARCHING_CLEAN_VERSION,
    STARTING_PLAYBACK,
    SHOWING_MINI_MUSIC_CARD,
    APPLYING_MUSIC_CONTROL,
    UPDATING_MINI_MUSIC_CARD,
    ASKING_PLAYLIST_NAME,
    CREATING_PLAYLIST,
    SEARCHING_TRENDING_MUSIC,
    WAITING_FOR_NEXT_COMMAND,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

enum class MusicProvider {
    SPOTIFY,
    YOUTUBE_MUSIC,
    APPLE_MUSIC,
    JIOSAAVN,
    WYNK_MUSIC,
    GAANA,
    LOCAL_DEVICE_MUSIC,
    UNKNOWN
}

enum class MusicProviderType {
    STREAMING,
    LOCAL
}

data class MusicProviderStatus(
    val provider: MusicProvider,
    val displayName: String,
    val packageNames: List<String>,
    val type: MusicProviderType,
    val isInstalled: Boolean = false,
    val searchSupport: Boolean = true,
    val playbackSupport: Boolean = true,
    val playlistSupport: Boolean = false,
    val deepLinkSupport: Boolean = true,
    val unavailableReason: String? = null
)

data class MusicPreferenceProfile(
    val preferredApp: MusicProvider = MusicProvider.UNKNOWN,
    val onlinePreference: Boolean = true,
    val favoriteArtists: Set<String> = emptySet(),
    val preferredLanguage: MusicLanguage = MusicLanguage.UNKNOWN,
    val preferredGenre: MusicGenre = MusicGenre.UNKNOWN,
    val explicitContentPreference: Boolean? = null,
    val cleanVersionPreference: Boolean = true,
    val outputDevicePreference: MusicDeviceOutputPreference = MusicDeviceOutputPreference.AUTO,
    val lastPlayedSong: String? = null,
    val lastPlaylist: String? = null,
    val activeMusicApp: MusicProvider = MusicProvider.UNKNOWN
)

data class MusicSearchResult(
    val songName: String,
    val artist: String? = null,
    val album: String? = null,
    val playlist: String? = null,
    val provider: MusicProvider,
    val matchType: MusicMatchType = MusicMatchType.EXACT,
    val isExplicit: Boolean = false,
    val hasCleanVersion: Boolean = false,
    val visiblePosition: Int = 0,
    val availability: Boolean = true,
    val warningReason: String? = null
)

enum class MusicMatchType {
    EXACT,
    SIMILAR_NAME,
    SAME_ARTIST,
    REMIX,
    COVER,
    LIVE_VERSION,
    CLEAN_VERSION,
    EXPLICIT_VERSION
}

enum class MusicPlaybackState {
    PLAYING,
    PAUSED,
    STOPPED,
    BUFFERING,
    UNKNOWN
}

enum class MusicMood {
    HAPPY,
    SAD,
    ROMANTIC,
    WORKOUT,
    FOCUS,
    PARTY,
    RELAX,
    DEVOTIONAL,
    UNKNOWN
}

enum class MusicLanguage {
    HINDI,
    ENGLISH,
    PUNJABI,
    TAMIL,
    TELUGU,
    MARATHI,
    BENGALI,
    HARYANVI,
    UNKNOWN
}

enum class MusicGenre {
    LOFI,
    EDM,
    POP,
    ROCK,
    HIP_HOP,
    BOLLYWOOD,
    CLASSICAL,
    DEVOTIONAL,
    UNKNOWN
}

enum class MusicDeviceOutputPreference {
    PHONE_SPEAKER,
    BLUETOOTH,
    EARPHONES,
    AUTO,
    UNKNOWN
}

enum class MusicStatus {
    SUCCESS,
    PARTIAL,
    BLOCKED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT,
    NEEDS_CONFIRMATION,
    MANUAL_ACTION_REQUIRED
}

data class MusicMiniCard(
    val songName: String?,
    val artist: String?,
    val albumOrPlaylist: String?,
    val appUsed: String?,
    val playbackStatus: MusicPlaybackState,
    val hasLyrics: Boolean = false,
    val explicitWarning: Boolean = false
)

data class MusicResponse(
    val popupText: String,
    val voiceText: String,
    val status: MusicStatus,
    val flowState: MusicFlowState,
    val miniCard: MusicMiniCard? = null
)

fun MusicResponse.toCommandResult(commandIntent: CommandIntent): CommandResult {
    return CommandResult(
        success = status == MusicStatus.SUCCESS || 
                  status == MusicStatus.NEEDS_USER_INPUT || 
                  status == MusicStatus.NEEDS_CONFIRMATION ||
                  status == MusicStatus.MANUAL_ACTION_REQUIRED,
        message = popupText,
        intentType = commandIntent.intentType,
        actionType = commandIntent.actionType,
        entities = commandIntent.entities + mapOf("voiceText" to voiceText)
    )
}
