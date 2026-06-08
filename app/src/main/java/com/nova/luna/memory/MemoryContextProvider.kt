package com.nova.luna.memory

class MemoryContextProvider(
    private val store: PersonalMemoryStore
) {
    fun getContextForCommand(command: String, domain: String? = null): String {
        val relevantItems = mutableListOf<PersonalMemoryItem>()
        
        // Always include basic preferences
        relevantItems.addAll(store.list(type = MemoryType.LANGUAGE_PREFERENCE))
        relevantItems.addAll(store.list(type = MemoryType.VOICE_STYLE))
        
        // Include domain-specific preferences
        if (domain != null) {
            relevantItems.addAll(getRelevantItemsForDomain(domain))
        } else {
            // If domain is unknown, try to infer from command
            val inferredDomain = inferDomain(command)
            if (inferredDomain != null) {
                relevantItems.addAll(getRelevantItemsForDomain(inferredDomain))
            }
        }

        // Include preferred apps relevant to the task
        relevantItems.addAll(store.list(type = MemoryType.PREFERRED_APP))

        return relevantItems
            .distinctBy { it.id }
            .filter { it.isEnabled && it.userConfirmed }
            .filter { it.sensitivity != MemorySensitivity.SENSITIVE_BLOCKED }
            .joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun getRelevantItemsForDomain(domain: String): List<PersonalMemoryItem> {
        val d = domain.uppercase()
        return when (d) {
            "MUSIC", "MEDIA" -> store.list(type = MemoryType.MUSIC_PREFERENCE)
            "CAB" -> store.list(type = MemoryType.CAB_PREFERENCE) + store.list(type = MemoryType.HOME_LABEL) + store.list(type = MemoryType.WORK_LABEL)
            "FOOD" -> store.list(type = MemoryType.FOOD_PREFERENCE) + store.list(type = MemoryType.FOOD_RESTRICTION) + store.list(type = MemoryType.BUDGET_PREFERENCE)
            "GROCERY" -> store.list(type = MemoryType.GROCERY_PREFERENCE) + store.list(type = MemoryType.BUDGET_PREFERENCE)
            "SHOPPING" -> store.list(type = MemoryType.SHOPPING_PREFERENCE) + store.list(type = MemoryType.BUDGET_PREFERENCE)
            "COMMUNICATION" -> store.list(type = MemoryType.COMMUNICATION_PREFERENCE)
            "CONTENT" -> store.list(type = MemoryType.CONTENT_CREATION_PREFERENCE)
            else -> emptyList()
        }
    }

    private fun inferDomain(command: String): String? {
        val c = command.lowercase()
        return when {
            c.contains("play") || c.contains("song") || c.contains("music") -> "MUSIC"
            c.contains("cab") || c.contains("uber") || c.contains("ola") || c.contains("ride") -> "CAB"
            c.contains("food") || c.contains("order") || c.contains("zomato") || c.contains("swiggy") -> "FOOD"
            c.contains("grocery") || c.contains("blinkit") || c.contains("zepto") -> "GROCERY"
            c.contains("buy") || c.contains("shopping") || c.contains("amazon") || c.contains("flipkart") -> "SHOPPING"
            else -> null
        }
    }

    fun getSafePromptSummary(): String {
        return store.exportSafeSummary()
    }
}
