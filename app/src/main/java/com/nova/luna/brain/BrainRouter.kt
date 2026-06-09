package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.InternetPermissionCategory
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.food.FoodIntentParser
import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.content.ContentCreationCommandType
import com.nova.luna.content.ContentCreationIntentParser
import com.nova.luna.util.AssistantTextNormalizer

class BrainRouter(
    private val internetPermissionPolicy: InternetPermissionPolicy = InternetPermissionPolicy(),
    private val onlineAiConfig: OnlineAiConfig = OnlineAiConfig.fromBuildConfig(),
    private val internetAvailable: Boolean = false,
    private val onlineAiPolicy: OnlineAiPolicy = OnlineAiPolicy(internetPermissionPolicy = internetPermissionPolicy)
) {
    private val foodIntentParser = FoodIntentParser()
    private val groceryIntentParser = GroceryIntentParser()
    private val contentCreationIntentParser = ContentCreationIntentParser()

    fun route(request: BrainRequest, allowOnlineHelper: Boolean = true): BrainRouteDecision {
        val normalized = normalize(request.rawText)
        if (normalized.isBlank() && !containsNonLatinScript(request.rawText)) {
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

        routeForActiveSession(request)?.let { return it }

        if (isMessagePlanning(normalized)) {
            return decision(
                role = BrainModelRole.ACTION_JSON,
                reason = "This request is message planning and should stay structured locally.",
                requiresInternet = false,
                safetyNotes = listOf(
                    "ActionJsonModel may only produce safe BrainAction JSON for message drafting flows.",
                    "Final send, OTP, login, CAPTCHA, and other sensitive steps must remain manual."
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

        if (request.activeFoodSession || isFoodPlanning(request.rawText)) {
            return decision(
                role = BrainModelRole.ACTION_JSON,
                reason = if (request.activeFoodSession) {
                    "An active food session needs structured action JSON continuity."
                } else {
                    "This request is food planning and should stay structured locally."
                },
                requiresInternet = false,
                safetyNotes = listOf(
                    "ActionJsonModel may only produce safe BrainAction JSON for food flows.",
                    "Payment, OTP, login, CAPTCHA, and other sensitive steps must remain manual."
                )
            )
        }

        if (isFlexibleReasoningRequest(request.rawText, normalized)) {
            val internetDecision = internetPermissionPolicy.classify(request.rawText)
            return decision(
                role = BrainModelRole.GEMMA_REASONING,
                reason = "This is a fuzzy, multilingual, or natural-language request that should stay on the local reasoning model.",
                requiresInternet = internetDecision.category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO ||
                    internetDecision.category == InternetPermissionCategory.INTERNET_OPTIONAL,
                safetyNotes = listOf(
                    "Gemma is the final on-device reasoning model for fuzzy or multilingual prompts.",
                    "Any action it suggests must still pass BrainActionValidator.",
                    "SafetyGate must still control any sensitive step."
                )
            )
        }

        if (isContentPlanning(request.rawText)) {
            return decision(
                role = BrainModelRole.ACTION_JSON,
                reason = "This request is content creation or editing and should stay structured locally.",
                requiresInternet = false,
                safetyNotes = listOf(
                    "ActionJsonModel may only produce safe BrainAction JSON for content creation flows.",
                    "Any final export, share, login, payment, or confirmation step must remain manual."
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

        val onlineCandidate = onlineAiPolicy.isPotentialCandidate(request)
        if (onlineCandidate) {
            val internetDecision = internetPermissionPolicy.classify(request.rawText)
            val onlineAvailable = allowOnlineHelper &&
                onlineAiConfig.enabled &&
                onlineAiConfig.providerType != OnlineAiProviderType.UNAVAILABLE &&
                internetAvailable

            return if (onlineAvailable) {
                decision(
                    role = BrainModelRole.ONLINE_AI_HELPER,
                    reason = "This request is better handled by the optional online research/content helper.",
                    requiresInternet = true,
                    safetyNotes = listOf(
                        "Online helper may only return drafts, summaries, or safe suggestions.",
                        "It must never control the phone directly.",
                        "BrainActionValidator, SafetyGate, and the local router still remain final authorities."
                    )
                )
            } else {
                decision(
                    role = BrainModelRole.GEMMA_REASONING,
                    reason = "This request is suited to online help, but it will stay on the local reasoning model because online help is disabled or unavailable.",
                    requiresInternet = internetDecision.category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO ||
                        internetDecision.category == InternetPermissionCategory.INTERNET_OPTIONAL,
                    safetyNotes = listOf(
                        "Optional online helper is unavailable, so the local reasoning model takes over.",
                        "Any action it suggests must still pass BrainActionValidator.",
                        "SafetyGate must still control any sensitive step."
                    )
                )
            }
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

    private fun routeForActiveSession(request: BrainRequest): BrainRouteDecision? {
        val activeSessionType = request.activeSessionType
            ?: request.memorySnapshot?.activeSessionType()
            ?: when {
                request.activeCabSession -> BrainSessionType.CAB
                request.activeGrocerySession -> BrainSessionType.GROCERY
                request.activeFoodSession -> BrainSessionType.FOOD
                else -> null
            }

        return when (activeSessionType) {
            BrainSessionType.CAB,
            BrainSessionType.FOOD,
            BrainSessionType.GROCERY,
            BrainSessionType.SHOPPING,
            BrainSessionType.CONTENT,
            BrainSessionType.COMMUNICATION,
            BrainSessionType.PHONE -> decision(
                role = BrainModelRole.ACTION_JSON,
                reason = "An active ${activeSessionType.wireValue} session should stay on structured local action JSON.",
                requiresInternet = false,
                safetyNotes = listOf(
                    "Active session continuity stays local and structured.",
                    "Final payment, OTP, login, send, and other sensitive steps must remain manual."
                )
            )

            BrainSessionType.MUSIC,
            BrainSessionType.MEDIA,
            BrainSessionType.BASIC_CONTROL -> decision(
                role = BrainModelRole.LITE_COMMAND,
                reason = "An active ${activeSessionType.wireValue} session should stay on a fast local command path.",
                safetyNotes = listOf(
                    "Active control sessions stay local and lightweight.",
                    "No model may call ActionExecutor directly."
                )
            )

            BrainSessionType.SCREEN -> decision(
                role = BrainModelRole.SCREEN_UNDERSTANDING,
                reason = "An active screen session should stay on the read-only screen model.",
                requiresScreenContext = true,
                safetyNotes = listOf(
                    "ScreenUnderstandingModel is read-only only.",
                    "No screen model may execute phone actions directly."
                )
            )

            BrainSessionType.ONLINE_HELPER -> {
                val onlineAvailable = onlineAiConfig.enabled &&
                    onlineAiConfig.providerType != OnlineAiProviderType.UNAVAILABLE &&
                    internetAvailable

                if (onlineAvailable) {
                    decision(
                        role = BrainModelRole.ONLINE_AI_HELPER,
                        reason = "An active online helper session can continue with the optional online helper.",
                        requiresInternet = true,
                        safetyNotes = listOf(
                            "Online helper may only return drafts, summaries, or safe suggestions.",
                            "It must never control the phone directly."
                        )
                    )
                } else {
                    decision(
                        role = BrainModelRole.GEMMA_REASONING,
                        reason = "An online helper session is active, but it will stay on the local reasoning model because online help is disabled or unavailable.",
                        requiresInternet = false,
                        safetyNotes = listOf(
                            "Optional online helper is unavailable, so the local reasoning model takes over.",
                            "Any action it suggests must still pass BrainActionValidator."
                        )
                    )
                }
            }

            BrainSessionType.LOCAL_LLM -> decision(
                role = BrainModelRole.GEMMA_REASONING,
                reason = "An active local LLM session should stay on the local reasoning model.",
                safetyNotes = listOf(
                    "Gemma is the final on-device reasoning model for active local sessions.",
                    "Any action it suggests must still pass BrainActionValidator."
                )
            )

            else -> null
        }
    }

    private fun isSimpleCommand(normalized: String): Boolean {
        return isStopCommand(normalized) ||
            isGoHomeCommand(normalized) ||
            isOpenAppCommand(normalized) ||
            isNavigationCommand(normalized) ||
            isQuickInteractionCommand(normalized) ||
            isPhoneCommand(normalized) ||
            isScreenshotCommand(normalized) ||
            isSettingsCommand(normalized)
    }

    private fun isGroceryPlanning(rawText: String): Boolean {
        return groceryIntentParser.parseInitialGroceryRequest(rawText) != null
    }

    private fun isFoodPlanning(rawText: String): Boolean {
        return foodIntentParser.parse(rawText) != null
    }

    private fun isStopCommand(normalized: String): Boolean {
        return normalized == "stop listening" ||
            normalized == "cancel listening" ||
            normalized == "stop speaking" ||
            normalized == "cancel speaking" ||
            normalized == "cancel voice" ||
            normalized == "stop voice" ||
            normalized == "stop service" ||
            normalized == "cancel service" ||
            normalized == "stop assistant" ||
            normalized == "cancel assistant" ||
            normalized == "quiet" ||
            normalized == "be quiet"
    }

    private fun isGoHomeCommand(normalized: String): Boolean {
        return normalized == "go home" ||
            normalized == "home" ||
            normalized == "back to home"
    }

    private fun isOpenAppCommand(normalized: String): Boolean {
        if (isMessagePlanning(normalized)) {
            return false
        }

        return normalized.startsWith("open app ") ||
            normalized.startsWith("open ") ||
            normalized.startsWith("launch ") ||
            normalized.startsWith("start ")
    }

    private fun isNavigationCommand(normalized: String): Boolean {
        return normalized == "go back" ||
            normalized == "go previous" ||
            normalized == "back" ||
            normalized == "previous" ||
            normalized == "show recent apps" ||
            normalized == "show recents" ||
            normalized == "open recent apps" ||
            normalized == "open recents" ||
            normalized == "recent apps" ||
            normalized == "recents" ||
            normalized == "open notifications" ||
            normalized == "show notifications" ||
            normalized == "scroll down" ||
            normalized == "move down" ||
            normalized == "scroll up" ||
            normalized == "move up" ||
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
            "prepare message",
            "compose message",
            "draft message",
            "reply to",
            "send message",
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

    private fun isContentPlanning(rawText: String): Boolean {
        val commandType = contentCreationIntentParser.parse(rawText).commandType
        return commandType in setOf(
            ContentCreationCommandType.CREATE_PPT,
            ContentCreationCommandType.CREATE_IMAGE,
            ContentCreationCommandType.CREATE_VIDEO,
            ContentCreationCommandType.CREATE_DOCUMENT,
            ContentCreationCommandType.CREATE_EXCEL,
            ContentCreationCommandType.CREATE_PDF,
            ContentCreationCommandType.CREATE_OTHER,
            ContentCreationCommandType.DETECT_BEST_FORMAT,
            ContentCreationCommandType.EDIT_DRAFT,
            ContentCreationCommandType.REGENERATE_DRAFT,
            ContentCreationCommandType.FINALIZE_OUTPUT,
            ContentCreationCommandType.EXPORT_FILE,
            ContentCreationCommandType.SHARE_FILE
        )
    }

    private fun isMessagePlanning(normalized: String): Boolean {
        val messageKeywords = listOf(
            "prepare message",
            "compose message",
            "draft message",
            "message to",
            "reply to",
            "send message",
            "text message"
        )

        return messageKeywords.any { containsPhrase(normalized, it) }
    }

    private fun isScreenQuery(normalized: String): Boolean {
        if (isScreenshotCommand(normalized)) {
            return false
        }

        val screenKeywords = listOf(
            "screen",
            "what do you see",
            "what's on my screen",
            "whats on my screen",
            "what screen am i on",
            "what app is open",
            "which app is open",
            "what app am i in",
            "current app",
            "foreground app",
            "read the screen",
            "analyze the screen",
            "describe the screen",
            "screen text",
            "screen reading",
            "read this page",
            "look at this screen",
            "what buttons are visible",
            "what fields are visible",
            "what can i tap",
            "read the visible buttons",
            "read the visible fields"
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

    private fun isPhoneCommand(normalized: String): Boolean {
        return normalized.startsWith("call ") ||
            normalized.startsWith("dial ")
    }

    private fun isScreenshotCommand(normalized: String): Boolean {
        return normalized == "take screenshot" ||
            normalized == "capture screenshot"
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

    private fun isFlexibleReasoningRequest(rawText: String, normalized: String): Boolean {
        if (normalized.isBlank()) {
            return containsNonLatinScript(rawText)
        }

        if (containsNonLatinScript(rawText)) {
            return true
        }

        val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
        if (wordCount == 0) {
            return false
        }

        val phraseSignals = listOf(
            "help me",
            "please help",
            "can you help",
            "could you help",
            "would you help",
            "can you please",
            "could you please",
            "would you please",
            "please explain",
            "explain this",
            "summarize this",
            "rewrite this",
            "translate this",
            "draft this",
            "compare these",
            "compare this",
            "what should i",
            "what do you think",
            "how should i",
            "how can i",
            "kya karu",
            "kaise karu",
            "kya tum",
            "batao",
            "samjhao",
            "please batao",
            "please samjhao",
            "thoda help",
            "thoda sa help",
            "make this",
            "improve this",
            "check this"
        )

        if (phraseSignals.any { containsPhrase(normalized, it) }) {
            return true
        }

        if (wordCount == 1) {
            return normalized in setOf(
                "help",
                "explain",
                "translate",
                "rewrite",
                "summarize",
                "draft",
                "recommend",
                "suggest",
                "analyze",
                "samjhao",
                "batao",
                "kya",
                "kaise",
                "kyu",
                "please"
            )
        }

        return normalized.contains("please") ||
            normalized.contains("help") ||
            normalized.contains("explain") ||
            normalized.contains("translate") ||
            normalized.contains("rewrite") ||
            normalized.contains("summarize") ||
            normalized.contains("recommend") ||
            normalized.contains("suggest")
    }

    private fun containsNonLatinScript(rawText: String): Boolean {
        return rawText.any { character -> character.code > 127 }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }
}
