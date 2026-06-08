package com.nova.luna.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)

class CommandBrainPhase2BrainServiceFallbackTest {
    private val codec = BrainActionJsonCodec()

    private lateinit var brain: CommandBrain
    private lateinit var executor: FakeActionExecutor

    @Before
    fun setUp() {
        val misleadingAction = BrainAction(
            intent = "open_app",
            reply = "BrainService should not claim this music command.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "play Arijit Singh song",
                "appName" to "youtube"
            )
        )

        val brainService = BrainService(
            provider = StaticBrainProvider(codec.encode(misleadingAction))
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        brain = CommandBrain(context, brainService = brainService)
        executor = FakeActionExecutor()
        injectActionExecutor(brain, executor)
    }

    @Test
    fun `music commands still use the parser fallback path`() {
        val result = brain.process("play Arijit Singh song")

        assertTrue(result.success)
        assertEquals(IntentType.CONTROL, result.intentType)
        assertEquals(ActionType.MUSIC, result.actionType)
        assertEquals("Handled music", result.message)
        assertEquals(1, executor.musicConversationCount)
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

    private class StaticBrainProvider(
        private val response: String
    ) : BrainProvider {
        override fun analyze(request: BrainRequest): String {
            return response
        }
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var totalCalls: Int = 0
            private set
        var musicConversationCount: Int = 0
            private set

        override fun execute(commandIntent: CommandIntent): CommandResult {
            totalCalls += 1
            if (commandIntent.actionType == ActionType.MUSIC) {
                return handleMusicText(commandIntent.rawText, commandIntent)
            }

            return CommandResult.success(
                message = "Executed",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        override fun hasActiveCabBookingSession(): Boolean = false
        override fun cancelCabBookingSession(): CommandResult = CommandResult.success("Cancelled cab")
        override fun handleCabBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled cab")

        override fun hasActiveFoodBookingSession(): Boolean = false
        override fun cancelFoodBookingSession(): CommandResult = CommandResult.success("Cancelled food")
        override fun handleFoodBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled food")

        override fun hasActiveGroceryBookingSession(): Boolean = false
        override fun cancelGroceryBookingSession(): CommandResult = CommandResult.success("Cancelled grocery")
        override fun handleGroceryBookingText(rawText: String, commandIntent: CommandIntent, userConfirmed: Boolean): CommandResult = CommandResult.success("Handled grocery")

        override fun hasActivePhoneContactSession(): Boolean = false
        override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled phone")

        override fun hasActiveCommunicationSession(): Boolean = false
        override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled communication")

        override fun hasActiveContentCreationSession(): Boolean = false
        override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled content")

        override fun hasActiveMediaSession(): Boolean = false
        override fun handleMediaText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled media")

        override fun hasActiveShoppingSession(): Boolean = false
        override fun handleShoppingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled shopping")

        override fun hasActiveMusicSession(): Boolean = false
        override fun handleMusicText(rawText: String, commandIntent: CommandIntent): CommandResult {
            musicConversationCount += 1
            return CommandResult.success(
                message = "Handled music",
                intentType = IntentType.CONTROL,
                actionType = ActionType.MUSIC
            )
        }
    }
}
