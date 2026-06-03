package com.nova.luna.data

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryRecordFactoryTest {
    @Test
    fun `builds a success history record from command result`() {
        val result = CommandResult.success(
            message = "Going home.",
            intentType = IntentType.NAVIGATION,
            actionType = ActionType.GO_HOME
        )

        val record = buildCommandHistoryEntity(
            rawText = "Go Home",
            normalizedText = "GO HOME",
            result = result,
            timestamp = 123456789L
        )

        assertEquals("Go Home", record.rawText)
        assertEquals("go home", record.normalizedText)
        assertEquals(IntentType.NAVIGATION.name, record.intentType)
        assertEquals(ActionType.GO_HOME.name, record.actionType)
        assertEquals(SafetyLevel.SAFE.name, record.safetyLevel)
        assertEquals("Allowed", record.safetyMessage)
        assertEquals("Going home.", record.resultMessage)
        assertTrue(record.success)
        assertFalse(record.shouldStopListening)
        assertEquals(123456789L, record.timestamp)
    }

    @Test
    fun `builds a failure history record from blocked command result`() {
        val result = CommandResult(
            success = false,
            message = "Blocked command: payments, banking, checkout, passwords, OTPs, and CAPTCHA work must stay manual.",
            intentType = IntentType.BLOCKED,
            actionType = ActionType.BLOCKED,
            safetyDecision = SafetyDecision.block("Blocked command."),
            shouldStopListening = false
        )

        val record = buildCommandHistoryEntity(
            rawText = "pay the bill",
            normalizedText = "pay the bill",
            result = result,
            timestamp = 987654321L
        )

        assertEquals("pay the bill", record.rawText)
        assertEquals("pay the bill", record.normalizedText)
        assertEquals(IntentType.BLOCKED.name, record.intentType)
        assertEquals(ActionType.BLOCKED.name, record.actionType)
        assertEquals(SafetyLevel.BLOCKED.name, record.safetyLevel)
        assertEquals("Blocked command.", record.safetyMessage)
        assertEquals(result.message, record.resultMessage)
        assertFalse(record.success)
        assertFalse(record.shouldStopListening)
        assertEquals(987654321L, record.timestamp)
    }
}
