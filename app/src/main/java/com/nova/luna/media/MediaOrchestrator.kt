package com.nova.luna.media

import android.content.Context
import java.util.UUID

class MediaOrchestrator(private val context: Context) {
    private val parser = MediaIntentParser()
    private val registry = MediaProviderRegistry(context.packageManager)
    private val launcher = MediaAppLauncher(context)
    private val accessibilityService = MediaAccessibilityService()
    private val playbackController = MediaPlaybackController(accessibilityService)
    private val scrollController = MediaScrollController(accessibilityService)
    private val socialActionController = MediaSocialActionController(accessibilityService)
    private val profileController = MediaProfileController(accessibilityService)
    private val ottController = MediaOttController(accessibilityService)
    private val settingsController = MediaSettingsController(accessibilityService)
    private val searchEngine = MediaSearchEngine(accessibilityService)
    private val ranker = MediaResultRanker()
    private val safetyDetector = MediaSafetyDetector()

    private var activeSession: MediaSession? = null

    fun handleRequest(text: String): MediaStatus {
        val request = parser.parse(text)
        
        if (activeSession == null || request.commandType == MediaCommandType.OPEN_APP) {
            activeSession = MediaSession(id = UUID.randomUUID().toString())
        }
        
        val session = activeSession!!
        session.lastRequest = request

        // Safety check first
        if (!safetyDetector.isSafeToProceed()) {
            return MediaStatus(
                MediaStatusType.BLOCKED,
                MediaVoiceResponses.safetyBlocked(),
                MediaVoiceResponses.safetyBlocked()
            )
        }

        return when (request.commandType) {
            MediaCommandType.OPEN_APP -> handleOpenApp(request, session)
            MediaCommandType.SEARCH_CONTENT -> handleSearch(request, session)
            MediaCommandType.SCROLL_FEED -> handleScroll(request)
            MediaCommandType.SELECT_VISIBLE_ITEM -> handleSelection(request, session)
            MediaCommandType.PLAY, MediaCommandType.PAUSE, MediaCommandType.RESUME,
            MediaCommandType.FORWARD, MediaCommandType.BACKWARD, MediaCommandType.FULL_SCREEN,
            MediaCommandType.EXIT_FULL_SCREEN, MediaCommandType.NEXT_CONTENT, MediaCommandType.PREVIOUS_CONTENT -> handlePlayback(request)
            MediaCommandType.LIKE, MediaCommandType.SAVE, MediaCommandType.SUBSCRIBE,
            MediaCommandType.FOLLOW, MediaCommandType.COMMENT -> handleSocialAction(request)
            MediaCommandType.OPEN_PROFILE, MediaCommandType.OPEN_CHANNEL, MediaCommandType.OPEN_CREATOR -> handleProfile(request)
            MediaCommandType.ADD_TO_WATCHLIST, MediaCommandType.REMOVE_FROM_WATCHLIST,
            MediaCommandType.DOWNLOAD_CONTENT, MediaCommandType.OPEN_WATCHLIST -> handleOttAction(request)
            MediaCommandType.CHANGE_QUALITY, MediaCommandType.TOGGLE_SUBTITLES,
            MediaCommandType.CHANGE_AUDIO_LANGUAGE, MediaCommandType.CHANGE_SPEED -> handleSettings(request)
            MediaCommandType.STOP_EXIT -> handleStop(session)
            MediaCommandType.CANCEL -> handleCancel(session)
            MediaCommandType.UNKNOWN -> {
                if (request.playbackControl != MediaPlaybackControl.NONE) {
                    handlePlayback(request)
                } else if (request.searchQuery != null) {
                    handleSearch(request, session)
                } else {
                    MediaStatus(
                        MediaStatusType.NEEDS_USER_INPUT,
                        "What would you like to do with media?",
                        "How can I help with media?"
                    )
                }
            }
        }
    }

    private fun handleOpenApp(request: MediaRequest, session: MediaSession): MediaStatus {
        val provider = request.provider
        if (provider == MediaProvider.UNKNOWN_APP) {
            return MediaStatus(
                MediaStatusType.NEEDS_USER_INPUT,
                "Which app should I open?",
                "Which app?"
            )
        }

        val status = registry.getProviderStatus(provider)
        if (!status.isInstalled) {
            return MediaStatus(
                MediaStatusType.FAILED,
                MediaVoiceResponses.appNotInstalled(provider),
                MediaVoiceResponses.appNotInstalled(provider)
            )
        }

        session.activeProvider = provider
        val launchResult = launcher.launch(provider, request.searchQuery)
        return if (launchResult == LaunchResult.SUCCESS) {
            MediaStatus(
                MediaStatusType.SUCCESS,
                MediaVoiceResponses.openingApp(provider),
                MediaVoiceResponses.openingApp(provider)
            )
        } else {
            MediaStatus(
                MediaStatusType.FAILED,
                "Failed to open ${provider.displayName}.",
                "Couldn't open app."
            )
        }
    }

    private fun handleSearch(request: MediaRequest, session: MediaSession): MediaStatus {
        val query = request.searchQuery
        if (query == null) {
            return MediaStatus(
                MediaStatusType.NEEDS_USER_INPUT,
                MediaVoiceResponses.whatToSearch(),
                MediaVoiceResponses.whatToSearch()
            )
        }

        // If app not open, open it first
        if (session.activeProvider == MediaProvider.UNKNOWN_APP) {
            return handleOpenApp(request, session)
        }

        return searchEngine.search(query)
    }

    private fun handleScroll(request: MediaRequest): MediaStatus {
        return scrollController.scroll(request.scrollDirection)
    }

    private fun handleSelection(request: MediaRequest, session: MediaSession): MediaStatus {
        val index = request.selectionIndex ?: 0
        val success = accessibilityService.selectItem(index)
        return if (success) {
            MediaStatus(MediaStatusType.SUCCESS, "Item selected.", "Done.")
        } else {
            MediaStatus(MediaStatusType.MANUAL_ACTION_REQUIRED, MediaVoiceResponses.manualHandoff(), MediaVoiceResponses.manualHandoff())
        }
    }

    private fun handlePlayback(request: MediaRequest): MediaStatus {
        return playbackController.handleAction(request.playbackControl)
    }

    private fun handleSocialAction(request: MediaRequest): MediaStatus {
        return socialActionController.handleAction(request.socialAction, request.isConfirmation ?: false)
    }

    private fun handleProfile(request: MediaRequest): MediaStatus {
        return profileController.openProfile()
    }

    private fun handleOttAction(request: MediaRequest): MediaStatus {
        return ottController.handleAction(request.ottAction, request.isConfirmation ?: false)
    }

    private fun handleSettings(request: MediaRequest): MediaStatus {
        return settingsController.changeSetting(request.settingAction, request.settingValue)
    }

    private fun handleStop(session: MediaSession): MediaStatus {
        activeSession = null
        return MediaStatus(
            MediaStatusType.SUCCESS,
            "Stopping media control.",
            "Stopped."
        )
    }

    private fun handleCancel(session: MediaSession): MediaStatus {
        return handleStop(session)
    }

    fun isActive(): Boolean = activeSession != null
}
