package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLocalLlmOutputParserTest {
    private val codec = BrainActionJsonCodec()
    private val parser = PhoneLocalLlmOutputParser(codec, BrainActionValidator())

    @Test
    fun `parses strict json and fenced json output`() {
        val action = BrainAction(
            intent = "explain",
            reply = "Here is a safe explanation.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "please explain this")
        )

        val parseResult = parser.parse("```json\n${codec.encode(action)}\n```")

        assertTrue(parseResult.accepted)
        assertEquals(action, parseResult.candidateAction)
        assertEquals(PhoneLocalLlmStatus.READY, parseResult.status)
    }

    @Test
    fun `rejects dangerous local llm candidates before execution`() {
        val action = BrainAction(
            intent = "cab_booking",
            reply = "Ready to book and pay now.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = true,
            params = mapOf("rawText" to "please help me book a cab"),
            nextQuestion = "Confirm booking now?"
        )

        val parseResult = parser.parse(codec.encode(action))

        assertFalse(parseResult.accepted)
        assertEquals(PhoneLocalLlmStatus.VALIDATION_REJECTED, parseResult.status)
        assertEquals(action, parseResult.candidateAction)
        assertTrue(parseResult.reason.contains("rejected", ignoreCase = true))
    }
}
