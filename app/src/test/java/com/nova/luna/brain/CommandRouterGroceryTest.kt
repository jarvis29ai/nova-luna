package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterGroceryTest {

    @Test
    fun `grocery phrases route into the grocery booking branch`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)

        val intent = CommandIntent(
            rawText = "buy milk",
            intentType = IntentType.GROCERY_BOOKING,
            actionType = ActionType.GROCERY_BOOKING
        )

        val result = router.route(intent)

        assertEquals(1, executor.executeCount)
        assertTrue(result.success)
    }

    @Test
    fun `ongoing grocery conversation routes to action executor`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)

        executor.activeGrocerySession = true

        val result = router.routeGroceryConversation("yes please", CommandIntent(rawText = "yes please"))

        assertEquals(1, executor.groceryConversationCount)
        assertTrue(result.success)
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCount = 0
        var groceryConversationCount = 0
        var activeGrocerySession = false

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

        override fun hasActiveGroceryBookingSession(): Boolean = activeGrocerySession
        override fun cancelGroceryBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleGroceryBookingText(rawText: String, commandIntent: CommandIntent, userConfirmed: Boolean): CommandResult {
            groceryConversationCount++
            return CommandResult.success("Handled grocery")
        }

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
        override fun handleConfirmationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled confirmation")
    }
}
