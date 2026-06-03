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
class CommandBrainTypeTextTest {
    private lateinit var context: Context
    private lateinit var brain: CommandBrain
    private lateinit var service: NovaAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        brain = CommandBrain(context)
        service = Mockito.mock(NovaAccessibilityService::class.java)
        Mockito.`when`(service.typeText("hello")).thenReturn(true)
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
    fun `type text aliases route through the same text entry path`() {
        val phrases = listOf(
            "type hello",
            "write hello",
            "enter hello",
            "type message hello"
        )

        phrases.forEach { phrase ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected text entry intent for phrase: $phrase",
                IntentType.TEXT_ENTRY,
                result.intentType
            )
            assertEquals(
                "Expected type text action for phrase: $phrase",
                ActionType.TYPE_TEXT,
                result.actionType
            )
            assertEquals("Typed text.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals("hello", result.entities["text"])
        }

        Mockito.verify(service, Mockito.times(4)).typeText("hello")
    }
}
