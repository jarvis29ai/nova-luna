package com.nova.luna.brain

import com.nova.luna.cab.CabIntentParser
import com.nova.luna.cab.toEntities
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class CabHandler(
    private val parser: CabIntentParser = CabIntentParser()
) : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.CAB
    override val modelName: String = "CabParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val cabKeywords = listOf(
            "book cab", "ride", "taxi", "pickup", "destination", "ola", "uber", "fare", "cab"
        )

        for (keyword in cabKeywords) {
            if (command.contains(keyword)) {
                signals.add(keyword)
            }
        }

        val request = parser.parse(command)
        val hasParserResult = request != null

        val confidence = if (signals.isNotEmpty()) 0.90f else if (hasParserResult) 0.80f else 0.0f
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = "Cab keywords or parser match found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        val resolvedCommand = com.nova.luna.memory.MemoryContextUtil.resolveLabels(command, context?.memoryContext)
        val request = parser.parse(resolvedCommand)
        val entities = request?.toEntities()?.toMutableMap() ?: mutableMapOf()
        
        // Use preferred cab app if not mentioned
        if (entities["provider"] == null) {
            com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "cab")?.let {
                entities["providerPreference"] = it
            }
        }

        return if (request != null) {
            CommandIntent(
                rawText = command,
                normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
                intentType = IntentType.CAB_BOOKING,
                actionType = ActionType.CAB_BOOKING,
                entities = entities
            )
        } else {
            CommandIntent(
                rawText = command,
                normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command)
            )
        }
    }
}
