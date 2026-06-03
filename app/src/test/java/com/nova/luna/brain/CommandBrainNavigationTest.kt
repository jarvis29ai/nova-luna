package com.nova.luna.brain

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
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainNavigationTest {
    private lateinit var context: Context
    private lateinit var brain: CommandBrain
    private lateinit var service: NovaAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Mockito.mock(NovaAccessibilityService::class.java)
        installServiceInstance(service)
        brain = CommandBrain(context)
    }

    @After
    fun tearDown() {
        installServiceInstance(null)
    }

    private fun installServiceInstance(instance: NovaAccessibilityService?) {
        val field = NovaAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }

    @Test
    fun `home aliases route through the same home path`() {
        Mockito.`when`(service.goHome()).thenReturn(true)

        val phrases = linkedMapOf(
            "go home" to "go_home",
            "home" to "go_home",
            "back to home" to "go_home"
        )

        phrases.forEach { (phrase, expectedCommand) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(IntentType.NAVIGATION, result.intentType)
            assertEquals(ActionType.GO_HOME, result.actionType)
            assertEquals("Going home.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedCommand, result.entities["command"])
        }

        Mockito.verify(service, Mockito.times(3)).goHome()
        Mockito.verify(service, Mockito.never()).goBack()
        Mockito.verify(service, Mockito.never()).openRecents()
        Mockito.verify(service, Mockito.never()).openNotifications()
    }

    @Test
    fun `back aliases route through the same back path`() {
        Mockito.`when`(service.goBack()).thenReturn(true)

        val phrases = linkedMapOf(
            "go back" to "go_back",
            "back" to "go_back"
        )

        phrases.forEach { (phrase, expectedCommand) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(IntentType.NAVIGATION, result.intentType)
            assertEquals(ActionType.GO_BACK, result.actionType)
            assertEquals("Going back.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedCommand, result.entities["command"])
        }

        Mockito.verify(service, Mockito.times(2)).goBack()
        Mockito.verify(service, Mockito.never()).goHome()
        Mockito.verify(service, Mockito.never()).openRecents()
        Mockito.verify(service, Mockito.never()).openNotifications()
    }

    @Test
    fun `recents aliases route through the same recents path`() {
        Mockito.`when`(service.openRecents()).thenReturn(true)

        val phrases = linkedMapOf(
            "recent apps" to "open_recents",
            "open recents" to "open_recents"
        )

        phrases.forEach { (phrase, expectedCommand) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(IntentType.NAVIGATION, result.intentType)
            assertEquals(ActionType.OPEN_RECENTS, result.actionType)
            assertEquals("Opening recent apps.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedCommand, result.entities["command"])
        }

        Mockito.verify(service, Mockito.times(2)).openRecents()
        Mockito.verify(service, Mockito.never()).goHome()
        Mockito.verify(service, Mockito.never()).goBack()
        Mockito.verify(service, Mockito.never()).openNotifications()
    }

    @Test
    fun `notification aliases route through the same notification path`() {
        Mockito.`when`(service.openNotifications()).thenReturn(true)

        val phrases = linkedMapOf(
            "open notifications" to "open_notifications",
            "show notifications" to "open_notifications"
        )

        phrases.forEach { (phrase, expectedCommand) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(IntentType.NAVIGATION, result.intentType)
            assertEquals(ActionType.OPEN_NOTIFICATIONS, result.actionType)
            assertEquals("Opening notifications.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedCommand, result.entities["command"])
        }

        Mockito.verify(service, Mockito.times(2)).openNotifications()
        Mockito.verify(service, Mockito.never()).goHome()
        Mockito.verify(service, Mockito.never()).goBack()
        Mockito.verify(service, Mockito.never()).openRecents()
    }
}
