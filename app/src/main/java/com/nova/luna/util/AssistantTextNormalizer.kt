package com.nova.luna.util

import java.util.Locale

object AssistantTextNormalizer {
    private val wakeWordPattern = Regex(
        """^\s*(?:(?:hey\s+)?(?:luna|nova))\b[\s,:-]*""",
        RegexOption.IGNORE_CASE
    )

    fun stripWakeWords(rawText: String): String {
        var text = rawText.trim()
        if (text.isBlank()) return text

        repeat(2) {
            val stripped = wakeWordPattern.replaceFirst(text, "")
            if (stripped == text) {
                return text
            }
            text = stripped.trimStart(',', ':', '-', ' ')
        }

        return text
    }

    fun normalize(rawText: String): String {
        return stripWakeWords(rawText)
            .lowercase(Locale.US)
            .replace(Regex("""[^\p{L}\p{N}\p{M}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
