package com.nova.luna.brain

import com.nova.luna.food.FoodIntentParser
import com.nova.luna.food.toEntities
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class FoodHandler(
    private val parser: FoodIntentParser = FoodIntentParser()
) : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.FOOD
    override val modelName: String = "FoodParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val foodKeywords = listOf(
            "order food", "pizza", "burger", "biryani", "restaurant", "zomato", "swiggy", "meal", "cart", "checkout", "food"
        )

        for (keyword in foodKeywords) {
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
            reason = "Food keywords or parser match found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        val request = parser.parse(command)
        val entities = request?.toEntities()?.toMutableMap() ?: mutableMapOf()

        // Use preferred food app if not mentioned
        if (entities["provider"] == null) {
            com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "food")?.let {
                entities["providerPreference"] = it
            }
        }

        // Use budget preference if not in command
        if (entities["budget"] == null) {
            com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "budget")?.let {
                entities["budgetPreference"] = it
            }
        }

        return if (request != null) {
            CommandIntent(
                rawText = command,
                normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
                intentType = IntentType.FOOD_ORDER,
                actionType = ActionType.FOOD_ORDER,
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
