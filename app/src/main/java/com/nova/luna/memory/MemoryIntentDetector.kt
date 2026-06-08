package com.nova.luna.memory

import com.nova.luna.util.AssistantTextNormalizer

class MemoryIntentDetector {
    fun detect(text: String): MemoryDecision? {
        val n = AssistantTextNormalizer.normalize(text).lowercase()
        
        return when {
            isClearAll(n) -> MemoryDecision(MemoryAction.CLEAR_ALL, MemoryType.UNKNOWN, "", "", needsConfirmation = true, userMessage = "This will delete all saved memory. Continue?")
            isView(n) -> detectView(n)
            isDelete(n) -> detectDelete(n)
            isSave(n) -> detectSave(n, text)
            else -> null
        }
    }

    private fun isClearAll(n: String) = n.contains("clear all memory") || n.contains("forget everything") || n.contains("delete all memory")

    private fun isView(n: String) = n.contains("what do you remember") || n.contains("show my saved preferences") || n.contains("what are my preferences") || n.contains("what are my") || n.contains("show my")

    private fun isDelete(n: String) = n.startsWith("forget ") || n.startsWith("delete ") || n.contains("remove my") || n.contains("forget my")

    private fun isSave(n: String) = n.startsWith("remember ") || n.startsWith("save ") || n.startsWith("use ") || n.contains(" my ") && n.contains(" is ") || n.contains(" prefer ")

    private fun detectView(n: String): MemoryDecision {
        val type = when {
            n.contains("cab") -> MemoryType.CAB_PREFERENCE
            n.contains("food") -> MemoryType.FOOD_PREFERENCE
            n.contains("music") -> MemoryType.MUSIC_PREFERENCE
            n.contains("shopping") -> MemoryType.SHOPPING_PREFERENCE
            n.contains("grocery") -> MemoryType.GROCERY_PREFERENCE
            else -> MemoryType.UNKNOWN
        }
        return MemoryDecision(MemoryAction.VIEW, type, "", "", confidence = 0.9f)
    }

    private fun detectDelete(n: String): MemoryDecision {
        val type = when {
            n.contains("home") -> MemoryType.HOME_LABEL
            n.contains("work") -> MemoryType.WORK_LABEL
            n.contains("food") -> MemoryType.FOOD_PREFERENCE
            n.contains("cab") -> MemoryType.CAB_PREFERENCE
            n.contains("music") -> MemoryType.MUSIC_PREFERENCE
            n.contains("shopping") -> MemoryType.SHOPPING_PREFERENCE
            n.contains("grocery") -> MemoryType.GROCERY_PREFERENCE
            n.contains("budget") -> MemoryType.BUDGET_PREFERENCE
            else -> MemoryType.UNKNOWN
        }
        val key = when (type) {
            MemoryType.HOME_LABEL -> "home"
            MemoryType.WORK_LABEL -> "work"
            MemoryType.MUSIC_PREFERENCE -> "preferred_music_app"
            MemoryType.CAB_PREFERENCE -> "cab"
            MemoryType.FOOD_PREFERENCE -> "food"
            MemoryType.GROCERY_PREFERENCE -> "grocery"
            MemoryType.SHOPPING_PREFERENCE -> "shopping"
            MemoryType.BUDGET_PREFERENCE -> "budget"
            else -> ""
        }
        return MemoryDecision(MemoryAction.DELETE, type, key, "", confidence = 0.8f)
    }

    private fun detectSave(n: String, original: String): MemoryDecision? {
        // "Remember I prefer YouTube Music"
        // "Save home as railway colony"
        // "My food budget is 300"
        
        val decision = when {
            n.contains("prefer") && n.contains("music") -> {
                val value = extractValueAfter(original, listOf("prefer", "use"))
                MemoryDecision(MemoryAction.SAVE, MemoryType.MUSIC_PREFERENCE, "preferred_music_app", value)
            }
            n.contains("music app") -> {
                val value = extractValueAfter(original, listOf("is", "use", "be"))
                MemoryDecision(MemoryAction.SAVE, MemoryType.PREFERRED_APP, "music", value)
            }
            n.contains("cab app") -> {
                val value = extractValueAfter(original, listOf("is", "use", "be"))
                MemoryDecision(MemoryAction.SAVE, MemoryType.PREFERRED_APP, "cab", value)
            }
            n.contains("food app") -> {
                val value = extractValueAfter(original, listOf("is", "use", "be"))
                MemoryDecision(MemoryAction.SAVE, MemoryType.PREFERRED_APP, "food", value)
            }
            n.contains("home") && (n.contains("as") || n.contains("is")) -> {
                val value = extractValueAfter(original, listOf("as", "is"))
                MemoryDecision(MemoryAction.SAVE, MemoryType.HOME_LABEL, "home", value, sensitivity = MemorySensitivity.HIGH, needsConfirmation = true)
            }
            n.contains("work") && (n.contains("as") || n.contains("is")) -> {
                val value = extractValueAfter(original, listOf("as", "is"))
                MemoryDecision(MemoryAction.SAVE, MemoryType.WORK_LABEL, "work", value, sensitivity = MemorySensitivity.HIGH, needsConfirmation = true)
            }
            n.contains("budget") -> {
                val type = when {
                    n.contains("food") -> MemoryType.FOOD_PREFERENCE
                    n.contains("grocery") -> MemoryType.GROCERY_PREFERENCE
                    n.contains("shopping") -> MemoryType.SHOPPING_PREFERENCE
                    else -> MemoryType.BUDGET_PREFERENCE
                }
                val value = extractValueAfter(original, listOf("is", "be", "to"))
                MemoryDecision(MemoryAction.SAVE, type, "budget", value)
            }
            n.contains("hindi") || n.contains("english") || n.contains("hinglish") -> {
                MemoryDecision(MemoryAction.SAVE, MemoryType.LANGUAGE_PREFERENCE, "language", original)
            }
            else -> null
        }

        if (decision != null) return decision

        // Generic fallback: "Remember my [key] is [value]"
        if (n.contains(" my ") && n.contains(" is ")) {
            val key = n.substringAfter(" my ").substringBefore(" is ").trim()
            val value = original.substringAfter(" is ").trim().removeSuffix(".")
            return MemoryDecision(MemoryAction.SAVE, MemoryType.USER_NOTE, key, value)
        }

        return null
    }

    private fun extractValueAfter(text: String, keywords: List<String>): String {
        val lower = text.lowercase()
        var bestIndex = -1
        var bestKeyword = ""
        
        for (kw in keywords) {
            val idx = lower.lastIndexOf(" $kw ")
            if (idx > bestIndex) {
                bestIndex = idx
                bestKeyword = kw
            }
        }
        
        if (bestIndex == -1) {
            for (kw in keywords) {
                val idx = lower.lastIndexOf(" $kw")
                if (idx != -1 && idx + kw.length + 1 >= lower.length) continue // ignore if it's at the end
                if (idx > bestIndex) {
                    bestIndex = idx
                    bestKeyword = kw
                }
            }
        }

        if (bestIndex != -1) {
            return text.substring(bestIndex + bestKeyword.length + 1).trim().removeSuffix(".")
        }
        return text // fallback
    }
}
