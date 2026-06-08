package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.shopping.ShoppingIntentParser
import com.nova.luna.shopping.ShoppingProductCategory

class ShoppingHandler(
    private val parser: ShoppingIntentParser = ShoppingIntentParser()
) : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.SHOPPING
    override val modelName: String = "ShoppingParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        
        // Exclude grocery and food keywords first
        val groceryKeywords = listOf("milk", "vegetables", "fruits", "groceries")
        if (groceryKeywords.any { command.contains(it) }) return DomainMatch(domain, 0.0f)

        val foodKeywords = listOf("order food", "pizza", "burger", "biryani", "restaurant")
        if (foodKeywords.any { command.contains(it) }) return DomainMatch(domain, 0.0f)

        val shoppingKeywords = listOf(
            "buy", "purchase", "compare price", "best deal", "earbuds", "phone", "laptop", "amazon", "flipkart"
        )

        for (keyword in shoppingKeywords) {
            if (command.contains(keyword)) {
                signals.add(keyword)
            }
        }

        val request = parser.parse(command)
        val hasProductEvidence = request.category != com.nova.luna.shopping.ShoppingProductCategory.UNKNOWN || request.productName != null || request.brand != null
        val hasShoppingContext = request.buyIntent || request.comparisonIntent || request.budget != null || request.website != null

        // Explicit check for shopping keywords to avoid being too greedy
        val isGenericComparison = request.comparisonIntent && !hasProductEvidence && signals.isEmpty()
        val isOnlineCandidate = OnlineAiPolicy().isPotentialCandidate(BrainRequest(command))
        
        val hasStrongShoppingSignal = signals.isNotEmpty()

        val hasStrongSignal = hasStrongShoppingSignal && (hasProductEvidence || hasShoppingContext)
        val hasMediumSignal = hasProductEvidence && hasShoppingContext && !isGenericComparison

        val confidence = when {
            hasStrongSignal -> 0.92f
            hasMediumSignal && hasStrongShoppingSignal -> 0.88f
            isGenericComparison || isOnlineCandidate -> 0.20f // Force fall-through for flexible reasoning
            hasProductEvidence || hasShoppingContext -> 0.40f // Too weak for auto-routing
            else -> 0.0f
        }
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = "Shopping keywords or intent evidence found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        val entities = mutableMapOf<String, String>()
        
        // Use preferred shopping app if not mentioned
        com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "shopping")?.let {
            entities["preferredApp"] = it
        }

        // Use budget preference if not in command
        com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "budget")?.let {
            entities["budgetPreference"] = it
        }

        return CommandIntent(
            rawText = command,
            normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
            intentType = IntentType.SHOPPING,
            actionType = ActionType.SHOPPING,
            entities = entities
        )
    }
}
