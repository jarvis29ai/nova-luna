package com.nova.luna.history

import com.nova.luna.data.buildCommandHistoryEntity
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryFormatterTest {
    @Test
    fun `formats safe and blocked command history fields for display`() {
        val safeEntry = buildCommandHistoryEntity(
            rawText = "go home",
            normalizedText = "go home",
            result = CommandResult.success(
                message = "Going home.",
                intentType = IntentType.NAVIGATION,
                actionType = ActionType.GO_HOME
            ),
            timestamp = 123456789L
        )
        val blockedEntry = buildCommandHistoryEntity(
            rawText = "pay 100 rupees",
            normalizedText = "pay 100 rupees",
            result = CommandResult.blocked(
                message = "Blocked command: payments stay manual.",
                intentType = IntentType.BLOCKED,
                actionType = ActionType.BLOCKED
            ),
            timestamp = 987654321L
        )

        val formatted = CommandHistoryFormatter.format(listOf(safeEntry, blockedEntry))

        assertTrue(formatted.contains("Command #1"))
        assertTrue(formatted.contains("Time: 1970-01-02T10:17:36.789Z"))
        assertTrue(formatted.contains("Raw: go home"))
        assertTrue(formatted.contains("Normalized: go home"))
        assertTrue(formatted.contains("Intent: NAVIGATION"))
        assertTrue(formatted.contains("Action: GO_HOME"))
        assertTrue(formatted.contains("Safety level: SAFE"))
        assertTrue(formatted.contains("Safety message: Allowed"))
        assertTrue(formatted.contains("Result: Going home."))
        assertTrue(formatted.contains("Success: true"))
        assertTrue(formatted.contains("Stop listening: false"))

        assertTrue(formatted.contains("Command #2"))
        assertTrue(formatted.contains("Time: 1970-01-12T10:20:54.321Z"))
        assertTrue(formatted.contains("Raw: pay 100 rupees"))
        assertTrue(formatted.contains("Intent: BLOCKED"))
        assertTrue(formatted.contains("Action: BLOCKED"))
        assertTrue(formatted.contains("Safety level: BLOCKED"))
        assertTrue(formatted.contains("Safety message: Blocked command: payments stay manual."))
        assertTrue(formatted.contains("Result: Blocked command: payments stay manual."))
        assertTrue(formatted.contains("Success: false"))
    }

    @Test
    fun `formats empty history with a local placeholder`() {
        assertEquals("No command history yet.", CommandHistoryFormatter.format(emptyList()))
    }
}
