package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase5Test {
    private val codec = BrainActionJsonCodec()

    @Test
    fun `unavailable model falls back to LocalMockBrainProvider`() {
        val service = BrainService(
            actionJsonModel = UnavailablePhoneModel(BrainModelRole.ACTION_JSON)
        )

        val diagnostics = service.diagnose("book cheapest auto to DB Mall")

        assertEquals(BrainModelRole.ACTION_JSON, diagnostics.selectedRole)
        assertFalse(diagnostics.modelAvailable ?: true)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertEquals("cab_booking", diagnostics.finalBrainAction.intent)
    }

    @Test
    fun `dangerous model output is rejected before fallback`() {
        val dangerousAction = BrainAction(
            intent = "cab_booking",
            reply = "Ready to book and pay now.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "book cheapest auto to DB Mall",
                "dropLocation" to "DB Mall"
            ),
            nextQuestion = "Confirm booking now?"
        )

        val service = BrainService(
            actionJsonModel = ConstantPhoneModel(
                role = BrainModelRole.ACTION_JSON,
                response = BrainModelResult.available(
                    role = BrainModelRole.ACTION_JSON,
                    candidateAction = dangerousAction,
                    rawResponse = codec.encode(dangerousAction)
                )
            )
        )

        val diagnostics = service.diagnose("book cheapest auto to DB Mall")
        val router = CommandRouter(FakeActionExecutor())
        val commandResult = router.route(diagnostics.finalBrainAction)

        assertFalse(diagnostics.validatorResult)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertEquals("cab_booking", diagnostics.finalBrainAction.intent)
        assertTrue(commandResult.awaitingConfirmation)
    }

    @Test
    fun `safe final action still passes through SafetyGate and does not execute directly`() {
        val service = BrainService()
        val action = service.process("book cab to DB Mall")
        val executor = FakeActionExecutor()
        val commandRouter = CommandRouter(executor)

        val commandResult = commandRouter.route(action)

        assertTrue(commandResult.awaitingConfirmation)
        assertEquals(0, executor.executeCount)
        assertFalse(commandResult.success)
    }

    @Test
    fun `flutter app remains isolated from phase five brain worktree changes`() {
        val settingsFile = listOf(
            java.io.File("settings.gradle"),
            java.io.File("../settings.gradle")
        ).firstOrNull { it.exists() }
            ?: error("Could not find settings.gradle from the unit test working directory.")

        val settingsGradle = settingsFile.readText()

        assertTrue(settingsGradle.contains(":app"))
        assertTrue(settingsGradle.contains(":wear"))
        assertTrue(settingsGradle.contains(":shared"))
        assertFalse(settingsGradle.contains("flutter_app"))
    }

    private class ConstantPhoneModel(
        override val role: BrainModelRole,
        private val response: BrainModelResult
    ) : PhoneBrainModel {
        override val available: Boolean
            get() = response.available

        override fun generate(request: BrainRequest, routeDecision: com.nova.luna.model.BrainRouteDecision): BrainModelResult {
            return response
        }
    }

    private class UnavailablePhoneModel(
        override val role: BrainModelRole
    ) : PhoneBrainModel {
        override val available: Boolean = false

        override fun generate(request: BrainRequest, routeDecision: com.nova.luna.model.BrainRouteDecision): BrainModelResult {
            return BrainModelResult.unavailable(
                role = role,
                reason = "Model unavailable for test."
            )
        }
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCount: Int = 0

        override fun execute(commandIntent: com.nova.luna.model.CommandIntent): CommandResult {
            executeCount += 1
            return CommandResult.success(
                message = "Executed",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        override fun hasActiveCabBookingSession(): Boolean = false

        override fun cancelCabBookingSession(): CommandResult {
            return CommandResult.success("Cancelled")
        }

        override fun handleCabBookingText(rawText: String): CommandResult {
            return CommandResult.success("Handled")
        }

        override fun hasActiveFoodBookingSession(): Boolean = false

        override fun cancelFoodBookingSession(): CommandResult {
            return CommandResult.success("Cancelled food")
        }

        override fun handleFoodBookingText(rawText: String): CommandResult {
            return CommandResult.success(
                message = "Handled food",
                intentType = com.nova.luna.model.IntentType.FOOD_ORDER,
                actionType = com.nova.luna.model.ActionType.FOOD_ORDER,
                entities = mapOf("rawText" to rawText)
            )
        }

        override fun hasActiveGroceryBookingSession(): Boolean = false

        override fun cancelGroceryBookingSession(): CommandResult {
            return CommandResult.success("Cancelled grocery")
        }

        override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult {
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
    }
}
