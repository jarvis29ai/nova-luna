package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainActionValidatorLocalLlmTest {
    private val validator = BrainActionValidator()

    @Test
    fun `dangerous final actions are rejected for local llm candidates`() {
        val action = BrainAction(
            intent = "send_message",
            reply = "Send it now.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf("rawText" to "send it now")
        )

        assertFalse(validator.isAcceptable(action))
    }

    @Test
    fun `safe read only local llm candidates are accepted`() {
        val action = BrainAction(
            intent = "explain",
            reply = "Here is a safe explanation.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "please explain this")
        )

        assertTrue(validator.isAcceptable(action))
    }
}
