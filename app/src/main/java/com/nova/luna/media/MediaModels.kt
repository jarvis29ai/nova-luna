package com.nova.luna.media

data class MediaRequest(
    val command: String,
    val appType: MediaAppType = MediaAppType.UNKNOWN,
    val provider: MediaProvider = MediaProvider.UNKNOWN_APP,
    val commandType: MediaCommandType = MediaCommandType.UNKNOWN,
    val searchQuery: String? = null,
    val contentTitle: String? = null,
    val creatorName: String? = null,
    val selectionIndex: Int? = null,
    val selectionTitle: String? = null,
    val scrollDirection: MediaScrollDirection = MediaScrollDirection.NONE,
    val scrollSpeed: String? = null, // "slow", "fast", "normal"
    val socialAction: MediaSocialAction = MediaSocialAction.NONE,
    val commentText: String? = null,
    val ottAction: MediaOttAction = MediaOttAction.NONE,
    val settingAction: MediaSettingAction = MediaSettingAction.NONE,
    val settingValue: String? = null,
    val playbackControl: MediaPlaybackControl = MediaPlaybackControl.NONE,
    val isConfirmation: Boolean? = null
)

enum class MediaAppType {
    VIDEO,
    SOCIAL,
    OTT,
    UNKNOWN
}

enum class MediaProvider(val id: String, val displayName: String, val packageNames: List<String>) {
    YOUTUBE("youtube", "YouTube", listOf("com.google.android.youtube")),
    YOUTUBE_SHORTS("youtube_shorts", "YouTube Shorts", listOf("com.google.android.youtube")),
    INSTAGRAM("instagram", "Instagram", listOf("com.instagram.android")),
    INSTAGRAM_REELS("instagram_reels", "Instagram Reels", listOf("com.instagram.android")),
    NETFLIX("netflix", "Netflix", listOf("com.netflix.mediaclient")),
    JIOHOTSTAR("jiohotstar", "JioHotstar", listOf("com.jio.media.ondemand", "in.startv.hotstar")),
    PRIME_VIDEO("prime_video", "Prime Video", listOf("com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom")),
    UNKNOWN_APP("unknown", "Unknown App", emptyList()),
    OTHER("other", "Other App", emptyList())
}

enum class MediaCommandType {
    OPEN_APP,
    SEARCH_CONTENT,
    SCROLL_FEED,
    SELECT_VISIBLE_ITEM,
    PLAY,
    PAUSE,
    RESUME,
    FORWARD,
    BACKWARD,
    FULL_SCREEN,
    EXIT_FULL_SCREEN,
    NEXT_CONTENT,
    PREVIOUS_CONTENT,
    LIKE,
    SAVE,
    SUBSCRIBE,
    FOLLOW,
    COMMENT,
    OPEN_PROFILE,
    OPEN_CHANNEL,
    OPEN_CREATOR,
    ADD_TO_WATCHLIST,
    REMOVE_FROM_WATCHLIST,
    DOWNLOAD_CONTENT,
    OPEN_WATCHLIST,
    CHANGE_QUALITY,
    TOGGLE_SUBTITLES,
    CHANGE_AUDIO_LANGUAGE,
    CHANGE_SPEED,
    STOP_EXIT,
    CANCEL,
    UNKNOWN
}

enum class MediaScrollDirection {
    UP, DOWN, LEFT, RIGHT, NONE
}

enum class MediaSocialAction {
    LIKE, SAVE, SUBSCRIBE, FOLLOW, COMMENT, NONE
}

enum class MediaOttAction {
    ADD_TO_WATCHLIST, REMOVE_FROM_WATCHLIST, DOWNLOAD, OPEN_WATCHLIST, NONE
}

enum class MediaSettingAction {
    QUALITY, SUBTITLES, AUDIO_LANGUAGE, SPEED, NONE
}

enum class MediaPlaybackControl {
    PLAY, PAUSE, RESUME, FORWARD, BACKWARD, FULL_SCREEN, EXIT_FULL_SCREEN, NEXT, PREVIOUS, STOP, NONE
}

data class MediaSession(
    val id: String,
    var state: MediaFlowState = MediaFlowState.IDLE,
    var activeProvider: MediaProvider = MediaProvider.UNKNOWN_APP,
    var lastRequest: MediaRequest? = null,
    var searchResults: List<MediaSearchResult> = emptyList(),
    var visibleItems: List<MediaVisibleItem> = emptyList(),
    var playbackState: MediaPlaybackState = MediaPlaybackState.UNKNOWN
)

enum class MediaFlowState {
    IDLE,
    PARSING_REQUEST,
    DETECTING_APP_AND_INTENT,
    ASKING_APP_SELECTION,
    CHECKING_APP_AVAILABLE,
    APP_NOT_AVAILABLE,
    OPENING_APP,
    WAITING_FOR_MEDIA_COMMAND,
    SEARCHING_CONTENT,
    ASKING_SEARCH_DETAILS,
    READING_SEARCH_RESULTS,
    RANKING_RESULTS,
    SHOWING_SEARCH_SUMMARY,
    WAITING_FOR_RESULT_SELECTION,
    DETECTING_VISIBLE_ITEMS,
    SELECTING_VISIBLE_ITEM,
    STARTING_PLAYBACK,
    SHOWING_MEDIA_CARD,
    SCROLLING_FEED,
    READING_VISIBLE_CONTENT,
    SUMMARIZING_VISIBLE_OPTIONS,
    CONTROLLING_PLAYBACK,
    MOVING_NEXT_CONTENT,
    MOVING_PREVIOUS_CONTENT,
    CONFIRMING_SOCIAL_ACTION,
    PERFORMING_SOCIAL_ACTION,
    ASKING_COMMENT_TEXT,
    SHOWING_COMMENT_PREVIEW,
    POSTING_COMMENT,
    OPENING_PROFILE_OR_CHANNEL,
    READING_PROFILE_INFO,
    HANDLING_WATCHLIST,
    HANDLING_DOWNLOAD,
    CHANGING_MEDIA_SETTING,
    CHECKING_SAFETY,
    BLOCKED_BY_SAFETY,
    STOPPING_MEDIA_CONTROL,
    EXITING_APP,
    WAITING_FOR_NEXT_COMMAND,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

data class MediaVisibleItem(
    val title: String,
    val creator: String? = null,
    val index: Int,
    val nodeId: String? = null
)

data class MediaSearchResult(
    val title: String,
    val creator: String? = null,
    val app: MediaProvider,
    val index: Int,
    val isOfficial: Boolean = false,
    val metadata: String? = null
)

enum class MediaPlaybackState {
    PLAYING, PAUSED, BUFFERING, STOPPED, UNKNOWN
}

data class MediaStatus(
    val status: MediaStatusType,
    val popupText: String,
    val voiceText: String,
    val metadata: Map<String, Any> = emptyMap()
)

enum class MediaStatusType {
    SUCCESS,
    PARTIAL,
    BLOCKED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT,
    NEEDS_CONFIRMATION,
    MANUAL_ACTION_REQUIRED
}
