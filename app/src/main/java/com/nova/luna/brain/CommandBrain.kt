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
import com.nova.luna.model.SafetyStatus
import com.nova.luna.safety.SafetyGate
import com.nova.luna.agent.AgentLoop
import com.nova.luna.agent.TaskLoopCoordinator
import com.nova.luna.llm.*

class CommandBrain(
    context: Context,
    private val brainMemoryStore: BrainMemoryStore = InMemoryBrainMemoryStore(),
    private val personalMemoryStore: com.nova.luna.memory.PersonalMemoryStore = com.nova.luna.memory.LocalPersonalMemoryStore(context),
    brainService: BrainService? = null
) {
    private val runtimeConfig = BrainRuntimeConfig.fromBuildConfig()
    private val storage = com.nova.luna.modelinstall.PrivateAppModelStorage.from(context)
    private val runtimeStateStore = com.nova.luna.modelinstall.ModelRuntimeStateStore(storage)
    private val modelInstallService = com.nova.luna.modelinstall.ModelInstallService(
        specRegistry = com.nova.luna.modelinstall.ModelInstallSpecRegistry(),
        pathResolver = com.nova.luna.modelinstall.ModelPathResolver(context, storage, runtimeStateStore),
        verifier = com.nova.luna.modelinstall.ModelInstallVerifier(),
        runtimeStateStore = runtimeStateStore,
        storage = storage
    )
    private val ramInfoProvider = AndroidRamInfoProvider(context)
    private val ramGuard = ModelRamGuard(ramInfoProvider)
    private val modelRuntimeManager = ModelRuntimeManager(
        loader = ModelRuntimeLoader(
            storage = storage,
            modelInstallService = modelInstallService,
            liteRealInferenceEnabled = runtimeConfig.liteRealInferenceEnabled
        ),
        ramGuard = ramGuard
    )
    private val runtimeLoader = ModelRuntimeLoader(
        storage = storage,
        modelInstallService = modelInstallService,
        liteRealInferenceEnabled = runtimeConfig.liteRealInferenceEnabled
    )
    private val gemmaRuntime = PhoneGemmaRuntime(
        config = GemmaPhoneConfig.fromBuildConfig(),
        backend = if (com.nova.luna.BuildConfig.GEMMA_REAL_INFERENCE_ENABLED) {
            LiteRTGemmaRuntimeBackend(context)
        } else {
            UnavailablePhoneGemmaRuntimeBackend()
        }
    )
    private val bridge = com.nova.luna.modelinstall.ModelInstallBrainRouterBridge(
        modelInstallService,
        coreRuntimeAvailable = { gemmaRuntime.isReady() }
    )

    private val coreModel = LocalBrainModelClient(
        role = com.nova.luna.model.BrainModelRole.CORE_BRAIN,
        roleReadinessProvider = bridge,
        manager = modelRuntimeManager
    )
    private val fullModel = LocalBrainModelClient(
        role = com.nova.luna.model.BrainModelRole.MULTILINGUAL_BACKUP,
        roleReadinessProvider = bridge,
        manager = modelRuntimeManager
    )
    private val liteModel = LocalBrainModelClient(
        role = com.nova.luna.model.BrainModelRole.LITE_FALLBACK,
        roleReadinessProvider = bridge,
        manager = modelRuntimeManager
    )

    private val brainService: BrainService = brainService ?: BrainService(
        brainMemoryStore = brainMemoryStore,
        localBrainRouterBridge = bridge,
        coreBrainModelOverride = coreModel,
        multilingualBackupModelOverride = fullModel,
        liteFallbackModelOverride = liteModel,
        modelInstallService = modelInstallService,
        modelRuntimeManager = modelRuntimeManager,
        gemmaRuntime = gemmaRuntime
    )

    private val parser = RuleBasedCommandParser()
    private val appLauncher = AppLauncher(context.applicationContext)
    private val resolver = IntentResolver(appLauncher)
    private val safetyGate = SafetyGate()
    private val brainActionValidator = BrainActionValidator()
    private val appResolver = com.nova.luna.phone.AppResolver(context.applicationContext)
    private val flashlightController = com.nova.luna.phone.FlashlightController(context.applicationContext)
    private val navigationController = com.nova.luna.phone.NavigationController()
    private val phoneActionExecutor = com.nova.luna.phone.AndroidPhoneActionExecutor(
        context.applicationContext,
        appResolver,
        flashlightController,
        navigationController
    )
    private val router = CommandRouter(ActionExecutor(context.applicationContext))
    private val sessionManager = BrainSessionManager(brainMemoryStore)
    private val brainActionRuntime = BrainActionRuntime(
        commandRouter = router,
        safetyGate = safetyGate,
        confirmationManager = com.nova.luna.confirmation.ConfirmationManagerProvider.instance,
        phoneActionExecutor = phoneActionExecutor,
        validator = brainActionValidator
    )
    private val taskLoopCoordinator: TaskLoopCoordinator = AgentLoop(
        brainService = this.brainService,
        brainActionRuntime = brainActionRuntime,
        brainSessionManager = sessionManager
    )

    private val personalMemoryManager = com.nova.luna.memory.PersonalMemoryManager(personalMemoryStore)
    private val memoryContextProvider = com.nova.luna.memory.MemoryContextProvider(personalMemoryStore)

    private val unifiedRouter = UnifiedDomainRouter(listOf(
        SystemHandler(),
        FoodHandler(),
        CabHandler(),
        GroceryHandler(),
        ShoppingHandler(),
        CommunicationHandler(),
        ContentHandler(),
        MediaHandler(),
        MusicHandler()
    ))

    private val localLlmManager = LocalLlmManager(context)
    private var activeContext = AssistantContext()

    fun process(rawText: String): CommandResult {
        val memorySnapshot = sessionManager.snapshot()
        val activeMemorySession = memorySnapshot.activeSessionType()
        val activeExecutorSession = bridgeExecutorSession()
        val activeSessionType = activeMemorySession ?: activeExecutorSession
        
        val activePendingConfirmation = memorySnapshot.activePendingConfirmation
        val confirmationResolution = sessionManager.resolvePendingConfirmation(rawText)
        val followUpResolution = sessionManager.resolveFollowUp(rawText, activeSessionType)

        // Phase 8: Load Relevant Memory Context
        val memoryContext = memoryContextProvider.getContextForCommand(rawText, activeContext.activeDomain?.name)
        activeContext = activeContext.copy(
            memoryContext = memoryContext
        )

        // Pre-initialize routeResult with mandatory fields to avoid NPE in finish()
        var routeResult = UnifiedRouteResult(
            status = RouteStatus.UNSUPPORTED,
            routeDecision = UnifiedRouteDecision(
                selectedDomain = UnifiedDomain.UNKNOWN,
                confidence = 0.0f,
                reason = "initial",
                sourceCommand = rawText,
                normalizedCommand = rawText
            )
        )

        // Pre-initialize currentParsed for finish helper
        var currentParsed = parser.parse(rawText)

        fun finish(result: CommandResult, sessionTypeHint: BrainSessionType? = result.memorySessionType ?: activeSessionType): CommandResult {
            val finalDomain = if (result.domain == UnifiedDomain.UNKNOWN && routeResult.status == RouteStatus.ROUTED) {
                routeResult.routeDecision.selectedDomain
            } else {
                result.domain
            }
            val resWithContext = result.copy(
                domain = finalDomain,
                memorySessionType = result.memorySessionType ?: sessionTypeHint
            )
            
            val loopRecorded = resWithContext.memoryMetadata["agentLoopRecorded"]?.equals("true", ignoreCase = true) == true
            if (!loopRecorded) {
                sessionManager.recordCommandResult(
                    rawText = rawText,
                    commandIntent = currentParsed,
                    result = resWithContext,
                    sessionTypeHint = sessionTypeHint
                )
            }
            return resWithContext
        }

        // 1. Handle Confirmations First
        if (confirmationResolution != null) {
            when {
                confirmationResolution.confirmed -> {
                    val pendingConfirmation = confirmationResolution.pendingConfirmation
                    sessionManager.consumePendingConfirmation(pendingConfirmation.confirmationId)

                    // Priority: Handle BrainAction if attached, then replay specific domains
                    pendingConfirmation.brainAction?.let { pendingBrainAction ->
                        handleBrainServiceAction(
                            brainAction = pendingBrainAction,
                            rawText = rawText,
                            parsed = currentParsed,
                            pendingConfirmation = pendingConfirmation,
                            userConfirmed = true
                        )?.let { return finish(it, sessionTypeHint = pendingConfirmation.sessionType) }
                    }

                    replayPendingConfirmation(
                        rawText = rawText,
                        parsed = currentParsed,
                        pendingConfirmation = pendingConfirmation
                    )?.let { return finish(it, sessionTypeHint = pendingConfirmation.sessionType) }
                }

                confirmationResolution.denied -> {
                    val pendingConfirmation = confirmationResolution.pendingConfirmation
                    sessionManager.consumePendingConfirmation(pendingConfirmation.confirmationId)
                    val message = when (pendingConfirmation.sessionType) {
                        BrainSessionType.ONLINE_HELPER -> "Okay, I will keep it local."
                        else -> "Okay, I stopped."
                    }
                    return finish(
                        CommandResult.success(
                            message = message,
                            memorySessionType = pendingConfirmation.sessionType
                        ),
                        sessionTypeHint = pendingConfirmation.sessionType
                    )
                }
            }
        }

        // 2. Bare-word Fallback (Higher priority than routing)
        if (activeSessionType == null) {
            val fallbackMsg = unknownFallbackMessage(rawText)
            if (fallbackMsg != "I couldn't understand that yet.") {
                return finish(CommandResult.failure(fallbackMsg, IntentType.UNKNOWN, ActionType.UNKNOWN, currentParsed.entities))
            }
        }

        // 3. Unified Domain Routing (Phase 5) + Local LLM (Phase 7)
        routeResult = unifiedRouter.route(rawText, activeContext)
        
        if (routeResult.status != RouteStatus.ROUTED || routeResult.routeDecision.confidence < 0.75f) {
            val llmRequest = LocalLlmRequest(
                commandText = rawText,
                currentDomainGuess = routeResult.routeDecision.selectedDomain,
                assistantContext = activeContext
            )
            val llmResult = localLlmManager.process(llmRequest)
            if (llmResult.status == LocalLlmStatus.READY && llmResult.parsedCandidateAction != null) {
                routeResult = routeResult.copy(
                    status = RouteStatus.ROUTED,
                    commandIntent = llmResult.parsedCandidateAction,
                    routeDecision = routeResult.routeDecision.copy(
                        selectedDomain = llmResult.parsedDomain,
                        confidence = llmResult.confidence,
                        reason = "Enhanced by local LLM (${llmResult.modelId.name})"
                    )
                )
            }
        }

        // Update context
        activeContext = activeContext.copy(
            activeDomain = if (routeResult.status == RouteStatus.ROUTED) routeResult.routeDecision.selectedDomain else activeContext.activeDomain,
            lastCommand = rawText,
            lastRouteDecision = routeResult.routeDecision
        )

        // 4. Finalize currentParsed from routing if available
        if (routeResult.status == RouteStatus.ROUTED && routeResult.commandIntent != null) {
            currentParsed = routeResult.commandIntent!!
        }

        // 5. Safe continuation routes that do not execute phone actions yet
        if (followUpResolution?.isSessionContinuation == true) {
            routeFollowUp(rawText, currentParsed, followUpResolution)?.let {
                return finish(it, sessionTypeHint = followUpResolution.sessionType)
            }
        }

        if (activeSessionType != null && (currentParsed.intentType == IntentType.UNKNOWN || isBarePlaybackCommand(rawText))) {
            when (activeSessionType) {
                BrainSessionType.CAB -> return finish(router.routeCabConversation(rawText, currentParsed))
                BrainSessionType.FOOD -> return finish(router.routeFoodConversation(rawText, currentParsed))
                BrainSessionType.GROCERY -> return finish(router.routeGroceryConversation(rawText, currentParsed))
                BrainSessionType.MUSIC -> return finish(router.routeMusicConversation(rawText, currentParsed))
                BrainSessionType.MEDIA -> return finish(router.routeMediaConversation(rawText, currentParsed))
                else -> {}
            }
        }

        if (routeResult.status == RouteStatus.ROUTED &&
            routeResult.routeDecision.selectedDomain == UnifiedDomain.FOOD &&
            currentParsed.actionType == ActionType.FOOD_ORDER &&
            !rawText.contains("now", ignoreCase = true) &&
            !rawText.contains("final", ignoreCase = true) &&
            !rawText.contains("checkout", ignoreCase = true) &&
            !rawText.contains("pay", ignoreCase = true)
        ) {
            return finish(router.routeFoodConversation(rawText, currentParsed).copy(domain = UnifiedDomain.FOOD), BrainSessionType.FOOD)
        }

        // 5. Mandatory Safety Check (Phase 6 stabilization)
        val safetyDecision = safetyGate.evaluate(intent = currentParsed)
        if (safetyDecision.status == SafetyStatus.BLOCKED) {
            return finish(
                CommandResult.blocked(
                    safetyDecision.reason,
                    intentType = currentParsed.intentType,
                    actionType = currentParsed.actionType,
                    entities = currentParsed.entities,
                    safetyDecision = safetyDecision
                )
            )
        }
        if (safetyDecision.status == SafetyStatus.CONFIRMATION_REQUIRED) {
            return finish(
                CommandResult.confirmationRequired(
                    safetyDecision.reason,
                    intentType = currentParsed.intentType,
                    actionType = currentParsed.actionType,
                    entities = currentParsed.entities,
                    safetyDecision = safetyDecision
                )
            )
        }

        // 6. Handle Stop/Cancel
        if (currentParsed.actionType == ActionType.STOP_SERVICE) {
            return finish(
                router.route(currentParsed).copy(
                    safetyDecision = safetyDecision,
                    memorySessionType = BrainSessionType.BASIC_CONTROL,
                    memoryMetadata = currentParsed.entities + mapOf("memoryFlow" to "stop_service")
                ),
                sessionTypeHint = BrainSessionType.BASIC_CONTROL
            )
        }

        // 8. Domain Specific Conversations
        if (routeResult.status == RouteStatus.ROUTED) {
            val domain = routeResult.routeDecision.selectedDomain
            when (domain) {
                UnifiedDomain.CAB -> return finish(router.routeCabConversation(rawText, currentParsed).copy(domain = domain), BrainSessionType.CAB)
                UnifiedDomain.FOOD -> return finish(router.routeFoodConversation(rawText, currentParsed).copy(domain = domain), BrainSessionType.FOOD)
                UnifiedDomain.GROCERY -> return finish(router.routeGroceryConversation(rawText, currentParsed).copy(domain = domain), BrainSessionType.GROCERY)
                UnifiedDomain.SHOPPING -> return finish(router.routeShoppingConversation(rawText, currentParsed).copy(domain = domain), BrainSessionType.SHOPPING)
                UnifiedDomain.COMMUNICATION -> return finish(router.routeCommunicationConversation(rawText, currentParsed).copy(domain = domain), BrainSessionType.COMMUNICATION)
                UnifiedDomain.CONTENT -> return finish(router.routeContentCreationConversation(rawText, currentParsed).copy(domain = domain), BrainSessionType.CONTENT)
                else -> {}
            }
        }

        // 9. Agent Loop & Brain Service
        if (shouldUseAgentLoop(currentParsed, rawText, activeSessionType, activePendingConfirmation, memorySnapshot)) {
            val loopResult = taskLoopCoordinator.run(
                rawText = rawText,
                commandIntent = currentParsed,
                activeCabSession = router.hasActiveCabBookingSession(),
                activeGrocerySession = router.hasActiveGroceryBookingSession(),
                activeFoodSession = router.hasActiveFoodBookingSession(),
                activeSessionType = activeSessionType,
                pendingConfirmation = activePendingConfirmation,
                onlineConsentGiven = false,
                memorySnapshot = memorySnapshot
            )
            return finish(loopResult.finalCommandResult, loopResult.state.domainSessionType ?: activeSessionType)
        }

        if (routeResult.status != RouteStatus.ROUTED && shouldUseBrainService(currentParsed, rawText)) {
            val brainAction = brainService.process(
                rawText = rawText,
                activeCabSession = router.hasActiveCabBookingSession(),
                activeGrocerySession = router.hasActiveGroceryBookingSession(),
                activeFoodSession = router.hasActiveFoodBookingSession(),
                onlineConsentGiven = false,
                activeSessionType = activeSessionType,
                pendingConfirmation = activePendingConfirmation
            )
            handleBrainServiceAction(brainAction, rawText, currentParsed, activePendingConfirmation)?.let { 
                return finish(it, it.memorySessionType ?: activeSessionType) 
            }
        }

        // 10. Fallback
        val resolved = resolver.resolve(currentParsed)
        return finish(router.route(resolved).copy(domain = routeResult.routeDecision.selectedDomain))
    }

    private fun bridgeExecutorSession(): BrainSessionType? {
        return try {
            when {
                router.hasActiveCabBookingSession() -> BrainSessionType.CAB
                router.hasActiveFoodBookingSession() -> BrainSessionType.FOOD
                router.hasActiveGroceryBookingSession() -> BrainSessionType.GROCERY
                router.hasActiveMusicSession() -> BrainSessionType.MUSIC
                router.hasActiveMediaSession() -> BrainSessionType.MEDIA
                router.hasActivePhoneContactSession() -> BrainSessionType.PHONE
                router.hasActiveCommunicationSession() -> BrainSessionType.COMMUNICATION
                router.hasActiveContentCreationSession() -> BrainSessionType.CONTENT
                router.hasActiveShoppingSession() -> BrainSessionType.SHOPPING
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun unknownFallbackMessage(text: String): String {
        val n = text.lowercase().trim().removePrefix("luna ").removePrefix("nova ").trim()
        return when {
            n == "compare" -> "Please tell me what you'd like me to compare."
            n == "cancel" || n == "stop" -> "There isn't an active task to cancel right now."
            n == "continue" -> "There isn't an active task to continue yet."
            n in setOf("play", "pause", "resume", "next", "previous", "skip") -> "Please tell me which song, video, or app you want."
            n == "send it" || n == "send" -> "I need an active draft before I can send anything."
            n == "cheapest" || n == "best" -> "I need an active list before I can choose that option."
            n in setOf("first one", "second one", "third one", "the first one", "the second one", "the third one") -> "I need an active list before I can select one."
            n in setOf("yes", "yeah", "yep", "sure", "ok", "okay", "no", "nope", "proceed") -> "Please tell me which app or task you want."
            else -> "I couldn't understand that yet."
        }
    }

    private fun isBarePlaybackCommand(rawText: String): Boolean {
        val trimmed = rawText.trim().lowercase().removePrefix("luna ").removePrefix("nova ").trim()
        return trimmed in setOf("play", "pause", "resume", "stop", "next", "previous", "skip")
    }

    private fun shouldUseBrainService(parsed: CommandIntent, rawText: String): Boolean {
        if (parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return isPlanningHeuristicCommand(parsed.normalizedText) ||
                isConversationHeuristicCommand(parsed.normalizedText) ||
                isFlexibleReasoningHeuristic(rawText, parsed.normalizedText) ||
                isOnlineHelperHeuristic(rawText)
        }

        return false
    }

    private fun shouldUseAgentLoop(
        parsed: CommandIntent,
        rawText: String,
        activeSessionType: BrainSessionType?,
        pendingConfirmation: PendingConfirmation?,
        memorySnapshot: com.nova.luna.memory.BrainMemorySnapshot
    ): Boolean {
        if (parsed.actionType == ActionType.OPEN_USAGE_ACCESS_SETTINGS) {
            return false
        }

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
        val planningKeywords = listOf("plan", "schedule", "organize", "todo", "to do")
        return planningKeywords.any { normalizedText.contains(it) }
    }

    private fun isConversationHeuristicCommand(normalizedText: String): Boolean {
        if (isQuestionLike(normalizedText)) return true
        val keywords = listOf("explain", "tell me", "how does", "how do", "why does", "what is", "what are")
        return keywords.any { normalizedText.contains(it) }
    }

    private fun isFlexibleReasoningHeuristic(rawText: String, normalizedText: String): Boolean {
        if (normalizedText.isBlank()) return containsNonLatinScript(rawText)
        if (containsNonLatinScript(rawText)) return true
        val phrases = listOf("help me", "please help", "can you help", "summarize", "rewrite", "translate")
        return phrases.any { normalizedText.contains(it) }
    }

    private fun isQuestionLike(normalizedText: String): Boolean {
        return normalizedText.endsWith("?") || normalizedText.startsWith("who ") || normalizedText.startsWith("what ")
    }

    private fun isCommunicationDraftingCommand(normalizedText: String): Boolean {
        val keywords = listOf("prepare message", "compose message", "draft message")
        return keywords.any { normalizedText.contains(it) }
    }

    private fun containsNonLatinScript(rawText: String): Boolean = rawText.any { it.code > 127 }

    private fun isOnlineHelperHeuristic(rawText: String): Boolean = OnlineAiPolicy().isPotentialCandidate(BrainRequest(rawText))

    private fun handleBrainServiceAction(
        brainAction: BrainAction,
        rawText: String,
        parsed: CommandIntent,
        pendingConfirmation: PendingConfirmation? = null,
        userConfirmed: Boolean = false
    ): CommandResult? {
        val result = brainActionRuntime.execute(
            brainAction = brainAction,
            rawText = rawText,
            parsed = parsed,
            pendingConfirmation = pendingConfirmation,
            userConfirmed = userConfirmed
        )
        val sessionType = sessionTypeForBrainAction(brainAction)
        return result?.let {
            if (it.memorySessionType == null) {
                it.copy(memorySessionType = sessionType ?: pendingConfirmation?.sessionType)
            } else {
                it
            }
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

    private fun replayPendingConfirmation(
        rawText: String,
        parsed: CommandIntent,
        pendingConfirmation: PendingConfirmation
    ): CommandResult? {
        val originalRawText = pendingConfirmation.sanitizedMetadata["rawText"]
            ?.takeIf { it.isNotBlank() }
            ?: pendingConfirmation.brainAction?.params?.get("rawText")
            ?: rawText

        val originalParsed = parser.parse(originalRawText)

        return when (pendingConfirmation.sessionType) {
            BrainSessionType.CAB -> router.routeCabConversation(originalRawText, originalParsed)
            BrainSessionType.FOOD -> router.routeFoodConversation(originalRawText, originalParsed)
            BrainSessionType.GROCERY -> router.routeGroceryConversation(originalRawText, originalParsed, userConfirmed = true)
            BrainSessionType.SHOPPING -> router.routeShoppingConversation(originalRawText, originalParsed)
            BrainSessionType.CONTENT -> router.routeContentCreationConversation(originalRawText, originalParsed)
            BrainSessionType.COMMUNICATION -> router.routeCommunicationConversation(originalRawText, originalParsed)
            BrainSessionType.PHONE -> router.routePhoneContactConversation(originalRawText, originalParsed)
            BrainSessionType.MUSIC -> router.routeMusicConversation(originalRawText, originalParsed)
            BrainSessionType.MEDIA -> router.routeMediaConversation(originalRawText, originalParsed)
            BrainSessionType.ONLINE_HELPER, BrainSessionType.LOCAL_LLM -> {
                val brainAction = brainService.process(
                    rawText = originalRawText,
                    activeCabSession = router.hasActiveCabBookingSession(),
                    activeGrocerySession = router.hasActiveGroceryBookingSession(),
                    activeFoodSession = router.hasActiveFoodBookingSession(),
                    onlineConsentGiven = true,
                    activeSessionType = pendingConfirmation.sessionType,
                    pendingConfirmation = null
                )
                handleBrainServiceAction(
                    brainAction = brainAction,
                    rawText = originalRawText,
                    parsed = originalParsed,
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
            BrainSessionType.CAB -> router.routeCabConversation(rawText, parsed)
            BrainSessionType.FOOD -> router.routeFoodConversation(rawText, parsed)
            BrainSessionType.GROCERY -> router.routeGroceryConversation(
                rawText = rawText,
                commandIntent = parsed,
                userConfirmed = followUpResolution.kind == com.nova.luna.memory.FollowUpKind.CONFIRMATION
            )
            BrainSessionType.SHOPPING -> router.routeShoppingConversation(rawText, parsed)
            BrainSessionType.CONTENT -> router.routeContentCreationConversation(rawText, parsed)
            BrainSessionType.COMMUNICATION -> router.routeCommunicationConversation(rawText, parsed)
            BrainSessionType.PHONE -> router.routePhoneContactConversation(rawText, parsed)
            BrainSessionType.MUSIC -> router.routeMusicConversation(rawText, parsed)
            BrainSessionType.MEDIA -> router.routeMediaConversation(rawText, parsed)
            BrainSessionType.ONLINE_HELPER,
            BrainSessionType.LOCAL_LLM -> {
                val brainAction = brainService.process(
                    rawText = rawText,
                    activeCabSession = router.hasActiveCabBookingSession(),
                    activeGrocerySession = router.hasActiveGroceryBookingSession(),
                    activeFoodSession = router.hasActiveFoodBookingSession(),
                    onlineConsentGiven = false,
                    activeSessionType = sessionType,
                    pendingConfirmation = sessionManager.activePendingConfirmation()
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
}
