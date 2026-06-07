package com.nova.luna.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainPhase5OnlineAiConsentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val brainService = onlineBrainService()
    private val brain = CommandBrain(context, brainService = brainService)

    @Test
    fun `online helper asks for consent and then replays after yes`() {
        val firstResult = brain.process("compare apples and oranges")

        assertTrue("firstResult=$firstResult", firstResult.awaitingConfirmation)
        assertTrue(firstResult.message.contains("online AI", ignoreCase = true))

        val secondResult = brain.process("yes")

        assertTrue("secondResult=$secondResult", secondResult.success)
        assertEquals("Here is a safe draft.", secondResult.message)
    }
}
