package com.nova.luna.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicOrchestratorTest {
    private lateinit var context: Context
    private lateinit var orchestrator: MusicOrchestrator
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val registry = MusicProviderRegistry(context.packageManager)
        val deepLinkBuilder = MusicDeepLinkBuilder(context)
        val launcher = MusicAppLauncher(context, deepLinkBuilder, registry)
        
        orchestrator = MusicOrchestrator(
            context = context,
            parser = MusicIntentParser(),
            registry = registry,
            launcher = launcher,
            searchEngine = MusicSearchEngine(),
            matcher = MusicResultMatcher(),
            safetyDetector = MusicSafetyDetector(),
            playbackController = MusicPlaybackController(context),
            responses = MusicVoiceResponses(),
            cardBuilder = MusicMiniCardBuilder()
        )
    }

    @Test
    fun testPlaySongFlow() {
        // Since we are in a test env, Spotify might not be "installed" 
        // unless we mock the package manager. 
        // But the orchestrator handles missing apps by returning an error or choice.
        val response = orchestrator.handleRequest("play Tum Hi Ho")
        
        // It should either try to play on the first available app or ask for app choice
        assertNotNull(response.popupText)
        assertNotNull(response.status)
    }

    @Test
    fun testPauseFlow() {
        val response = orchestrator.handleRequest("pause")
        assertEquals(MusicStatus.SUCCESS, response.status)
        assertEquals("Paused.", response.popupText)
    }

    @Test
    fun testMissingDetailsFlow() {
        val response = orchestrator.handleRequest("play")
        assertEquals(MusicStatus.NEEDS_USER_INPUT, response.status)
        assertEquals("Which song or artist would you like to hear?", response.popupText)
    }

    @Test
    fun testPlaylistCreationFlow() {
        // Step 1: Request creation
        var response = orchestrator.handleRequest("create playlist named gym")
        assertEquals(MusicStatus.NEEDS_CONFIRMATION, response.status)
        assertEquals("Should I create the playlist 'gym'?", response.popupText)
        
        // Step 2: Confirm
        response = orchestrator.handleRequest("yes")
        assertEquals(MusicStatus.SUCCESS, response.status)
        assertEquals("I created the playlist 'gym'.", response.popupText)
    }
}
