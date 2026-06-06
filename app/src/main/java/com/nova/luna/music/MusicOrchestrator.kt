package com.nova.luna.music

import android.content.Context
import java.util.UUID

/**
 * Orchestrator for the music domain. Acts as the state machine.
 */
class MusicOrchestrator(
    private val context: Context,
    private val parser: MusicIntentParser,
    private val registry: MusicProviderRegistry,
    private val launcher: MusicAppLauncher,
    private val searchEngine: MusicSearchEngine,
    private val matcher: MusicResultMatcher,
    private val safetyDetector: MusicSafetyDetector,
    private val playbackController: MusicPlaybackController,
    private val responses: MusicVoiceResponses,
    private val cardBuilder: MusicMiniCardBuilder
) {
    private var activeSession: MusicSession? = null

    fun handleRequest(text: String): MusicResponse {
        val session = activeSession ?: startNewSession()
        activeSession = session

        val request = parser.parse(text)
        val updatedSession = session.copy(
            currentRequest = mergeRequest(session.currentRequest, request)
        )
        activeSession = updatedSession

        return processSession(updatedSession)
    }

    fun isActive(): Boolean = activeSession != null && 
                           activeSession?.flowState != MusicFlowState.COMPLETED &&
                           activeSession?.flowState != MusicFlowState.FAILED &&
                           activeSession?.flowState != MusicFlowState.CANCELLED

    fun cancelSession() {
        activeSession = null
    }

    private fun startNewSession(): MusicSession {
        return MusicSession(sessionId = UUID.randomUUID().toString())
    }

    private fun mergeRequest(current: MusicRequest, new: MusicRequest): MusicRequest {
        // Simple merge: new fields override old ones if present
        val mergedCommandType = if (new.commandType != MusicCommandType.UNKNOWN && 
                                   new.commandType != MusicCommandType.SELECT_CLOSE_MATCH) {
            new.commandType
        } else {
            current.commandType
        }

        return current.copy(
            commandType = mergedCommandType,
            songName = new.songName ?: current.songName,
            artistName = new.artistName ?: current.artistName,
            albumName = new.albumName ?: current.albumName,
            mood = if (new.mood != MusicMood.UNKNOWN) new.mood else current.mood,
            language = if (new.language != MusicLanguage.UNKNOWN) new.language else current.language,
            genre = if (new.genre != MusicGenre.UNKNOWN) new.genre else current.genre,
            playlistName = new.playlistName ?: current.playlistName,
            preferredProvider = if (new.preferredProvider != MusicProvider.UNKNOWN) new.preferredProvider else current.preferredProvider,
            explicitAllowed = new.explicitAllowed ?: current.explicitAllowed,
            outputPreference = if (new.outputPreference != MusicDeviceOutputPreference.AUTO) new.outputPreference else current.outputPreference,
            closeMatchSelection = new.closeMatchSelection ?: current.closeMatchSelection,
            confirmation = new.confirmation ?: current.confirmation
        )
    }

    private fun processSession(session: MusicSession): MusicResponse {
        val request = session.currentRequest

        return when (request.commandType) {
            MusicCommandType.PAUSE -> applyControl("Paused", session) { playbackController.pause() }
            MusicCommandType.RESUME -> applyControl("Resumed", session) { playbackController.resume() }
            MusicCommandType.NEXT -> applyControl("Playing next song", session) { playbackController.next() }
            MusicCommandType.PREVIOUS -> applyControl("Playing previous song", session) { playbackController.previous() }
            MusicCommandType.STOP_MUSIC -> applyControl("Music stopped", session) { playbackController.stop() }
            MusicCommandType.INCREASE_VOLUME -> applyControl("Volume increased", session) { playbackController.adjustVolume(true) }
            MusicCommandType.DECREASE_VOLUME -> applyControl("Volume decreased", session) { playbackController.adjustVolume(false) }
            
            MusicCommandType.PLAY_SPECIFIC_SONG,
            MusicCommandType.PLAY_ARTIST,
            MusicCommandType.PLAY_ALBUM,
            MusicCommandType.PLAY_MOOD_PLAYLIST,
            MusicCommandType.PLAY_LANGUAGE_OR_GENRE,
            MusicCommandType.FIND_NEW_SONGS -> handlePlayFlow(session)

            MusicCommandType.CREATE_PLAYLIST -> handlePlaylistFlow(session)
            
            MusicCommandType.CANCEL -> {
                cancelSession()
                responses.controlApplied("Cancelled")
            }
            
            else -> responses.missingDetails()
        }
    }

    private fun handlePlayFlow(session: MusicSession): MusicResponse {
        val request = session.currentRequest
        
        // 1. Check details
        if (request.songName.isNullOrBlank() && request.artistName.isNullOrBlank() && request.albumName.isNullOrBlank() && 
            request.mood == MusicMood.UNKNOWN && request.language == MusicLanguage.UNKNOWN && 
            request.genre == MusicGenre.UNKNOWN && request.commandType != MusicCommandType.FIND_NEW_SONGS) {
            return responses.missingDetails()
        }

        // 2. Check provider
        val installedProviders = registry.getInstalledProviders()
        if (installedProviders.isEmpty()) {
            return responses.error("No music apps installed. Please install Spotify, YouTube Music, or others.")
        }

        val provider = if (request.preferredProvider != MusicProvider.UNKNOWN) {
            registry.getProviderStatus(request.preferredProvider)?.takeIf { it.isInstalled }?.provider
        } else {
            installedProviders.firstOrNull()?.provider
        }

        if (provider == null) {
            return responses.appChoice(installedProviders)
        }

        // 3. Search and Launch
        val query = searchEngine.buildSearchQuery(request)
        val launched = launcher.launchProvider(provider, query)
        
        if (!launched) {
            return responses.error("Failed to open $provider.")
        }

        // In this simplified model, we assume success after launch for now.
        // A real implementation would wait for Accessibility feedback.
        val simulatedResult = MusicSearchResult(
            songName = request.songName ?: "Song",
            artist = request.artistName,
            provider = provider
        )
        
        val updatedSession = session.copy(
            selectedResult = simulatedResult,
            activeApp = provider,
            playbackState = MusicPlaybackState.PLAYING,
            flowState = MusicFlowState.STARTING_PLAYBACK
        )
        activeSession = updatedSession

        return responses.playing(simulatedResult.songName, provider.name).copy(
            miniCard = cardBuilder.build(updatedSession)
        )
    }

    private fun handlePlaylistFlow(session: MusicSession): MusicResponse {
        val request = session.currentRequest
        if (request.playlistName == null) {
            return responses.manualActionRequired("provide a playlist name")
        }
        
        if (request.confirmation == null) {
            return MusicResponse(
                "Should I create the playlist '${request.playlistName}'?",
                "Should I create the playlist ${request.playlistName}?",
                MusicStatus.NEEDS_CONFIRMATION,
                MusicFlowState.ASKING_PLAYLIST_NAME
            )
        }
        
        if (request.confirmation == true) {
            return responses.playlistCreated(request.playlistName)
        } else {
            cancelSession()
            return responses.controlApplied("Cancelled")
        }
    }

    private fun applyControl(message: String, session: MusicSession, action: () -> Unit): MusicResponse {
        action()
        val updatedSession = session.copy(flowState = MusicFlowState.COMPLETED)
        activeSession = updatedSession
        return responses.controlApplied(message).copy(
            miniCard = cardBuilder.build(updatedSession)
        )
    }
}
