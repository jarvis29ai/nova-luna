package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterSafetyTest {

    @Test
    fun `sensitive intent routes to action executor`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)
        val intent = CommandIntent(
            rawText = "transfer money",
            intentType = IntentType.SENSITIVE,
            actionType = ActionType.BLOCKED
        )

        val result = router.route(intent)

        assertEquals(1, executor.executeCount)
        assertTrue(result.success)
    }

    @Test
    fun `navigation intent routes to action executor`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)
        val intent = CommandIntent(
            rawText = "go home",
            intentType = IntentType.NAVIGATION,
            actionType = ActionType.GO_HOME
        )

        val result = router.route(intent)

        assertEquals(1, executor.executeCount)
        assertTrue(result.success)
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCount = 0

        override fun execute(commandIntent: CommandIntent): CommandResult {
            executeCount++
            return CommandResult.success("Executed")
        }

        override fun hasActiveCabBookingSession(): Boolean = false
        override fun cancelCabBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleCabBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")

        override fun hasActiveFoodBookingSession(): Boolean = false
        override fun cancelFoodBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleFoodBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")

        override fun hasActiveGroceryBookingSession(): Boolean = false
        override fun cancelGroceryBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleGroceryBookingText(rawText: String, commandIntent: CommandIntent, userConfirmed: Boolean): CommandResult = CommandResult.success("Handled")

        override fun hasActivePhoneContactSession(): Boolean = false
        override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveCommunicationSession(): Boolean = false
        override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveContentCreationSession(): Boolean = false
        override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveMediaSession(): Boolean = false
        override fun handleMediaText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveShoppingSession(): Boolean = false
        override fun handleShoppingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveMusicSession(): Boolean = false
        override fun handleMusicText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled music")
    }
}
