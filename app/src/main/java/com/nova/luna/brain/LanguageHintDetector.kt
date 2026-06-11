package com.nova.luna.brain

import com.nova.luna.util.AssistantTextNormalizer

class LanguageHintDetector {
    fun isMultilingualRequest(request: BrainRequest, normalizedText: String): Boolean {
        val preferredLanguage = request.preferences?.preferredLanguage
            ?.lowercase()
            ?.trim()
            .orEmpty()

        if (preferredLanguage in multilingualLanguageHints) {
            return true
        }

        if (containsNonLatinScript(request.rawText)) {
            return true
        }

        return multilingualSignals.any { signal ->
            containsPhrase(normalizedText, signal)
        }
    }

    private fun containsNonLatinScript(rawText: String): Boolean {
        return rawText.any { character -> character.code > 127 }
    }

    private fun containsPhrase(normalizedText: String, phrase: String): Boolean {
        val target = AssistantTextNormalizer.normalize(phrase)
        if (target.isBlank()) return false
        val regex = Regex("\\b${Regex.escape(target)}\\b")
        return regex.containsMatchIn(normalizedText)
    }

    companion object {
        private val multilingualLanguageHints = setOf(
            "hi",
            "hindi",
            "hinglish",
            "multilingual",
            "regional"
        )

        private val multilingualSignals = listOf(
            "translate",
            "transliterate",
            "samjhao",
            "batao",
            "karo",
            "kholo",
            "chalao",
            "band",
            "bhejo",
            "mujhe",
            "mera",
            "kya",
            "kyon",
            "kaise",
            "kab",
            "nahi",
            "nahin",
            "answer in hindi",
            "answer in hinglish",
            "help me understand",
            "explain this in hindi",
            "explain this in hinglish",
            "camera kholo",
            "setting dikhao"
        )
    }
}
