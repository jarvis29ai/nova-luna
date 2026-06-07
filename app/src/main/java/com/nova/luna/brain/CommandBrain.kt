package com.nova.luna.brain

import android.content.Context
import com.nova.luna.executor.ActionExecutor
import com.nova.luna.executor.AppLauncher
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.memory.BrainMemoryStore
import com.nova.luna.memory.BrainSessionManager
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.ConfirmationResolution
import com.nova.luna.memory.FollowUpResolution
import com.nova.luna.memory.InMemoryBrainMemoryStore
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.memory.PendingConfirmationType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate
import com.nova.luna.agent.AgentLoop
import com.nova.luna.agent.TaskLoopCoordinator

class CommandBrain(
    context: Context,
    private val brainMemoryStore: BrainMemoryStore = InMemoryBrainMemoryStore(),
    private val brainService: BrainService = BrainService(brainMemoryStore = brainMemoryStore)
) {
    private val parser = RuleBasedCommandParser()
    private val appLauncher = AppLauncher(context.applicationContext)
    private val resolver = IntentResolver(appLauncher)
    private val safetyGate = SafetyGate()
    private val brainActionValidator = BrainActionValidator()
    private val router = CommandRouter(ActionExecutor(context.applicationContext))
    private val sessionManager = BrainSessionManager(brainMemoryStore)
    private val brainActionRuntime = BrainActionRuntime(router, safetyGate, brainActionValidator)
    private val taskLoopCoordinator: TaskLoopCoordinator = AgentLoop(
        brainService = brainService,
        brainActionRuntime = brainActionRuntime,
        brainSessionManager = sessionManager
    )

    fun process(rawText: String): CommandResult {
        val parsed = parser.parse(rawText)
        val memorySnapshot = sessionManager.snapshot()
        val activeSessionType = memorySnapshot.activeSessionType()
        val activePendingConfirmation = memorySnapshot.activePendingConfirmation
        val confirmationResolution = sessionManager.resolvePendingConfirmation(rawText)
        val followUpResolution = sessionManager.resolveFollowUp(rawText, activeSessionType)

        fun finish(result: CommandResult, sessionTypeHint: BrainSessionType? = result.memorySessionType ?: activeSessionType): CommandResult {
            val loopRecorded = result.memoryMetadata["agentLoopRecorded"]?.equals("true", ignoreCase = true) == true
            if (!loopRecorded) {
                sessionManager.recordCommandResult(
                    rawText = rawText,
                    commandIntent = parsed,
                    result = result,
                    sessionTypeHint = sessionTypeHint
                )
            }
            return result
        }

        if (parsed.intentType == IntentType.BLOCKED || parsed.actionType == ActionType.BLOCKED) {
            return finish(
                CommandResult.blocked(
                message = "Blocked command: payments, banking, checkout, passwords, OTPs, and CAPTCHA work must stay manual.",
                intentType = parsed.intentType,
                actionType = parsed.actionType,
                entities = parsed.entities
            )
            )
        }

        if (parsed.actionType == ActionType.STOP_SERVICE) {
            val decision = safetyGate.evaluate(parsed)
            if (!decision.allowed) {
                return finish(
                    CommandResult.blocked(
                message = decision.message,
                intentType = parsed.intentType,
                actionType = parsed.actionType,
                entities = parsed.entities
            )
                )
            }

            return finish(
                router.route(parsed).copy(
                    safetyDecision = decision,
                    memorySessionType = BrainSessionType.BASIC_CONTROL,
                    memoryMetadata = parsed.entities + mapOf("memoryFlow" to "stop_service")
                ),
                sessionTypeHint = BrainSessionType.BASIC_CONTROL
            )
        }

        if (confirmationResolution != null) {
            when {
                confirmationResolution.confirmed -> {
                    val pendingConfirmation = confirmationResolution.pendingConfirmation
                    sessionManager.consumePendingConfirmation(pendingConfirmation.confirmationId)

                    if (pendingConfirmation.sessionType == BrainSessionType.ONLINE_HELPER ||
                        pendingConfirmation.sessionType == BrainSessionType.LOCAL_LLM
                    ) {
                        replayPendingConfirmation(
                            rawText = rawText,
                            parsed = parsed,
                            pendingConfirmation = pendingConfirmation
                        )?.let { return finish(it, sessionTypeHint = pendingConfirmation.sessionType) }
                    }

                    pendingConfirmation.brainAction?.let { pendingBrainAction ->
                        handleBrainServiceAction(
                            brainAction = pendingBrainAction,
                            rawText = rawText,
                            parsed = parsed,
                            pendingConfirmation = pendingConfirmation,
                            userConfirmed = true
                        )?.let { return finish(it, sessionTypeHint = pendingConfirmation.sessionType) }
                    }

                    replayPendingConfirmation(
                        rawText = rawText,
                        parsed = parsed,
                        pendingConfirmation = pendingConfirmation
                    )?.let { return finish(it, sessionTypeHint = pendingConfirmation.sessionType) }

                    return finish(
                        CommandResult.failure(
                            message = "I could not continue the pending request yet.",
                            intentType = parsed.intentType,
                            actionType = parsed.actionType,
                            entities = parsed.entities,
                            memorySessionType = pendingConfirmation.sessionType
                        ),
                        sessionTypeHint = pendingConfirmation.sessionType
                    )
                }

                confirmationResolution.denied -> {
                    val pendingConfirmation = confirmationResolution.pendingConfirmation
                    sessionManager.consumePendingConfirmation(pendingConfirmation.confirmationId)
                    return finish(
                        CommandResult.success(
                            message = "Okay, I will keep it local.",
                            intentType = parsed.intentType,
                            actionType = parsed.actionType,
                            entities = parsed.entities,
                            memorySessionType = pendingConfirmation.sessionType
                        ),
                        sessionTypeHint = pendingConfirmation.sessionType
                    )
                }

                confirmationResolution.expired -> {
                    val pendingConfirmation = confirmationResolution.pendingConfirmation
                    sessionManager.consumePendingConfirmation(pendingConfirmation.confirmationId)
                    return finish(
                        CommandResult.failure(
                            message = "That confirmation expired. Please ask again.",
                            intentType = parsed.intentType,
                            actionType = parsed.actionType,
                            entities = parsed.entities,
                            memorySessionType = pendingConfirmation.sessionType
                        ),
                        sessionTypeHint = pendingConfirmation.sessionType
                    )
                }
            }
        }

        if (followUpResolution?.isSessionContinuation == true) {
            routeFollowUp(rawText, parsed, followUpResolution)?.let {
                return finish(it, sessionTypeHint = followUpResolution.sessionType)
            }
        }

        if (router.hasActiveGroceryBookingSession()) {
            return finish(router.routeGroceryConversation(rawText), sessionTypeHint = BrainSessionType.GROCERY)
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
                    finish(
                        CommandResult.biometricRequired(
                        message = decision.message,
                        intentType = resolved.intentType,
                        actionType = resolved.actionType,
                        entities = resolved.entities
                    )
                    )
                } else {
                    finish(
                        CommandResult.blocked(
                        message = decision.message,
                        intentType = resolved.intentType,
                        actionType = resolved.actionType,
                        entities = resolved.entities
                    )
                    )
                }
            }

            val result = router.route(resolved)
            return finish(
                result.copy(
                    safetyDecision = decision,
                    memorySessionType = result.memorySessionType ?: sessionTypeForResolvedIntent(resolved)
                ),
                sessionTypeHint = sessionTypeForResolvedIntent(resolved)
            )
        }

        if (router.hasActiveCabBookingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeCabConversation(rawText), sessionTypeHint = BrainSessionType.CAB)
        }

        if (router.hasActiveFoodBookingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeFoodConversation(rawText), sessionTypeHint = BrainSessionType.FOOD)
        }

        if (router.hasActivePhoneContactSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routePhoneContactConversation(rawText), sessionTypeHint = BrainSessionType.PHONE)
        }

        if (router.hasActiveCommunicationSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeCommunicationConversation(rawText), sessionTypeHint = BrainSessionType.COMMUNICATION)
        }

        if (router.hasActiveContentCreationSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeContentCreationConversation(rawText), sessionTypeHint = BrainSessionType.CONTENT)
        }

        if (router.hasActiveMusicSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeMusicConversation(rawText), sessionTypeHint = BrainSessionType.MUSIC)
        }

        if (router.hasActiveShoppingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeShoppingConversation(rawText), sessionTypeHint = BrainSessionType.SHOPPING)
        }

        if (router.hasActiveMediaSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(router.routeMediaConversation(rawText), sessionTypeHint = BrainSessionType.MEDIA)
        }

        if (router.hasActiveMediaSession() && shouldRouteMediaConversation(parsed)) {
            return finish(router.routeMediaConversation(rawText), sessionTypeHint = BrainSessionType.MEDIA)
        }

        if (shouldUseAgentLoop(parsed, rawText, activeSessionType, activePendingConfirmation, memorySnapshot)) {
            val loopResult = taskLoopCoordinator.run(
                rawText = rawText,
                commandIntent = parsed,
                activeCabSession = router.hasActiveCabBookingSession(),
                activeGrocerySession = router.hasActiveGroceryBookingSession(),
                activeFoodSession = router.hasActiveFoodBookingSession(),
                activeSessionType = activeSessionType,
                pendingConfirmation = activePendingConfirmation,
                onlineConsentGiven = false,
                memorySnapshot = memorySnapshot
            )
            return finish(
                loopResult.finalCommandResult,
                sessionTypeHint = loopResult.state.domainSessionType ?: activeSessionType
            )
        }

        if (shouldUseBrainService(parsed, rawText)) {
            val brainAction = brainService.process(
                rawText = rawText,
                activeCabSession = router.hasActiveCabBookingSession(),
                activeGrocerySession = router.hasActiveGroceryBookingSession(),
                activeFoodSession = router.hasActiveFoodBookingSession(),
                activeSessionType = activeSessionType,
                pendingConfirmation = activePendingConfirmation,
                onlineConsentGiven = false
            )

            handleBrainServiceAction(
                brainAction = brainAction,
                rawText = rawText,
                parsed = parsed,
                pendingConfirmation = activePendingConfirmation
            )?.let { return finish(it, sessionTypeHint = activeSessionType) }
        }

        if (parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return finish(
                CommandResult.failure(
                    unknownFallbackMessage(parsed.normalizedText),
                    parsed.intentType,
                    parsed.actionType,
                    parsed.entities,
                    memorySessionType = activeSessionType
                ),
                sessionTypeHint = activeSessionType
            )
        }

        val resolved = resolver.resolve(parsed)
        val decision = safetyGate.evaluate(resolved)
        if (!decision.allowed) {
            return if (decision.requiresBiometric) {
                finish(
                    CommandResult.biometricRequired(
                    message = decision.message,
                    intentType = resolved.intentType,
                    actionType = resolved.actionType,
                    entities = resolved.entities
                )
                )
            } else {
                finish(
                    CommandResult.blocked(
                    message = decision.message,
                    intentType = resolved.intentType,
                    actionType = resolved.actionType,
                    entities = resolved.entities
                )
                )
            }
        }

        val result = router.route(resolved)
        return finish(
            result.copy(
                safetyDecision = decision,
                memorySessionType = result.memorySessionType ?: sessionTypeForResolvedIntent(resolved)
            ),
            sessionTypeHint = sessionTypeForResolvedIntent(resolved)
        )
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

    private fun shouldUseBrainService(parsed: CommandIntent, rawText: String): Boolean {
        if (parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return isPlanningHeuristicCommand(parsed.normalizedText) ||
                isConversationHeuristicCommand(parsed.normalizedText) ||
                isFlexibleReasoningHeuristic(rawText, parsed.normalizedText) ||
                isOnlineHelperHeuristic(rawText)
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

    private fun shouldUseAgentLoop(
        parsed: CommandIntent,
        rawText: String,
        activeSessionType: BrainSessionType?,
        pendingConfirmation: PendingConfirmation?,
        memorySnapshot: com.nova.luna.memory.BrainMemorySnapshot
    ): Boolean {
        val taskPlan = brainService.buildTaskPlan(
            rawText = rawText,
            commandIntent = parsed,
            activeCabSession = router.hasActiveCabBookingSession(),
            activeGrocerySession = router.hasActiveGroceryBookingSession(),
            activeFoodSession = router.hasActiveFoodBookingSession(),
            activeSessionType = activeSessionType,
            pendingConfirmation = pendingConfirmation ?: memorySnapshot.activePendingConfirmation,
            screenState = null
        )
        return taskPlan.loopCapable
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

    private fun isFlexibleReasoningHeuristic(rawText: String, normalizedText: String): Boolean {
        if (normalizedText.isBlank()) {
            return containsNonLatinScript(rawText)
        }

        if (containsNonLatinScript(rawText)) {
            return true
        }

        val flexiblePhrases = listOf(
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

        return flexiblePhrases.any { phrase ->
            normalizedText.contains(phrase)
        } || normalizedText in setOf(
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

    private fun containsNonLatinScript(rawText: String): Boolean {
        return rawText.any { character -> character.code > 127 }
    }

    private fun isOnlineHelperHeuristic(rawText: String): Boolean {
        return OnlineAiPolicy().isPotentialCandidate(BrainRequest(rawText))
    }

    private fun isAffirmativeResponse(normalizedText: String): Boolean {
        return normalizedText in setOf(
            "yes",
            "yeah",
            "yep",
            "sure",
            "ok",
            "okay",
            "proceed",
            "yes please",
            "sure please",
            "please do it"
        )
    }

    private fun isNegativeResponse(normalizedText: String): Boolean {
        return normalizedText in setOf(
            "no",
            "nope",
            "not now",
            "not yet",
            "cancel"
        )
    }

    private fun handleBrainServiceAction(
        brainAction: BrainAction,
        rawText: String,
        parsed: CommandIntent,
        pendingConfirmation: PendingConfirmation? = null,
        userConfirmed: Boolean = false
    ): CommandResult? {
        return brainActionRuntime.execute(
            brainAction = brainAction,
            rawText = rawText,
            parsed = parsed,
            pendingConfirmation = pendingConfirmation,
            userConfirmed = userConfirmed
        )
    }

    private fun replayPendingConfirmation(
        rawText: String,
        parsed: CommandIntent,
        pendingConfirmation: PendingConfirmation
    ): CommandResult? {
        val originalRawText = pendingConfirmation.sanitizedMetadata["rawText"]
            ?.takeIf { it.isNotBlank() }
            ?: pendingConfirmation.brainAction?.params?.get("rawText")
            ?: rawText

        return when (pendingConfirmation.sessionType) {
            BrainSessionType.CAB -> router.routeCabConversation(originalRawText)
            BrainSessionType.FOOD -> router.routeFoodConversation(originalRawText)
            BrainSessionType.GROCERY -> router.routeGroceryConversation(originalRawText, userConfirmed = true)
            BrainSessionType.SHOPPING -> router.routeShoppingConversation(originalRawText)
            BrainSessionType.CONTENT -> router.routeContentCreationConversation(originalRawText)
            BrainSessionType.COMMUNICATION -> router.routeCommunicationConversation(originalRawText)
            BrainSessionType.PHONE -> router.routePhoneContactConversation(originalRawText)
            BrainSessionType.MUSIC -> router.routeMusicConversation(originalRawText)
            BrainSessionType.MEDIA -> router.routeMediaConversation(originalRawText)
            BrainSessionType.ONLINE_HELPER, BrainSessionType.LOCAL_LLM -> {
                val brainAction = brainService.process(
                    rawText = originalRawText,
                    activeCabSession = router.hasActiveCabBookingSession(),
                    activeGrocerySession = router.hasActiveGroceryBookingSession(),
                    activeFoodSession = router.hasActiveFoodBookingSession(),
                    activeSessionType = pendingConfirmation.sessionType,
                    pendingConfirmation = null,
                    onlineConsentGiven = true
                )
                handleBrainServiceAction(
                    brainAction = brainAction,
                    rawText = originalRawText,
                    parsed = parsed,
                    pendingConfirmation = null,
                    userConfirmed = true
                )
            }

            else -> null
        }
    }

    private fun routeFollowUp(
        rawText: String,
        parsed: CommandIntent,
        followUpResolution: FollowUpResolution
    ): CommandResult? {
        val sessionType = followUpResolution.sessionType ?: return null
        return when (sessionType) {
            BrainSessionType.CAB -> router.routeCabConversation(rawText)
            BrainSessionType.FOOD -> router.routeFoodConversation(rawText)
            BrainSessionType.GROCERY -> router.routeGroceryConversation(
                rawText = rawText,
                userConfirmed = followUpResolution.kind == com.nova.luna.memory.FollowUpKind.CONFIRMATION
            )
            BrainSessionType.SHOPPING -> router.routeShoppingConversation(rawText)
            BrainSessionType.CONTENT -> router.routeContentCreationConversation(rawText)
            BrainSessionType.COMMUNICATION -> router.routeCommunicationConversation(rawText)
            BrainSessionType.PHONE -> router.routePhoneContactConversation(rawText)
            BrainSessionType.MUSIC -> router.routeMusicConversation(rawText)
            BrainSessionType.MEDIA -> router.routeMediaConversation(rawText)
            BrainSessionType.ONLINE_HELPER,
            BrainSessionType.LOCAL_LLM -> {
                val brainAction = brainService.process(
                    rawText = rawText,
                    activeCabSession = router.hasActiveCabBookingSession(),
                    activeGrocerySession = router.hasActiveGroceryBookingSession(),
                    activeFoodSession = router.hasActiveFoodBookingSession(),
                    activeSessionType = sessionType,
                    pendingConfirmation = sessionManager.activePendingConfirmation(),
                    onlineConsentGiven = false
                )
                handleBrainServiceAction(
                    brainAction = brainAction,
                    rawText = rawText,
                    parsed = parsed,
                    pendingConfirmation = sessionManager.activePendingConfirmation()
                )
            }

            else -> null
        }
    }

    private fun sessionTypeForResolvedIntent(commandIntent: CommandIntent): BrainSessionType? {
        return when {
            commandIntent.intentType == IntentType.CAB_BOOKING || commandIntent.actionType == ActionType.CAB_BOOKING -> BrainSessionType.CAB
            commandIntent.intentType == IntentType.FOOD_ORDER || commandIntent.actionType == ActionType.FOOD_ORDER -> BrainSessionType.FOOD
            commandIntent.intentType == IntentType.GROCERY_BOOKING || commandIntent.actionType == ActionType.GROCERY_BOOKING -> BrainSessionType.GROCERY
            commandIntent.intentType == IntentType.SHOPPING || commandIntent.actionType == ActionType.SHOPPING -> BrainSessionType.SHOPPING
            commandIntent.intentType == IntentType.COMMUNICATION || commandIntent.actionType == ActionType.COMMUNICATION -> BrainSessionType.COMMUNICATION
            commandIntent.intentType == IntentType.CONTENT_CREATION || commandIntent.actionType == ActionType.CONTENT_CREATION -> BrainSessionType.CONTENT
            commandIntent.intentType == IntentType.MEDIA_CONTROL || commandIntent.actionType == ActionType.MEDIA_CONTROL -> BrainSessionType.MEDIA
            commandIntent.intentType == IntentType.READ_NOTIFICATIONS || commandIntent.actionType == ActionType.READ_NOTIFICATIONS -> BrainSessionType.BASIC_CONTROL
            commandIntent.intentType == IntentType.SENSITIVE -> BrainSessionType.BASIC_CONTROL
            commandIntent.intentType == IntentType.CONTROL -> BrainSessionType.BASIC_CONTROL
            else -> null
        }
    }

    private fun sessionTypeForBrainAction(brainAction: BrainAction): BrainSessionType? {
        return when {
            brainAction.intent.startsWith("cab", ignoreCase = true) -> BrainSessionType.CAB
            brainAction.intent.startsWith("food", ignoreCase = true) -> BrainSessionType.FOOD
            brainAction.intent.startsWith("grocery", ignoreCase = true) -> BrainSessionType.GROCERY
            brainAction.intent.startsWith("shopping", ignoreCase = true) -> BrainSessionType.SHOPPING
            brainAction.intent.startsWith("music", ignoreCase = true) -> BrainSessionType.MUSIC
            brainAction.intent.startsWith("media", ignoreCase = true) -> BrainSessionType.MEDIA
            brainAction.intent.startsWith("content", ignoreCase = true) -> BrainSessionType.CONTENT
            brainAction.intent.startsWith("communication", ignoreCase = true) -> BrainSessionType.COMMUNICATION
            brainAction.intent.startsWith("phone", ignoreCase = true) -> BrainSessionType.PHONE
            brainAction.intent.startsWith("screen", ignoreCase = true) -> BrainSessionType.SCREEN
            brainAction.intent.equals("online_ai_permission", ignoreCase = true) -> BrainSessionType.ONLINE_HELPER
            brainAction.intent.equals("local_model_unavailable", ignoreCase = true) -> BrainSessionType.LOCAL_LLM
            else -> null
        }
    }

    private fun confirmationTypeForBrainAction(brainAction: BrainAction): PendingConfirmationType {
        return when {
            brainAction.intent.equals("online_ai_permission", ignoreCase = true) -> PendingConfirmationType.ONLINE_AI_USE
            brainAction.intent.startsWith("cab", ignoreCase = true) -> PendingConfirmationType.BOOK_RIDE
            brainAction.intent.startsWith("food", ignoreCase = true) -> PendingConfirmationType.PLACE_ORDER
            brainAction.intent.startsWith("grocery", ignoreCase = true) -> PendingConfirmationType.PLACE_ORDER
            brainAction.intent.startsWith("shopping", ignoreCase = true) -> PendingConfirmationType.PLACE_ORDER
            brainAction.intent.startsWith("communication", ignoreCase = true) -> PendingConfirmationType.SEND_MESSAGE
            brainAction.intent.startsWith("content", ignoreCase = true) -> PendingConfirmationType.EXPORT_CONTENT
            brainAction.intent.startsWith("phone", ignoreCase = true) -> PendingConfirmationType.CALL_CONTACT
            else -> PendingConfirmationType.GENERIC_SAFE_ACTION
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
