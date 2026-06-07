package com.nova.luna.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainPhase1StabilityTest {
    private lateinit var brain: CommandBrain
    private lateinit var executor: FakeActionExecutor

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        brain = CommandBrain(context)
        executor = FakeActionExecutor()
        injectActionExecutor(brain, executor)
    }

    @Test
    fun `ambiguous bare words return the safe fallback without calling any executor path`() {
        val expectations = mapOf(
            "compare" to "Please tell me what you'd like me to compare.",
            "cancel" to "There isn't an active task to cancel right now.",
            "continue" to "There isn't an active task to continue yet.",
            "send it" to "I need an active draft before I can send anything.",
            "play" to "Please tell me which song, video, or app you want.",
            "yes" to "Please tell me which app or task you want."
        )

        expectations.forEach { (phrase, expectedMessage) ->
            val result = brain.process(phrase)

            assertFalse("Expected failure for phrase: $phrase", result.success)
            assertEquals("Expected safe fallback for phrase: $phrase", expectedMessage, result.message)
            assertEquals("Expected unknown intent for phrase: $phrase", IntentType.UNKNOWN, result.intentType)
            assertEquals("Expected unknown action for phrase: $phrase", ActionType.UNKNOWN, result.actionType)
        }

        assertEquals(0, executor.totalCalls)
    }

    @Test
    fun `active grocery sessions keep grocery follow ups on the grocery route`() {
        executor.activeGrocerySession = true

        val result = brain.process("cheapest")

        assertTrue(result.success)
        assertEquals("Handled grocery", result.message)
        assertEquals(1, executor.groceryConversationCount)
        assertEquals("cheapest", executor.lastGroceryText)
        assertFalse(executor.lastGroceryUserConfirmed)
        assertEquals(1, executor.totalCalls)
    }

    @Test
    fun `active music sessions keep bare playback commands on the music route`() {
        executor.activeMusicSession = true

        val result = brain.process("pause")

        assertTrue(result.success)
        assertEquals("Handled music", result.message)
        assertEquals(1, executor.musicConversationCount)
        assertEquals("pause", executor.lastMusicText)
        assertEquals(1, executor.totalCalls)
    }

    private fun injectActionExecutor(brain: CommandBrain, actionExecutor: ActionExecutorGateway) {
        val routerField = CommandBrain::class.java.getDeclaredField("router")
        routerField.isAccessible = true
        val router = routerField.get(brain)

        val actionExecutorField = router.javaClass.getDeclaredField("actionExecutor")
        actionExecutorField.isAccessible = true
        actionExecutorField.set(router, actionExecutor)
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var totalCalls: Int = 0
            private set
        var groceryConversationCount: Int = 0
            private set
        var musicConversationCount: Int = 0
            private set
        var activeGrocerySession: Boolean = false
        var activeMusicSession: Boolean = false
        var lastGroceryText: String? = null
            private set
        var lastGroceryUserConfirmed: Boolean = false
            private set
        var lastMusicText: String? = null
            private set

        override fun execute(commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            return CommandResult.success(
                message = "Executed",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        override fun hasActiveCabBookingSession(): Boolean = false

        override fun cancelCabBookingSession(): CommandResult {
            totalCalls += 1
            return CommandResult.success("Cancelled cab")
        }

        override fun handleCabBookingText(rawText: String): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled cab")
        }

        override fun hasActiveFoodBookingSession(): Boolean = false

        override fun cancelFoodBookingSession(): CommandResult {
            totalCalls += 1
            return CommandResult.success("Cancelled food")
        }

        override fun handleFoodBookingText(rawText: String): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled food")
        }

        override fun hasActiveGroceryBookingSession(): Boolean = activeGrocerySession

        override fun cancelGroceryBookingSession(): CommandResult {
            totalCalls += 1
            return CommandResult.success("Cancelled grocery")
        }

        override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult {
            totalCalls += 1
            groceryConversationCount += 1
            lastGroceryText = rawText
            lastGroceryUserConfirmed = userConfirmed
            return CommandResult.success(
                message = "Handled grocery",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING
            )
        }

        override fun hasActivePhoneContactSession(): Boolean = false

        override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled phone")
        }

        override fun hasActiveCommunicationSession(): Boolean = false

        override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled communication")
        }

        override fun hasActiveContentCreationSession(): Boolean = false

        override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled content")
        }

        override fun hasActiveMediaSession(): Boolean = false

        override fun handleMediaText(rawText: String, commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled media")
        }

        override fun hasActiveShoppingSession(): Boolean = false

        override fun handleShoppingText(rawText: String, commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            return CommandResult.success("Handled shopping")
        }

        override fun hasActiveMusicSession(): Boolean = activeMusicSession

        override fun handleMusicText(rawText: String, commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            musicConversationCount += 1
            lastMusicText = rawText
            return CommandResult.success(
                message = "Handled music",
                intentType = IntentType.CONTROL,
                actionType = ActionType.MUSIC
            )
        }
    }
}
