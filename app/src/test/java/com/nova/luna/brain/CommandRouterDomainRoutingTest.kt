package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRouterDomainRoutingTest {
    @Test
    fun `shopping and music conversation routes reach the domain executors`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)

        val shoppingResult = router.routeShoppingConversation("buy a phone")
        val musicResult = router.routeMusicConversation("pause music")

        assertEquals(1, executor.shoppingConversationCount)
        assertEquals(1, executor.musicConversationCount)
        assertEquals("Handled shopping", shoppingResult.message)
        assertEquals("Handled music", musicResult.message)
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var shoppingConversationCount = 0
        var musicConversationCount = 0

        override fun execute(commandIntent: CommandIntent): CommandResult = CommandResult.success("Executed")

        override fun hasActiveCabBookingSession(): Boolean = false
        override fun cancelCabBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleCabBookingText(rawText: String): CommandResult = CommandResult.success("Handled cab")

        override fun hasActiveFoodBookingSession(): Boolean = false
        override fun cancelFoodBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleFoodBookingText(rawText: String): CommandResult = CommandResult.success("Handled food")

        override fun hasActiveGroceryBookingSession(): Boolean = false
        override fun cancelGroceryBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult = CommandResult.success("Handled grocery")

        override fun hasActivePhoneContactSession(): Boolean = false
        override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled phone")

        override fun hasActiveCommunicationSession(): Boolean = false
        override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled communication")

        override fun hasActiveContentCreationSession(): Boolean = false
        override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled content")

        override fun hasActiveMediaSession(): Boolean = false
        override fun handleMediaText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled media")

        override fun hasActiveShoppingSession(): Boolean = false
        override fun handleShoppingText(rawText: String, commandIntent: CommandIntent): CommandResult {
            shoppingConversationCount++
            return CommandResult.success("Handled shopping")
        }

        override fun hasActiveMusicSession(): Boolean = false
        override fun handleMusicText(rawText: String, commandIntent: CommandIntent): CommandResult {
            musicConversationCount++
            return CommandResult.success("Handled music")
        }
    }
}
