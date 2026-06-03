package com.nova.luna.data

import com.nova.luna.model.CommandResult
import java.util.Locale

fun buildCommandHistoryEntity(
    rawText: String,
    normalizedText: String,
    result: CommandResult,
    timestamp: Long = System.currentTimeMillis()
): CommandHistoryEntity {
    return CommandHistoryEntity(
        rawText = rawText,
        normalizedText = normalizedText.trim().lowercase(Locale.US),
        intentType = result.intentType.name,
        actionType = result.actionType.name,
        safetyLevel = result.safetyDecision.level.name,
        safetyMessage = result.safetyDecision.message,
        resultMessage = result.message,
        success = result.success,
        shouldStopListening = result.shouldStopListening,
        timestamp = timestamp
    )
}
