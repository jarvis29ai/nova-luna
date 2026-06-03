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
class CommandBrainTapClickTest {
    private lateinit var context: Context
    private lateinit var brain: CommandBrain
    private lateinit var service: NovaAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        brain = CommandBrain(context)
        service = Mockito.mock(NovaAccessibilityService::class.java)
        Mockito.`when`(service.clickByTextOrDescription("settings")).thenReturn(true)
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
    fun `tap click and press aliases route through the same click path`() {
        val phrases = linkedMapOf(
            "tap settings" to "settings",
            "click settings" to "settings",
            "press settings" to "settings",
            "tap on settings" to "settings",
            "click on settings" to "settings"
        )

        phrases.forEach { (phrase, expectedTarget) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected interaction intent for phrase: $phrase",
                IntentType.INTERACTION,
                result.intentType
            )
            assertEquals(
                "Expected click text action for phrase: $phrase",
                ActionType.CLICK_TEXT,
                result.actionType
            )
            assertEquals("Tapped $expectedTarget.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedTarget, result.entities["text"])
        }

        Mockito.verify(service, Mockito.times(5)).clickByTextOrDescription("settings")
    }
}
