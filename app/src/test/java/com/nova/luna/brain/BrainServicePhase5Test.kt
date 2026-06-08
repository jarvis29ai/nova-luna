package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase5Test {

    @Test
    fun `brain service provides structured actions`() {
        val service = BrainService()
        val action = service.process("book cab to DB Mall")

        assertEquals("cab_booking", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertEquals("DB Mall", action.params["dropLocation"])
    }

    @Test
    fun `command router routes to action executor`() {
        val service = BrainService()
        val action = service.process("book cab to DB Mall")
        val executor = FakeActionExecutor()
        val commandRouter = CommandRouter(executor)

        val commandResult = commandRouter.route(action)

        assertEquals(1, executor.executeCount)
        assertTrue(commandResult.success)
        assertEquals("Executed", commandResult.message)
    }

    @Test
    fun `grocery planning stays local`() {
        val service = BrainService()
        val action = service.process("order 1kg sugar from bigbasket")

        assertEquals("grocery_booking", action.intent)
        assertEquals(BrainActionType.EXTERNAL_ACTION, action.actionType)
        assertEquals("sugar", action.params["items"])
        assertEquals("bigbasket", action.params["preferredProvider"])
    }

    @Test
    fun `flutter module is not added by accident`() {
        val settingsGradle = locateProjectFile("settings.gradle", "settings.gradle.kts").readText()
        org.junit.Assert.assertFalse(settingsGradle.contains("flutter_app"))
    }

    private fun locateProjectFile(vararg names: String): File {
        var current = File(".").canonicalFile
        while (true) {
            names.forEach { name ->
                val candidate = File(current, name)
                if (candidate.exists()) {
                    return candidate
                }
            }

            val parent = current.parentFile ?: break
            current = parent
        }

        error("Unable to locate project file: ${names.joinToString()}")
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCount: Int = 0

        override fun execute(commandIntent: CommandIntent): CommandResult {
            executeCount += 1
            return CommandResult.success(
                message = "Executed",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        override fun hasActiveCabBookingSession(): Boolean = false
        override fun cancelCabBookingSession(): CommandResult = CommandResult.success(message = "Cancelled")
        override fun handleCabBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled")

        override fun hasActiveFoodBookingSession(): Boolean = false
        override fun cancelFoodBookingSession(): CommandResult = CommandResult.success(message = "Cancelled food")
        override fun handleFoodBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled food")

        override fun hasActiveGroceryBookingSession(): Boolean = false
        override fun cancelGroceryBookingSession(): CommandResult = CommandResult.success(message = "Cancelled grocery")
        override fun handleGroceryBookingText(rawText: String, commandIntent: CommandIntent, userConfirmed: Boolean): CommandResult {
            return CommandResult.success(
                message = "Handled grocery",
                intentType = com.nova.luna.model.IntentType.GROCERY_BOOKING,
                actionType = com.nova.luna.model.ActionType.GROCERY_BOOKING,
                entities = mapOf(
                    "rawText" to rawText,
                    "finalUserConfirmed" to userConfirmed.toString()
                )
            )
        }

        override fun hasActivePhoneContactSession(): Boolean = false
        override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled")
        override fun hasActiveCommunicationSession(): Boolean = false
        override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled")
        override fun hasActiveContentCreationSession(): Boolean = false
        override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled")
        override fun hasActiveMediaSession(): Boolean = false
        override fun handleMediaText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled")
        override fun hasActiveShoppingSession(): Boolean = false
        override fun handleShoppingText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled")
        override fun hasActiveMusicSession(): Boolean = false
        override fun handleMusicText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success(message = "Handled music")
    }
}
