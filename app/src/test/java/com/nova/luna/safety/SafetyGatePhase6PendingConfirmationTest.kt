package com.nova.luna.safety

import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.memory.PendingConfirmationType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGatePhase6PendingConfirmationTest {
    private val safetyGate = SafetyGate()

    @Test
    fun `matching pending confirmation allows the confirmed brain action`() {
        val brainAction = groceryBrainAction()
        val decision = safetyGate.evaluate(
            action = brainAction,
            pendingConfirmation = pendingConfirmation(brainAction),
            userConfirmed = true
        )

        assertTrue(decision.allowed)
        assertEquals(SafetyLevel.SAFE, decision.level)
        assertFalse(decision.requiresConfirmation)
    }

    @Test
    fun `mismatched confirmation stays blocked until the user restates the request`() {
        val brainAction = groceryBrainAction()
        val mismatchedConfirmation = pendingConfirmation(
            brainAction = brainAction.copy(reply = "Add rice to the cart.")
        )

        val decision = safetyGate.evaluate(
            action = brainAction,
            pendingConfirmation = mismatchedConfirmation,
            userConfirmed = true
        )

        assertFalse(decision.allowed)
        assertTrue(decision.requiresConfirmation)
        assertTrue(
            decision.message.contains("no longer matches", ignoreCase = true)
        )
    }

    private fun groceryBrainAction(): BrainAction {
        return BrainAction(
            intent = "grocery_booking",
            reply = "Add milk to the cart.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to "buy milk",
                "selectedItem" to "milk"
            ),
            nextQuestion = "Confirm adding milk to the cart?"
        )
    }

    private fun pendingConfirmation(brainAction: BrainAction): PendingConfirmation {
        return PendingConfirmation(
            confirmationId = "confirmation-grocery",
            type = PendingConfirmationType.PLACE_ORDER,
            sessionId = "session-grocery",
            sessionType = BrainSessionType.GROCERY,
            createdAtMillis = 1_000L,
            expiresAtMillis = System.currentTimeMillis() + 60_000L,
            userFacingSummary = "Add milk to the cart",
            actionSummary = brainAction.reply,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            brainAction = brainAction,
            sanitizedMetadata = mapOf("rawText" to "buy milk"),
            confirmationPhraseExpected = "yes",
            denialPhraseExpected = "no"
        )
    }
}
