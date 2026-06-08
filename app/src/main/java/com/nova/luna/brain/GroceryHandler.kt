package com.nova.luna.brain

import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class GroceryHandler(
    private val parser: GroceryIntentParser = GroceryIntentParser()
) : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.GROCERY
    override val modelName: String = "GroceryParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val groceryKeywords = listOf(
            "grocery", "vegetables", "milk", "bread", "rice", "atta", "compare grocery", "blinkit", "zepto", "bigbasket", "jiomart"
        )

        for (keyword in groceryKeywords) {
            if (command.contains(keyword)) {
                signals.add(keyword)
            }
        }

        val request = parser.parseInitialGroceryRequest(command)
        val hasParserResult = request != null

        val confidence = if (signals.isNotEmpty()) 0.90f else if (hasParserResult) 0.80f else 0.0f
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = "Grocery keywords or parser match found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        val request = parser.parseInitialGroceryRequest(command)
        val entities = mutableMapOf<String, String>()
        
        if (request != null) {
            entities["item"] = request.basket.items.joinToString { it.name }
            entities["quantity"] = request.basket.items.joinToString { it.quantityText ?: "" }
        }

        // Use preferred grocery app if not mentioned
        com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "grocery")?.let {
            entities["providerPreference"] = it
        }

        // Use budget preference if not in command
        com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "budget")?.let {
            entities["budgetPreference"] = it
        }

        return if (request != null) {
            CommandIntent(
                rawText = command,
                normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = entities
            )
        } else {
            CommandIntent(
                rawText = command,
                normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
                entities = entities
            )
        }
    }
}
