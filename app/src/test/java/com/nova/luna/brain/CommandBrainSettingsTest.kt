package com.nova.luna.brain

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainSettingsTest {
    @Test
    fun `general settings aliases route through the same settings path`() {
        val phrases = linkedMapOf(
            "open settings" to "open_settings",
            "launch settings" to "open_settings",
            "open phone settings" to "open_settings",
            "Luna open settings" to "open_settings"
        )

        phrases.forEach { (phrase, expectedValue) ->
            val context = RecordingContext(ApplicationProvider.getApplicationContext())
            val brain = CommandBrain(context)

            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected sensitive intent for phrase: $phrase",
                IntentType.SENSITIVE,
                result.intentType
            )
            assertEquals(
                "Expected general settings action for phrase: $phrase",
                ActionType.OPEN_SETTINGS,
                result.actionType
            )
            assertEquals("Opening system settings.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedValue, result.entities["value"])
            assertEquals(1, context.launchedIntents.size)
            assertEquals(Settings.ACTION_SETTINGS, context.launchedIntents.single().action)
            assertTrue(
                "Expected NEW_TASK flag for phrase: $phrase",
                context.launchedIntents.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
            )
        }
    }

    @Test
    fun `accessibility settings aliases route through the same accessibility path`() {
        val phrases = linkedMapOf(
            "open accessibility settings" to "open_accessibility_settings",
            "open nova accessibility settings" to "open_accessibility_settings"
        )

        phrases.forEach { (phrase, expectedValue) ->
            val context = RecordingContext(ApplicationProvider.getApplicationContext())
            val brain = CommandBrain(context)

            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected sensitive intent for phrase: $phrase",
                IntentType.SENSITIVE,
                result.intentType
            )
            assertEquals(
                "Expected accessibility settings action for phrase: $phrase",
                ActionType.OPEN_ACCESSIBILITY_SETTINGS,
                result.actionType
            )
            assertEquals("Opening accessibility settings.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedValue, result.entities["value"])
            assertEquals(1, context.launchedIntents.size)
            assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, context.launchedIntents.single().action)
            assertTrue(
                "Expected NEW_TASK flag for phrase: $phrase",
                context.launchedIntents.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
            )
        }
    }

    private class RecordingContext(baseContext: Context) : ContextWrapper(baseContext) {
        val launchedIntents = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun startActivity(intent: Intent) {
            launchedIntents.add(Intent(intent))
        }
    }
}
