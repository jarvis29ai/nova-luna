package com.nova.luna.history

import com.nova.luna.data.CommandHistoryEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CommandHistoryFormatter {
    private val timestampFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    private const val EMPTY_STATE = "No command history yet."

    fun format(entries: List<CommandHistoryEntity>): String {
        if (entries.isEmpty()) {
            return EMPTY_STATE
        }

        return entries.mapIndexed { index, entry ->
            formatEntry(index + 1, entry)
        }.joinToString(separator = "\n\n")
    }

    private fun formatEntry(index: Int, entry: CommandHistoryEntity): String {
        return buildString {
            appendLine("Command #$index")
            appendLine("Time: ${timestampFormatter.format(Instant.ofEpochMilli(entry.timestamp))}")
            appendLine("Raw: ${entry.rawText}")
            appendLine("Normalized: ${entry.normalizedText}")
            appendLine("Intent: ${entry.intentType}")
            appendLine("Action: ${entry.actionType}")
            appendLine("Safety level: ${entry.safetyLevel}")
            appendLine("Safety message: ${entry.safetyMessage}")
            appendLine("Result: ${entry.resultMessage}")
            appendLine("Success: ${if (entry.success) "true" else "false"}")
            appendLine("Stop listening: ${if (entry.shouldStopListening) "true" else "false"}")
        }.trimEnd()
    }
}
