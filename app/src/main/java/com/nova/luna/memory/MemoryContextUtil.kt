package com.nova.luna.memory

object MemoryContextUtil {
    fun getPreference(context: String?, key: String): String? {
        if (context.isNullOrBlank()) return null
        return context.split(";")
            .map { it.trim() }
            .find { it.startsWith("$key=") }
            ?.substringAfter("=")
    }

    fun getAllPreferences(context: String?): Map<String, String> {
        if (context.isNullOrBlank()) return emptyMap()
        return context.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate { 
                val parts = it.split("=")
                parts[0] to parts[1]
            }
    }

    fun resolveLabels(text: String, context: String?): String {
        if (context.isNullOrBlank()) return text
        var result = text
        val prefs = getAllPreferences(context)
        
        // Resolve common labels
        val labels = listOf("home", "work")
        for (label in labels) {
            val value = prefs[label]
            if (value != null) {
                result = result.replace("\\b$label\\b".toRegex(RegexOption.IGNORE_CASE), value)
            }
        }
        return result
    }
}
