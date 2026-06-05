package com.nova.luna.brain

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import com.nova.luna.service.NovaAccessibilityService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CommandBrainRecentsTest {
    private lateinit var service: NovaAccessibilityService
    private lateinit var brain: CommandBrain

    @Before
    fun setUp() {
        service = Robolectric.buildService(NovaAccessibilityService::class.java)
            .create()
            .get()
        installServiceInstance()

        val context = ApplicationProvider.getApplicationContext<Context>()
        brain = CommandBrain(context)
    }

    @After
    fun tearDown() {
        service.onDestroy()
    }

    private fun installServiceInstance() {
        val field = NovaAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, service)
    }

    @Test
    fun `recents aliases route through the same navigation path`() {
        val phrases = listOf(
            "recent apps",
            "recents",
            "show recent apps",
            "open recents",
            "Luna show recent apps"
        )

        phrases.forEach { phrase ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected navigation intent for phrase: $phrase",
                IntentType.NAVIGATION,
                result.intentType
            )
            assertEquals(
                "Expected recents action for phrase: $phrase",
                ActionType.OPEN_RECENTS,
                result.actionType
            )
            assertEquals("Opening recent apps.", result.message)
            assertFalse(result.shouldStopListening)
        }

        assertEquals(
            listOf(
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                AccessibilityService.GLOBAL_ACTION_RECENTS
            ),
            shadowOf(service).globalActionsPerformed
        )
    }
}
