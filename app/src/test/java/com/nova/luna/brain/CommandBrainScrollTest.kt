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
class CommandBrainScrollTest {
    private lateinit var context: Context
    private lateinit var brain: CommandBrain
    private lateinit var service: NovaAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        brain = CommandBrain(context)
        service = Mockito.mock(NovaAccessibilityService::class.java)
        Mockito.`when`(service.scrollForward()).thenReturn(true)
        Mockito.`when`(service.scrollBackward()).thenReturn(true)
        installServiceInstance(service)
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
    fun `scroll down aliases route through the same forward scroll path`() {
        val phrases = linkedMapOf(
            "scroll down" to "scroll_down",
            "swipe down" to "scroll_down",
            "move down" to "scroll_down"
        )

        phrases.forEach { (phrase, expectedCommand) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected navigation intent for phrase: $phrase",
                IntentType.NAVIGATION,
                result.intentType
            )
            assertEquals(
                "Expected scroll forward action for phrase: $phrase",
                ActionType.SCROLL_FORWARD,
                result.actionType
            )
            assertEquals("Scrolled down.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedCommand, result.entities["command"])
        }

        Mockito.verify(service, Mockito.times(3)).scrollForward()
        Mockito.verify(service, Mockito.never()).scrollBackward()
    }

    @Test
    fun `scroll up aliases route through the same backward scroll path`() {
        val phrases = linkedMapOf(
            "scroll up" to "scroll_up",
            "swipe up" to "scroll_up",
            "move up" to "scroll_up"
        )

        phrases.forEach { (phrase, expectedCommand) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected navigation intent for phrase: $phrase",
                IntentType.NAVIGATION,
                result.intentType
            )
            assertEquals(
                "Expected scroll backward action for phrase: $phrase",
                ActionType.SCROLL_BACKWARD,
                result.actionType
            )
            assertEquals("Scrolled up.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedCommand, result.entities["command"])
        }

        Mockito.verify(service, Mockito.times(3)).scrollBackward()
        Mockito.verify(service, Mockito.never()).scrollForward()
    }
}
