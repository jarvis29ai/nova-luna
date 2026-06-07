package com.nova.luna.brain

import android.content.Context
import com.nova.luna.executor.ActionExecutor
import com.nova.luna.executor.AppLauncher
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate

class CommandBrain(
    context: Context,
    private val brainService: BrainService = BrainService()
) {
    private val parser = RuleBasedCommandParser()
    private val appLauncher = AppLauncher(context.applicationContext)
    private val resolver = IntentResolver(appLauncher)
    private val safetyGate = SafetyGate()
    private val brainActionValidator = BrainActionValidator()
    private val router = CommandRouter(ActionExecutor(context.applicationContext))

    fun process(rawText: String): CommandResult {
        val parsed = parser.parse(rawText)

        if (parsed.intentType == IntentType.BLOCKED || parsed.actionType == ActionType.BLOCKED) {
            return CommandResult.blocked(
                message = "Blocked command: payments, banking, checkout, passwords, OTPs, and CAPTCHA work must stay manual.",
                intentType = parsed.intentType,
                actionType = parsed.actionType,
                entities = parsed.entities
            )
        }

        if (parsed.actionType == ActionType.STOP_SERVICE) {
            val decision = safetyGate.evaluate(parsed)
            if (!decision.allowed) {
                return CommandResult.blocked(
                    message = decision.message,
                    intentType = parsed.intentType,
                    actionType = parsed.actionType,
                    entities = parsed.entities
                )
            }

            return router.route(parsed).copy(safetyDecision = decision)
        }

        if (router.hasActiveGroceryBookingSession()) {
            return router.routeGroceryConversation(rawText)
        }

        if (
            parsed.intentType == IntentType.CAB_BOOKING ||
            parsed.actionType == ActionType.CAB_BOOKING ||
            parsed.intentType == IntentType.FOOD_ORDER ||
            parsed.actionType == ActionType.FOOD_ORDER ||
            parsed.intentType == IntentType.GROCERY_BOOKING ||
            parsed.actionType == ActionType.GROCERY_BOOKING ||
            parsed.intentType == IntentType.COMMUNICATION ||
            parsed.actionType == ActionType.COMMUNICATION ||
            parsed.intentType == IntentType.CONTENT_CREATION ||
            parsed.actionType == ActionType.CONTENT_CREATION
        ) {
            val resolved = resolver.resolve(parsed)
            val decision = safetyGate.evaluate(resolved)
            if (!decision.allowed) {
                return if (decision.requiresBiometric) {
                    CommandResult.biometricRequired(
                        message = decision.message,
                        intentType = resolved.intentType,
                        actionType = resolved.actionType,
                        entities = resolved.entities
                    )
                } else {
                    CommandResult.blocked(
                        message = decision.message,
                        intentType = resolved.intentType,
                        actionType = resolved.actionType,
                        entities = resolved.entities
                    )
                }
            }

            val result = router.route(resolved)
            return result.copy(safetyDecision = decision)
        }

        if (router.hasActiveCabBookingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeCabConversation(rawText)
        }

        if (router.hasActiveFoodBookingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeFoodConversation(rawText)
        }

        if (router.hasActivePhoneContactSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routePhoneContactConversation(rawText)
        }

        if (router.hasActiveCommunicationSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeCommunicationConversation(rawText)
        }

        if (router.hasActiveContentCreationSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeContentCreationConversation(rawText)
        }

        if (router.hasActiveMusicSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeMusicConversation(rawText)
        }

        if (router.hasActiveShoppingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeShoppingConversation(rawText)
        }

        if (router.hasActiveMediaSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeMediaConversation(rawText)
        }

        if (router.hasActiveMediaSession() && shouldRouteMediaConversation(parsed)) {
            return router.routeMediaConversation(rawText)
        }

        if (shouldUseBrainService(parsed)) {
            val brainAction = brainService.process(
                rawText = rawText,
                activeCabSession = router.hasActiveCabBookingSession(),
                activeGrocerySession = router.hasActiveGroceryBookingSession(),
                activeFoodSession = router.hasActiveFoodBookingSession()
            )

            handleBrainServiceAction(brainAction)?.let { return it }
        }

        if (parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return CommandResult.failure(
                unknownFallbackMessage(parsed.normalizedText),
                parsed.intentType,
                parsed.actionType,
                parsed.entities
            )
        }

        val resolved = resolver.resolve(parsed)
        val decision = safetyGate.evaluate(resolved)
        if (!decision.allowed) {
            return if (decision.requiresBiometric) {
                CommandResult.biometricRequired(
                    message = decision.message,
                    intentType = resolved.intentType,
                    actionType = resolved.actionType,
                    entities = resolved.entities
                )
            } else {
                CommandResult.blocked(
                    message = decision.message,
                    intentType = resolved.intentType,
                    actionType = resolved.actionType,
                    entities = resolved.entities
                )
            }
        }

        val result = router.route(resolved)
        return result.copy(safetyDecision = decision)
    }

    private fun unknownFallbackMessage(normalizedText: String): String {
        return when (normalizedText) {
            "compare" -> "Please tell me what you'd like me to compare."
            "cancel",
            "stop" -> "There isn't an active task to cancel right now."

            "continue" -> "There isn't an active task to continue yet."
            "play",
            "pause",
            "resume",
            "next",
            "previous",
            "skip" -> "Please tell me which song, video, or app you want."

            "send it",
            "send" -> "I need an active draft before I can send anything."

            "cheapest",
            "best" -> "I need an active list before I can choose that option."

            "first one",
            "second one",
            "third one",
            "the first one",
            "the second one",
            "the third one" -> "I need an active list before I can select one."

            "yes",
            "yeah",
            "yep",
            "sure",
            "ok",
            "okay",
            "no",
            "nope",
            "proceed" -> "Please tell me which app or task you want."

            else -> "I couldn't understand that yet."
        }
    }

    private fun shouldRouteMediaConversation(parsed: CommandIntent): Boolean {
        if (parsed.actionType != ActionType.MUSIC) {
            return false
        }

        val normalized = parsed.normalizedText
        val mediaKeywords = listOf(
            "youtube",
            "shorts",
            "instagram",
            "reels",
            "netflix",
            "hotstar",
            "prime video",
            "movie",
            "movies",
            "show",
            "shows",
            "episode",
            "episodes",
            "watchlist",
            "my list",
            "video",
            "videos",
            "channel",
            "profile",
            "creator",
            "feed"
        )

        if (mediaKeywords.any { normalized.contains(it) }) {
            return false
        }

        return normalized == "play" ||
            normalized == "pause" ||
            normalized == "resume" ||
            normalized == "continue" ||
            normalized == "next" ||
            normalized == "previous" ||
            normalized == "skip" ||
            normalized == "stop" ||
            normalized == "stop music" ||
            normalized == "pause music" ||
            normalized == "resume music" ||
            normalized == "next song" ||
            normalized == "previous song" ||
            normalized == "increase volume" ||
            normalized == "decrease volume" ||
            normalized == "volume up" ||
            normalized == "volume down"
    }

    private fun shouldUseBrainService(parsed: CommandIntent): Boolean {
        if (parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return isPlanningHeuristicCommand(parsed.normalizedText) ||
                isConversationHeuristicCommand(parsed.normalizedText)
        }

        return when {
            parsed.actionType == ActionType.MUSIC ||
                parsed.intentType == IntentType.MEDIA_CONTROL ||
                parsed.intentType == IntentType.SHOPPING -> false

            parsed.intentType == IntentType.COMMUNICATION ->
                isCommunicationDraftingCommand(parsed.normalizedText)

            parsed.intentType == IntentType.CONTENT_CREATION -> true

            parsed.intentType == IntentType.CAB_BOOKING ||
                parsed.intentType == IntentType.FOOD_ORDER ||
                parsed.intentType == IntentType.GROCERY_BOOKING -> true

            parsed.intentType == IntentType.OPEN_APP ||
                parsed.intentType == IntentType.NAVIGATION ||
                parsed.intentType == IntentType.INTERACTION ||
                parsed.intentType == IntentType.TEXT_ENTRY ||
                parsed.intentType == IntentType.READ_NOTIFICATIONS -> true

            parsed.intentType == IntentType.SENSITIVE ->
                parsed.actionType == ActionType.CALL_CONTACT ||
                    parsed.actionType == ActionType.TAKE_SCREENSHOT ||
                    parsed.actionType == ActionType.OPEN_SETTINGS ||
                    parsed.actionType == ActionType.OPEN_ACCESSIBILITY_SETTINGS ||
                    parsed.actionType == ActionType.OPEN_USAGE_ACCESS_SETTINGS

            parsed.intentType == IntentType.CONTROL ->
                parsed.actionType != ActionType.MUSIC &&
                    parsed.actionType != ActionType.STOP_SERVICE

            else -> false
        }
    }

    private fun isPlanningHeuristicCommand(normalizedText: String): Boolean {
        val planningKeywords = listOf(
            "plan",
            "schedule",
            "organize",
            "todo",
            "to do"
        )

        return planningKeywords.any { phrase ->
            normalizedText.contains(phrase)
        }
    }

    private fun isConversationHeuristicCommand(normalizedText: String): Boolean {
        if (isQuestionLike(normalizedText)) {
            return true
        }

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
            "what can you do"
        )

        return conversationKeywords.any { phrase ->
            normalizedText.contains(phrase)
        }
    }

    private fun isQuestionLike(normalizedText: String): Boolean {
        return normalizedText.endsWith("?") ||
            normalizedText.startsWith("who ") ||
            normalizedText.startsWith("what ") ||
            normalizedText.startsWith("when ") ||
            normalizedText.startsWith("where ") ||
            normalizedText.startsWith("why ") ||
            normalizedText.startsWith("how ")
    }

    private fun isCommunicationDraftingCommand(normalizedText: String): Boolean {
        val draftingKeywords = listOf(
            "prepare message",
            "compose message",
            "draft message",
            "message to",
            "reply to",
            "draft reply",
            "draft email",
            "write a reply",
            "write professional email"
        )

        return draftingKeywords.any { phrase ->
            normalizedText.contains(phrase)
        }
    }

    private fun handleBrainServiceAction(brainAction: BrainAction): CommandResult? {
        if (!brainActionValidator.isAcceptable(brainAction)) {
            return null
        }

        val safetyDecision = safetyGate.evaluate(brainAction)
        val resultIntentType = resultIntentType(brainAction)
        val resultActionType = resultActionType(brainAction)

        if (!safetyDecision.allowed) {
            return when {
                safetyDecision.requiresBiometric -> CommandResult.biometricRequired(
                    message = safetyDecision.message,
                    intentType = resultIntentType,
                    actionType = resultActionType,
                    entities = brainAction.params
                )

                safetyDecision.requiresConfirmation -> CommandResult.confirmationRequired(
                    message = safetyDecision.message,
                    intentType = resultIntentType,
                    actionType = resultActionType,
                    entities = brainAction.params
                )

                else -> CommandResult.blocked(
                    message = safetyDecision.message,
                    intentType = resultIntentType,
                    actionType = resultActionType,
                    entities = brainAction.params
                )
            }
        }

        return when (brainAction.actionType) {
            BrainActionType.EXTERNAL_ACTION -> {
                val routedResult = router.route(brainAction)
                routedResult.copy(safetyDecision = safetyDecision)
            }

            BrainActionType.READ_ONLY,
            BrainActionType.NONE -> CommandResult.success(
                message = brainAction.reply,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params
            )

            BrainActionType.PREPARE -> CommandResult.confirmationRequired(
                message = brainAction.nextQuestion?.takeIf { it.isNotBlank() }
                    ?: safetyDecision.message,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params
            )

            BrainActionType.HUMAN_ONLY -> CommandResult.blocked(
                message = safetyDecision.message,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params
            )
        }
    }

    private fun resultIntentType(brainAction: BrainAction): IntentType {
        val mapped = brainAction.toCommandIntent()
        if (mapped.intentType != IntentType.UNKNOWN) {
            return mapped.intentType
        }

        return when {
            brainAction.intent.startsWith("content", ignoreCase = true) -> IntentType.CONTENT_CREATION
            else -> IntentType.UNKNOWN
        }
    }

    private fun resultActionType(brainAction: BrainAction): ActionType {
        val mapped = brainAction.toCommandIntent()
        if (mapped.actionType != ActionType.UNKNOWN) {
            return mapped.actionType
        }

        return when {
            brainAction.intent.startsWith("content", ignoreCase = true) -> ActionType.CONTENT_CREATION
            else -> ActionType.UNKNOWN
        }
    }
}
