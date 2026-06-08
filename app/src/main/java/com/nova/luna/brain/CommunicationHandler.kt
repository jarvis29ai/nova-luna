package com.nova.luna.brain

import com.nova.luna.communication.CommunicationIntentParser
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class CommunicationHandler(
    private val parser: CommunicationIntentParser = CommunicationIntentParser()
) : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.COMMUNICATION
    override val modelName: String = "CommunicationParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val commKeywords = listOf(
            "send message", "read email", "summarize", "whatsapp", "gmail", "telegram", "sms", "reply", "draft"
        )

        for (keyword in commKeywords) {
            if (command.contains(keyword)) {
                signals.add(keyword)
            }
        }

        val request = parser.parse(command)
        val hasParserResult = request.commandType != com.nova.luna.communication.CommunicationCommandType.UNKNOWN
        val isGenericStop = command.trim().lowercase() in setOf("stop", "cancel", "quiet", "stop service")

        val confidence = when {
            signals.isNotEmpty() && !isGenericStop -> 0.90f
            hasParserResult && !isGenericStop -> 0.85f
            isGenericStop -> 0.10f // Let SystemHandler or others catch it
            else -> 0.0f
        }
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = "Communication keywords or parser match found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        return CommandIntent(
            rawText = command,
            normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
            intentType = IntentType.COMMUNICATION,
            actionType = ActionType.COMMUNICATION
        )
    }
}
