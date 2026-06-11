package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel

class BrainActionParser(
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) {
    fun parse(
        rawCommand: String,
        normalizedCommand: String,
        modelOutput: String,
        source: BrainActionSource = BrainActionSource.MODEL
    ): BrainAction {
        val json = extractJson(modelOutput)
        
        if (json == null) {
            return errorAction(rawCommand, normalizedCommand, "Failed to extract JSON from model output", modelOutput)
        }

        val decoded = codec.decode(json)
        if (decoded == null) {
            return errorAction(rawCommand, normalizedCommand, "Failed to decode JSON", json)
        }

        // Repair and normalize using the helper
        return decoded.withPhase23Metadata(
            schemaVersion = 1,
            source = if (json != modelOutput) BrainActionSource.MODEL_WITH_RULE_REPAIR else source,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            confidence = decoded.confidence.coerceIn(0.0, 1.0)
        )
    }

    private fun extractJson(output: String): String? {
        val firstOpen = output.indexOf('{')
        val lastClose = output.lastIndexOf('}')
        if (firstOpen == -1 || lastClose == -1 || lastClose < firstOpen) {
            return null
        }
        return output.substring(firstOpen, lastClose + 1)
    }

    private fun errorAction(
        rawCommand: String,
        normalizedCommand: String,
        reason: String,
        output: String
    ): BrainAction {
        return BrainAction(
            intent = "error",
            reply = "I encountered an error understanding your request.",
            actionType = BrainActionType.UNKNOWN,
            riskLevel = BrainRiskLevel.UNKNOWN,
            requiresConfirmation = false,
            params = emptyMap(),
            nextQuestion = reason
        ).withPhase23Metadata(
            schemaVersion = 1,
            source = BrainActionSource.ERROR,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            confidence = 0.0,
            assistantReply = "I encountered an error understanding your request.",
            reason = reason,
            errors = listOf("Parser error: $reason", "Raw output sample: ${output.take(100)}")
        )
    }
}
