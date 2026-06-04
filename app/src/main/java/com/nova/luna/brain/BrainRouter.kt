package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.InternetPermissionCategory
import com.nova.luna.food.FoodIntentParser
import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.util.AssistantTextNormalizer

class BrainRouter(
    private val internetPermissionPolicy: InternetPermissionPolicy = InternetPermissionPolicy()
) {
    private val foodIntentParser = FoodIntentParser()
    private val groceryIntentParser = GroceryIntentParser()

    fun route(request: BrainRequest): BrainRouteDecision {
        val normalized = normalize(request.rawText)
        if (normalized.isBlank()) {
            return decision(
                role = BrainModelRole.MOCK_FALLBACK,
                reason = "Empty input stays on the guaranteed fallback path.",
                safetyNotes = listOf(
                    "No model should execute actions directly.",
                    "LocalMockBrainProvider remains the guaranteed fallback."
                )
            )
        }

        if (isScreenQuery(normalized)) {
            return decision(
                role = BrainModelRole.SCREEN_UNDERSTANDING,
                reason = "This request asks for read-only screen understanding.",
                requiresScreenContext = true,
                safetyNotes = listOf(
                    "ScreenUnderstandingModel is read-only only.",
                    "No screen model may execute phone actions directly."
                )
            )
        }

        if (request.activeGrocerySession || isGroceryPlanning(request.rawText)) {
            return decision(
                role = BrainModelRole.ACTION_JSON,
                reason = if (request.activeGrocerySession) {
                    "An active grocery session needs structured action JSON continuity."
                } else {
                    "This request is grocery planning and should stay structured locally."
                },
                requiresInternet = false,
                safetyNotes = listOf(
                    "ActionJsonModel may only produce safe BrainAction JSON for grocery flows.",
                    "Payment, OTP, login, CAPTCHA, and other sensitive steps must remain manual."
                )
            )
        }

        if (isFoodPlanning(request.rawText)) {
            return decision(
                role = BrainModelRole.ACTION_JSON,
                reason = "This request is food planning and should stay structured locally.",
                requiresInternet = false,
                safetyNotes = listOf(
                    "ActionJsonModel may only produce safe BrainAction JSON for food flows.",
                    "Payment, OTP, login, CAPTCHA, and other sensitive steps must remain manual."
                )
            )
        }

        if (isSimpleCommand(normalized)) {
            return decision(
                role = BrainModelRole.LITE_COMMAND,
                reason = "This is a fast local device command.",
                safetyNotes = listOf(
                    "LiteCommandModel handles quick offline commands only.",
                    "No model may call ActionExecutor directly."
                )
            )
        }

        if (isPlanningRequest(normalized, request.activeCabSession)) {
            return decision(
                role = BrainModelRole.ACTION_JSON,
                reason = if (request.activeCabSession) {
                    "An active cab session needs structured action JSON continuity."
                } else {
                    "This request is cab, food, or task planning and should stay structured."
                },
                requiresInternet = needsInternetForPlanning(normalized),
                safetyNotes = listOf(
                    "ActionJsonModel may only produce safe BrainAction JSON.",
                    "Final payment, OTP, login, send-money, and delete steps must remain manual."
                )
            )
        }

        if (isConversation(normalized)) {
            val internetDecision = internetPermissionPolicy.classify(request.rawText)
            return decision(
                role = BrainModelRole.GEMMA_REASONING,
                reason = "This is general conversation or explanation, so the reasoning role fits best.",
                requiresInternet = internetDecision.category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO ||
                    internetDecision.category == InternetPermissionCategory.INTERNET_OPTIONAL,
                safetyNotes = listOf(
                    "Gemma is intended to be the final on-device reasoning model.",
                    "Any action it suggests must still pass BrainActionValidator."
                )
            )
        }

        return decision(
            role = BrainModelRole.MOCK_FALLBACK,
            reason = "No confident phone model role matched this input.",
            requiresInternet = internetPermissionPolicy.classify(request.rawText).category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO,
            safetyNotes = listOf(
                "Unknown requests stay on the guaranteed fallback path.",
                "LocalMockBrainProvider keeps offline behavior available."
            )
        )
    }

    private fun decision(
        role: BrainModelRole,
        reason: String,
        requiresInternet: Boolean = false,
        requiresScreenContext: Boolean = false,
        safetyNotes: List<String>
    ): BrainRouteDecision {
        return BrainRouteDecision(
            selectedRole = role,
            reason = reason,
            requiresInternet = requiresInternet,
            requiresScreenContext = requiresScreenContext,
            fallbackAllowed = true,
            safetyNotes = safetyNotes
        )
    }

    private fun isSimpleCommand(normalized: String): Boolean {
        return isStopCommand(normalized) ||
            isGoHomeCommand(normalized) ||
            isOpenAppCommand(normalized) ||
            isNavigationCommand(normalized) ||
            isQuickInteractionCommand(normalized) ||
            isSettingsCommand(normalized)
    }

    private fun isGroceryPlanning(rawText: String): Boolean {
        return groceryIntentParser.parseInitialGroceryRequest(rawText) != null
    }

    private fun isFoodPlanning(rawText: String): Boolean {
        return foodIntentParser.parse(rawText) != null
    }

    private fun isStopCommand(normalized: String): Boolean {
        return normalized == "stop" ||
            normalized == "cancel" ||
            normalized == "stop listening" ||
            normalized == "cancel listening" ||
            normalized == "stop speaking" ||
            normalized == "cancel voice" ||
            normalized == "quiet" ||
            normalized == "be quiet"
    }

    private fun isGoHomeCommand(normalized: String): Boolean {
        return normalized == "go home" ||
            normalized == "home" ||
            normalized == "back to home"
    }

    private fun isOpenAppCommand(normalized: String): Boolean {
        return normalized.startsWith("open app ") ||
            normalized.startsWith("open ") ||
            normalized.startsWith("launch ") ||
            normalized.startsWith("start ")
    }

    private fun isNavigationCommand(normalized: String): Boolean {
        return normalized == "go back" ||
            normalized == "back" ||
            normalized == "previous" ||
            normalized == "show recents" ||
            normalized == "open recents" ||
            normalized == "recent apps" ||
            normalized == "recents" ||
            normalized == "open notifications" ||
            normalized == "show notifications" ||
            normalized == "scroll down" ||
            normalized == "scroll up" ||
            normalized == "swipe down" ||
            normalized == "swipe up"
    }

    private fun isQuickInteractionCommand(normalized: String): Boolean {
        return normalized.startsWith("tap ") ||
            normalized.startsWith("tap on ") ||
            normalized.startsWith("click ") ||
            normalized.startsWith("click on ") ||
            normalized.startsWith("press ") ||
            normalized.startsWith("press on ") ||
            normalized.startsWith("type ") ||
            normalized.startsWith("write ") ||
            normalized.startsWith("enter ") ||
            normalized.startsWith("input ") ||
            normalized == "read notifications" ||
            normalized == "check notifications"
    }

    private fun isSettingsCommand(normalized: String): Boolean {
        return normalized == "open settings" ||
            normalized == "launch settings" ||
            normalized == "open phone settings" ||
            normalized == "open accessibility settings" ||
            normalized == "open usage access settings" ||
            normalized == "open usage permission" ||
            normalized == "open app usage settings"
    }

    private fun isPlanningRequest(normalized: String, activeCabSession: Boolean): Boolean {
        if (activeCabSession) return true

        val planningKeywords = listOf(
            "cab",
            "ride",
            "taxi",
            "uber",
            "ola",
            "rapido",
            "delivery",
            "restaurant",
            "meal",
            "food",
            "dinner",
            "lunch",
            "breakfast",
            "task",
            "todo",
            "to do",
            "plan",
            "schedule",
            "organize",
            "book"
        )

        if (planningKeywords.any { containsKeyword(normalized, it) }) {
            return true
        }

        return normalized.contains("order") && normalized.contains("food")
    }

    private fun isScreenQuery(normalized: String): Boolean {
        val screenKeywords = listOf(
            "screen",
            "what do you see",
            "what's on my screen",
            "whats on my screen",
            "read the screen",
            "analyze the screen",
            "describe the screen",
            "screen text",
            "screen reading",
            "read this page",
            "look at this screen",
            "screenshot"
        )

        return screenKeywords.any { containsPhrase(normalized, it) }
    }

    private fun isConversation(normalized: String): Boolean {
        if (isQuestionLike(normalized)) return true

        val conversationKeywords = listOf(
            "explain",
            "tell me",
            "how does",
            "how do",
            "why does",
            "what is",
            "what are",
            "help me understand",
            "talk to me",
            "summarize",
            "compare",
            "what can you do"
        )

        return conversationKeywords.any { containsPhrase(normalized, it) }
    }

    private fun isQuestionLike(normalized: String): Boolean {
        return normalized.endsWith("?") ||
            normalized.startsWith("who ") ||
            normalized.startsWith("what ") ||
            normalized.startsWith("when ") ||
            normalized.startsWith("where ") ||
            normalized.startsWith("why ") ||
            normalized.startsWith("how ")
    }

    private fun needsInternetForPlanning(normalized: String): Boolean {
        return containsAny(normalized, listOf("cab", "ride", "taxi", "uber", "ola", "rapido", "food", "restaurant", "delivery", "order"))
    }

    private fun containsAny(normalized: String, keywords: List<String>): Boolean {
        return keywords.any { containsKeyword(normalized, it) }
    }

    private fun containsKeyword(normalized: String, keyword: String): Boolean {
        val target = normalize(keyword)
        if (target.isBlank()) return false
        return Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return normalized.contains(target)
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }
}
