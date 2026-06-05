package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterGroceryTest {
    @Test
    fun `grocery session routes through the grocery session helper`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)
        val action = BrainAction(
            intent = "grocery_session",
            reply = "Continuing the grocery flow.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "yes")
        )

        val result = router.route(action, userConfirmed = true)

        assertTrue(result.success)
        assertEquals(1, executor.groceryBookingTextCalls)
        assertEquals("yes", executor.lastGroceryBookingText)
        assertTrue(executor.lastGroceryUserConfirmed)
    }

    @Test
    fun `grocery booking actions start through the generic executor path`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)
        val action = BrainAction(
            intent = "grocery_booking",
            reply = "I can prepare the grocery flow.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "Luna order milk and bread")
        )

        val result = router.route(action)

        assertTrue(result.success)
        assertEquals(1, executor.executeCalls)
        assertEquals(0, executor.groceryBookingTextCalls)
        assertEquals(ActionType.GROCERY_BOOKING, executor.lastCommandIntent?.actionType)
    }

    @Test
    fun `grocery session helper keeps follow up text unconfirmed by default`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)

        router.routeGroceryConversation("dismiss")

        assertEquals(1, executor.groceryBookingTextCalls)
        assertFalse(executor.lastGroceryUserConfirmed)
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCalls: Int = 0
        var groceryBookingTextCalls: Int = 0
        var lastCommandIntent: CommandIntent? = null
        var lastGroceryBookingText: String? = null
        var lastGroceryUserConfirmed: Boolean = false

        override fun execute(commandIntent: CommandIntent): CommandResult {
            executeCalls += 1
            lastCommandIntent = commandIntent
            return CommandResult.success(
                message = "executed",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        override fun hasActiveCabBookingSession(): Boolean = false

        override fun cancelCabBookingSession(): CommandResult {
            return CommandResult.success("cancelled")
        }

        override fun handleCabBookingText(rawText: String): CommandResult {
            return CommandResult.success("cab")
        }

        override fun hasActiveFoodBookingSession(): Boolean = false

        override fun cancelFoodBookingSession(): CommandResult {
            return CommandResult.success("food cancelled")
        }

        override fun handleFoodBookingText(rawText: String): CommandResult {
            return CommandResult.success(
                message = "food handled",
                intentType = IntentType.FOOD_ORDER,
                actionType = ActionType.FOOD_ORDER,
                entities = mapOf("rawText" to rawText)
            )
        }

        override fun hasActiveGroceryBookingSession(): Boolean = true

        override fun cancelGroceryBookingSession(): CommandResult {
            return CommandResult.success("grocery cancelled")
        }

        override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult {
            groceryBookingTextCalls += 1
            lastGroceryBookingText = rawText
            lastGroceryUserConfirmed = userConfirmed
            return CommandResult.success(
                message = "grocery handled",
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = mapOf(
                    "rawText" to rawText,
                    "finalUserConfirmed" to userConfirmed.toString()
                )
            )
        }
    }
}
