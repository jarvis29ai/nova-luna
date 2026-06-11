package com.nova.luna.brain

import com.nova.luna.util.AssistantTextNormalizer

data class RequestComplexitySignal(
    val simpleCommand: Boolean,
    val complexRequest: Boolean,
    val liteFallbackCandidate: Boolean
)

class RequestComplexityClassifier {
    fun classify(normalizedText: String): RequestComplexitySignal {
        val simpleCommand = isSimpleCommand(normalizedText)
        val complexRequest = !simpleCommand && isComplexRequest(normalizedText)
        val liteFallbackCandidate = !simpleCommand && isLiteFallbackCandidate(normalizedText)

        return RequestComplexitySignal(
            simpleCommand = simpleCommand,
            complexRequest = complexRequest,
            liteFallbackCandidate = liteFallbackCandidate
        )
    }

    fun isSimpleCommand(normalizedText: String): Boolean {
        return isStopCommand(normalizedText) ||
            isGoHomeCommand(normalizedText) ||
            isOpenAppCommand(normalizedText) ||
            isNavigationCommand(normalizedText) ||
            isQuickInteractionCommand(normalizedText) ||
            isPhoneCommand(normalizedText) ||
            isScreenshotCommand(normalizedText) ||
            isSettingsCommand(normalizedText)
    }

    fun isComplexRequest(normalizedText: String): Boolean {
        val wordCount = normalizedText.split(Regex("\\s+")).count { it.isNotBlank() }
        if (wordCount == 0) return false

        val phrases = listOf(
            "help me",
            "please help",
            "can you help",
            "could you help",
            "would you help",
            "please explain",
            "explain this",
            "summarize this",
            "rewrite this",
            "draft this",
            "compare these",
            "compare this",
            "what should i",
            "what do you think",
            "how should i",
            "how can i",
            "make this",
            "improve this",
            "check this",
            "please batao",
            "please samjhao",
            "thoda help"
        )

        if (phrases.any { containsPhrase(normalizedText, it) }) {
            return true
        }

        val signalWords = listOf(
            "help",
            "explain",
            "translate",
            "rewrite",
            "summarize",
            "draft",
            "compare",
            "plan",
            "tell me",
            "make",
            "improve",
            "check",
            "how",
            "what",
            "why"
        )

        if (normalizedText.contains("?") && wordCount >= 3) {
            return true
        }

        return wordCount >= 3 && containsAny(normalizedText, signalWords)
    }

    fun isLiteFallbackCandidate(normalizedText: String): Boolean {
        val words = normalizedText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        if (words.size > 6) return false

        val fallbackSignals = listOf(
            "simple",
            "basic",
            "quick",
            "short",
            "light",
            "lite",
            "fallback",
            "small",
            "brief"
        )

        return fallbackSignals.any { containsPhrase(normalizedText, it) } ||
            containsAny(
                normalizedText,
                listOf("help", "explain", "translate", "rewrite", "summarize", "plan", "draft")
            ) ||
            normalizedText.contains("?")
    }

    private fun isStopCommand(normalizedText: String): Boolean {
        return normalizedText == "stop listening" ||
            normalizedText == "cancel listening" ||
            normalizedText == "stop speaking" ||
            normalizedText == "cancel speaking" ||
            normalizedText == "cancel voice" ||
            normalizedText == "stop voice" ||
            normalizedText == "stop service" ||
            normalizedText == "cancel service" ||
            normalizedText == "stop assistant" ||
            normalizedText == "cancel assistant" ||
            normalizedText == "quiet" ||
            normalizedText == "be quiet"
    }

    private fun isGoHomeCommand(normalizedText: String): Boolean {
        return normalizedText == "go home" ||
            normalizedText == "home" ||
            normalizedText == "back to home"
    }

    private fun isOpenAppCommand(normalizedText: String): Boolean {
        if (isMessagePlanning(normalizedText)) {
            return false
        }

        return normalizedText.startsWith("open app ") ||
            normalizedText.startsWith("open ") ||
            normalizedText.startsWith("launch ") ||
            normalizedText.startsWith("start ")
    }

    private fun isNavigationCommand(normalizedText: String): Boolean {
        return normalizedText == "go back" ||
            normalizedText == "go previous" ||
            normalizedText == "back" ||
            normalizedText == "previous" ||
            normalizedText == "show recent apps" ||
            normalizedText == "show recents" ||
            normalizedText == "open recent apps" ||
            normalizedText == "open recents" ||
            normalizedText == "recent apps" ||
            normalizedText == "recents" ||
            normalizedText == "open notifications" ||
            normalizedText == "show notifications" ||
            normalizedText == "scroll down" ||
            normalizedText == "move down" ||
            normalizedText == "scroll up" ||
            normalizedText == "move up" ||
            normalizedText == "swipe down" ||
            normalizedText == "swipe up"
    }

    private fun isQuickInteractionCommand(normalizedText: String): Boolean {
        return normalizedText.startsWith("tap ") ||
            normalizedText.startsWith("tap on ") ||
            normalizedText.startsWith("click ") ||
            normalizedText.startsWith("click on ") ||
            normalizedText.startsWith("press ") ||
            normalizedText.startsWith("press on ") ||
            normalizedText.startsWith("type ") ||
            normalizedText.startsWith("write ") ||
            normalizedText.startsWith("enter ") ||
            normalizedText.startsWith("input ") ||
            normalizedText == "read notifications" ||
            normalizedText == "check notifications"
    }

    private fun isSettingsCommand(normalizedText: String): Boolean {
        return normalizedText == "open settings" ||
            normalizedText == "launch settings" ||
            normalizedText == "open phone settings" ||
            normalizedText == "open accessibility settings" ||
            normalizedText == "open usage access settings" ||
            normalizedText == "open usage permission" ||
            normalizedText == "open app usage settings"
    }

    private fun isPhoneCommand(normalizedText: String): Boolean {
        return normalizedText.startsWith("call ") ||
            normalizedText.startsWith("dial ")
    }

    private fun isScreenshotCommand(normalizedText: String): Boolean {
        return normalizedText == "take screenshot" ||
            normalizedText == "capture screenshot"
    }

    private fun isMessagePlanning(normalizedText: String): Boolean {
        val messageKeywords = listOf(
            "prepare message",
            "compose message",
            "draft message",
            "reply to",
            "send message",
            "text message"
        )

        return messageKeywords.any { containsPhrase(normalizedText, it) }
    }

    private fun containsAny(normalizedText: String, keywords: List<String>): Boolean {
        return keywords.any { containsPhrase(normalizedText, it) }
    }

    private fun containsPhrase(normalizedText: String, phrase: String): Boolean {
        val target = AssistantTextNormalizer.normalize(phrase)
        if (target.isBlank()) return false
        val regex = Regex("\\b${Regex.escape(target)}\\b")
        return regex.containsMatchIn(normalizedText)
    }
}
