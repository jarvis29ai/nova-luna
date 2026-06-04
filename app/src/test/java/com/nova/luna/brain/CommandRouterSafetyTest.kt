package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.model.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterSafetyTest {
    @Test
    fun `confirmation required actions do not reach the executor until the user confirms`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)
        val action = BrainAction(
            intent = "cab_booking",
            reply = "I can prepare a cab to DB Mall.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to "book cheapest auto to DB Mall",
                "dropLocation" to "DB Mall"
            ),
            nextQuestion = "Where should I pick you up from?"
        )

        val pending = router.route(action)
        assertFalse(pending.success)
        assertTrue(pending.awaitingConfirmation)
        assertEquals(SafetyLevel.CONFIRMATION_REQUIRED, pending.safetyDecision.level)
        assertEquals(0, executor.executeCalls)

        val confirmed = router.route(action, userConfirmed = true)
        assertTrue(confirmed.success)
        assertEquals(1, executor.executeCalls)
        assertEquals(ActionType.CAB_BOOKING, executor.lastCommandIntent?.actionType)
    }

    @Test
    fun `human only actions never reach the executor`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)
        val action = BrainAction(
            intent = "human_only",
            reply = "That needs to stay manual.",
            actionType = BrainActionType.HUMAN_ONLY,
            riskLevel = BrainRiskLevel.BLOCKED,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "send money")
        )

        val result = router.route(action)

        assertFalse(result.success)
        assertEquals(SafetyLevel.HUMAN_ONLY, result.safetyDecision.level)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun `cab conversation is still routed through the gate and executor boundary`() {
        val executor = FakeActionExecutor()
        val router = CommandRouter(executor)

        val result = router.routeCabConversation("book auto to railway station")

        assertTrue(result.success)
        assertEquals(1, executor.cabBookingTextCalls)
        assertEquals("book auto to railway station", executor.lastCabBookingText)
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCalls: Int = 0
        var cabBookingTextCalls: Int = 0
        var lastCommandIntent: CommandIntent? = null
        var lastCabBookingText: String? = null

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
            return CommandResult.success(message = "cancelled")
        }

        override fun handleCabBookingText(rawText: String): CommandResult {
            cabBookingTextCalls += 1
            lastCabBookingText = rawText
            return CommandResult.success(
                message = "cab handled",
                intentType = IntentType.CAB_BOOKING,
                actionType = ActionType.CAB_BOOKING,
                entities = mapOf("rawText" to rawText)
            )
        }

        override fun hasActiveFoodBookingSession(): Boolean = false

        override fun cancelFoodBookingSession(): CommandResult {
            return CommandResult.success(message = "food cancelled")
        }

        override fun handleFoodBookingText(rawText: String): CommandResult {
            return CommandResult.success(
                message = "food handled",
                intentType = IntentType.FOOD_ORDER,
                actionType = ActionType.FOOD_ORDER,
                entities = mapOf("rawText" to rawText)
            )
        }

        override fun hasActiveGroceryBookingSession(): Boolean = false

        override fun cancelGroceryBookingSession(): CommandResult {
            return CommandResult.success(message = "grocery cancelled")
        }

        override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult {
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
