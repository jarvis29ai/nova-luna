package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertFalse
import org.junit.Test

class BrainActionValidatorTest {
    private val validator = BrainActionValidator()

    @Test
    fun `payment otp login and final booking cannot be marked executable`() {
        val cases = listOf(
            BrainAction(
                intent = "open_app",
                reply = "Opening an app to send money.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = mapOf("rawText" to "send money to mom")
            ),
            BrainAction(
                intent = "type_text",
                reply = "Typing OTP.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = mapOf("rawText" to "enter otp", "text" to "123456")
            ),
            BrainAction(
                intent = "open_app",
                reply = "Logging in now.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = mapOf("rawText" to "login to gmail")
            ),
            BrainAction(
                intent = "cab_booking",
                reply = "Booking now.",
                actionType = BrainActionType.PREPARE,
                riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
                requiresConfirmation = true,
                finalActionAllowed = true,
                params = mapOf("rawText" to "book cheapest auto to DB Mall")
            ),
            BrainAction(
                intent = "open_app",
                reply = "Book it without asking me.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = mapOf("rawText" to "book it without asking me")
            ),
            BrainAction(
                intent = "grocery_booking",
                reply = "Preparing the grocery flow.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = mapOf("rawText" to "buy milk and bread")
            ),
            BrainAction(
                intent = "shopping_booking",
                reply = "Preparing the shopping flow.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = mapOf("rawText" to "buy a phone")
            )
        )

        cases.forEach { action ->
            assertFalse(validator.isAcceptable(action))
        }
    }
}
