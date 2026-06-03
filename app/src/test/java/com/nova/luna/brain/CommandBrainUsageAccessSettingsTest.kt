package com.nova.luna.brain

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainUsageAccessSettingsTest {
    @Test
    fun `usage access settings aliases route through the same usage access path`() {
        val phrases = linkedMapOf(
            "open usage access settings" to "open_usage_access_settings",
            "open usage settings" to "open_usage_access_settings",
            "open app usage settings" to "open_usage_access_settings",
            "open usage permission" to "open_usage_access_settings",
            "open app usage permission" to "open_usage_access_settings"
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
                "Expected usage access settings action for phrase: $phrase",
                ActionType.OPEN_USAGE_ACCESS_SETTINGS,
                result.actionType
            )
            assertEquals("Opening usage access settings.", result.message)
            assertFalse(result.shouldStopListening)
            assertTrue(
                "Expected explicit usage-access command to pass safety for phrase: $phrase",
                result.safetyDecision.allowed
            )
            assertFalse(result.safetyDecision.requiresBiometric)
            assertEquals(expectedValue, result.entities["value"])
            assertEquals(1, context.launchedIntents.size)
            assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, context.launchedIntents.single().action)
            assertTrue(
                "Expected NEW_TASK flag for phrase: $phrase",
                context.launchedIntents.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
            )
        }
    }

    @Test
    fun `usage access safety only allows explicit usage access wording`() {
        val safetyGate = SafetyGate()
        val explicitIntent = CommandIntent(
            rawText = "open usage access settings",
            intentType = IntentType.SENSITIVE,
            actionType = ActionType.OPEN_USAGE_ACCESS_SETTINGS,
            entities = mapOf("value" to "open_usage_access_settings")
        )
        val genericIntent = CommandIntent(
            rawText = "open settings",
            intentType = IntentType.SENSITIVE,
            actionType = ActionType.OPEN_USAGE_ACCESS_SETTINGS,
            entities = mapOf("value" to "open_usage_access_settings")
        )

        val explicitDecision = safetyGate.evaluate(explicitIntent)
        assertTrue(explicitDecision.allowed)
        assertFalse(explicitDecision.requiresBiometric)

        val genericDecision = safetyGate.evaluate(genericIntent)
        assertFalse(genericDecision.allowed)
        assertTrue(genericDecision.requiresBiometric)
    }

    private class RecordingContext(baseContext: Context) : ContextWrapper(baseContext) {
        val launchedIntents = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun startActivity(intent: Intent) {
            launchedIntents.add(Intent(intent))
        }
    }
}
