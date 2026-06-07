package com.nova.luna.brain

import com.nova.luna.agent.TaskPlan
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.BrainRuntimeStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.InternetPermissionCategory
import com.nova.luna.model.InternetPermissionDecision
import com.nova.luna.model.IntentType
import com.nova.luna.memory.BrainMemoryStore
import com.nova.luna.memory.BrainSessionManager
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.InMemoryBrainMemoryStore
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateReader
import com.nova.luna.safety.SafetyGate
import java.util.Locale

class BrainService(
    private val provider: BrainProvider? = null,
    private val fallbackProvider: BrainProvider = LocalMockBrainProvider(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec(),
    private val validator: BrainActionValidator = BrainActionValidator(),
    private val safetyGate: SafetyGate = SafetyGate(),
    private val runtimeConfig: BrainRuntimeConfig = BrainRuntimeConfig.fromBuildConfig(),
    private val internetPermissionPolicy: InternetPermissionPolicy = InternetPermissionPolicy(),
    private val internetAvailable: Boolean = false,
    private val onlineAiConfig: OnlineAiConfig = OnlineAiConfig.fromBuildConfig(),
    private val onlineAiProvider: OnlineAiProvider = OnlineAiProviderFactory.create(onlineAiConfig),
    private val onlineAiPolicy: OnlineAiPolicy = OnlineAiPolicy(),
    private val onlineAiHelper: PhoneBrainModel = OnlineAiHelper(
        config = onlineAiConfig,
        provider = onlineAiProvider,
        networkStatusProvider = StaticNetworkStatusProvider(internetAvailable),
        policy = onlineAiPolicy
    ),
    private val phoneBrainProvider: PhoneBrainProvider = UnavailablePhoneBrainProvider(),
    private val ollamaClient: OllamaClient = HttpOllamaClient(),
    private val brainRouter: BrainRouter = BrainRouter(
        onlineAiConfig = onlineAiConfig,
        internetAvailable = internetAvailable,
        onlineAiPolicy = onlineAiPolicy
    ),
    private val gemmaRuntime: PhoneGemmaRuntime = PhoneGemmaRuntime(),
    private val gemmaBrainModel: PhoneBrainModel = GemmaBrainModel(gemmaRuntime),
    private val actionJsonModel: PhoneBrainModel = ActionJsonModel(),
    private val liteCommandModel: PhoneBrainModel = LiteCommandModel(),
    private val screenStateReader: ScreenStateReader = ScreenStateReader(),
    private val brainMemoryStore: BrainMemoryStore = InMemoryBrainMemoryStore()
) {
    private val screenUnderstandingModel: PhoneBrainModel = ScreenUnderstandingModel(screenStateReader)
    private val sessionManager: BrainSessionManager = BrainSessionManager(brainMemoryStore)

    private val runtimeSelection by lazy {
        BrainProviderFactory.createSelection(
            config = runtimeConfig,
            client = ollamaClient,
            internetAvailable = internetAvailable,
            phoneBrainProvider = phoneBrainProvider
        )
    }

    fun process(
        rawText: String,
        activeCabSession: Boolean = false,
        activeGrocerySession: Boolean = false,
        activeFoodSession: Boolean = false,
        onlineConsentGiven: Boolean = false,
        activeSessionType: BrainSessionType? = null,
        pendingConfirmation: PendingConfirmation? = null,
        screenState: ScreenState? = null
    ): BrainAction {
        val memorySnapshot = sessionManager.snapshot()
        val effectiveActiveSessionType = activeSessionType ?: memorySnapshot.activeSessionType()
        val effectivePendingConfirmation = pendingConfirmation ?: memorySnapshot.activePendingConfirmation
        val request = BrainRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            screenState = screenState,
            onlineConsentGiven = onlineConsentGiven,
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmation = effectivePendingConfirmation,
            memorySnapshot = memorySnapshot,
            preferences = memorySnapshot.preferences,
            recoveryState = effectiveActiveSessionType?.let { memorySnapshot.recoveryStates[it] }
        )

        val policyDecision = internetPermissionPolicy.classify(rawText)
        when (policyDecision.category) {
            InternetPermissionCategory.BLOCKED_SENSITIVE -> {
                return rememberBrainAction(
                    request,
                    null,
                    blockedHumanOnlyAction(rawText, policyDecision.reason)
                )
            }

            InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO -> {
                if (!internetAvailable) {
                    return rememberBrainAction(
                        request,
                        null,
                        offlineInfoAction(rawText, policyDecision.reason)
                    )
                }
            }

            else -> Unit
        }

        if (provider != null) {
            val primaryAttempt = evaluateProvider(provider, request)
            if (isAccepted(primaryAttempt.parsedAction)) {
                return rememberBrainAction(request, null, primaryAttempt.parsedAction!!)
            }

            val fallbackAttempt = evaluateProvider(fallbackProvider, request)
            if (isAccepted(fallbackAttempt.parsedAction)) {
                return rememberBrainAction(request, null, fallbackAttempt.parsedAction!!)
            }

            return rememberBrainAction(request, null, fallback(rawText))
        }

        val routeDecision = brainRouter.route(request)
        val routedRequest = request.withScreenStateIfNeeded(routeDecision)
        val primaryAttempt = evaluateRouteDecision(routeDecision, routedRequest)
        if (isAccepted(primaryAttempt.parsedAction)) {
            return rememberBrainAction(request, routeDecision, primaryAttempt.parsedAction!!)
        }

        if (routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER) {
            val fallbackRouteDecision = brainRouter.route(request, allowOnlineHelper = false)
            val fallbackRoutedRequest = request.withScreenStateIfNeeded(fallbackRouteDecision)
            val fallbackRouteAttempt = evaluateRouteDecision(fallbackRouteDecision, fallbackRoutedRequest)

            if (isAccepted(fallbackRouteAttempt.parsedAction)) {
                return rememberBrainAction(request, fallbackRouteDecision, fallbackRouteAttempt.parsedAction!!)
            }

            if (fallbackRouteDecision.selectedRole == BrainModelRole.GEMMA_REASONING) {
                return rememberBrainAction(
                    request,
                    fallbackRouteDecision,
                    localModelFallbackAction(
                        rawText = rawText,
                        routeDecision = fallbackRouteDecision,
                        reason = fallbackRouteAttempt.reason ?: gemmaRuntime.localReadinessStatus().reason
                    )
                )
            }

            val fallbackAttempt = if (fallbackRouteDecision.selectedRole == BrainModelRole.MOCK_FALLBACK) {
                null
            } else {
                evaluateProvider(fallbackProvider, request)
            }

            if (isAccepted(fallbackAttempt?.parsedAction)) {
                return rememberBrainAction(request, fallbackRouteDecision, fallbackAttempt!!.parsedAction!!)
            }

            return rememberBrainAction(request, fallbackRouteDecision, fallback(rawText))
        }

        if (routeDecision.selectedRole == BrainModelRole.GEMMA_REASONING) {
            return rememberBrainAction(
                request,
                routeDecision,
                localModelFallbackAction(
                    rawText = rawText,
                    routeDecision = routeDecision,
                    reason = primaryAttempt.reason ?: gemmaRuntime.localReadinessStatus().reason
                )
            )
        }

        val fallbackAttempt = if (routeDecision.selectedRole == BrainModelRole.MOCK_FALLBACK) {
            null
        } else {
            evaluateProvider(fallbackProvider, request)
        }

        if (isAccepted(fallbackAttempt?.parsedAction)) {
            return rememberBrainAction(request, routeDecision, fallbackAttempt!!.parsedAction!!)
        }

        return rememberBrainAction(request, routeDecision, fallback(rawText))
    }

    fun diagnose(
        rawText: String,
        activeCabSession: Boolean = false,
        activeGrocerySession: Boolean = false,
        activeFoodSession: Boolean = false,
        onlineConsentGiven: Boolean = false,
        activeSessionType: BrainSessionType? = null,
        pendingConfirmation: PendingConfirmation? = null,
        screenState: ScreenState? = null
    ): BrainDiagnostics {
        val memorySnapshot = sessionManager.snapshot()
        val effectiveActiveSessionType = activeSessionType ?: memorySnapshot.activeSessionType()
        val effectivePendingConfirmation = pendingConfirmation ?: memorySnapshot.activePendingConfirmation
        val request = BrainRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            screenState = screenState,
            onlineConsentGiven = onlineConsentGiven,
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmation = effectivePendingConfirmation,
            memorySnapshot = memorySnapshot,
            preferences = memorySnapshot.preferences,
            recoveryState = effectiveActiveSessionType?.let { memorySnapshot.recoveryStates[it] }
        )

        val policyDecision = internetPermissionPolicy.classify(rawText)
        val taskPlan = buildTaskPlan(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmation = effectivePendingConfirmation,
            screenState = screenState
        )
        val runtimeState = runtimeBaseStatus(policyDecision).copy(
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
            memoryLoaded = true,
            memorySessionCount = memorySnapshot.activeSessionCount,
            agentLoopCandidate = taskPlan.loopCapable
        )

        when (policyDecision.category) {
            InternetPermissionCategory.BLOCKED_SENSITIVE -> {
                val action = blockedHumanOnlyAction(rawText, policyDecision.reason)
                val onlineTrace = OnlineAiTrace.blocked(
                    providerType = onlineAiProvider.providerType,
                    providerName = onlineAiProvider.diagnostics().ifBlank {
                        onlineAiProvider.providerType.wireValue
                    },
                    decision = OnlineAiPolicyDecision.DENY_SENSITIVE,
                    reason = policyDecision.reason,
                    networkAvailable = internetAvailable
                )
                return BrainDiagnostics(
                    userInput = rawText,
                    activeCabSession = activeCabSession,
                    selectedProvider = runtimeState.selectedProvider,
                    selectedRole = null,
                    routeDecision = null,
                    rawModelResponse = null,
                    extractedBrainActionJson = null,
                    parsedBrainAction = null,
                    modelAvailable = null,
                    validatorResult = true,
                    fallbackUsed = false,
                    finalProvider = runtimeState.selectedProvider,
                    finalBrainAction = action,
                    finalSafetyDecision = safetyGate.evaluate(
                        action,
                        pendingConfirmation = request.pendingConfirmation,
                        userConfirmed = request.onlineConsentGiven
                    ),
                    runtimeStatus = runtimeState.copy(
                        onlineTrace = onlineTrace,
                        activeSessionType = effectiveActiveSessionType,
                        pendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                        memoryLoaded = true,
                        memorySessionCount = memorySnapshot.activeSessionCount
                    ),
                    internetPermissionDecision = policyDecision,
                    onlineTrace = onlineTrace,
                    activeSessionType = effectiveActiveSessionType,
                    pendingConfirmationId = effectivePendingConfirmation?.confirmationId,
                    pendingConfirmationType = effectivePendingConfirmation?.type,
                    recoveryState = request.recoveryState,
                    memorySessionCount = memorySnapshot.activeSessionCount,
                    memoryPendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                    preferences = memorySnapshot.preferences,
                    agentLoopCandidate = taskPlan.loopCapable
                )
            }

            InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO -> {
                if (!internetAvailable) {
                    val action = offlineInfoAction(rawText, policyDecision.reason)
                    val onlineTrace = OnlineAiTrace.blocked(
                        providerType = onlineAiProvider.providerType,
                        providerName = onlineAiProvider.diagnostics().ifBlank {
                            onlineAiProvider.providerType.wireValue
                        },
                        decision = OnlineAiPolicyDecision.DENY_NO_INTERNET,
                        reason = "Online helper needs internet and none is currently available.",
                        networkAvailable = false
                    )
                    return BrainDiagnostics(
                        userInput = rawText,
                        activeCabSession = activeCabSession,
                        selectedProvider = runtimeState.selectedProvider,
                        selectedRole = null,
                        routeDecision = null,
                        rawModelResponse = null,
                        extractedBrainActionJson = null,
                        parsedBrainAction = null,
                        modelAvailable = null,
                        validatorResult = true,
                        fallbackUsed = false,
                        finalProvider = runtimeState.selectedProvider,
                        finalBrainAction = action,
                        finalSafetyDecision = safetyGate.evaluate(
                            action,
                            pendingConfirmation = request.pendingConfirmation,
                            userConfirmed = request.onlineConsentGiven
                        ),
                        runtimeStatus = runtimeState.copy(
                            reason = runtimeReason(policyDecision, fallbackUsed = false, routeDecision = null, finalProvider = runtimeState.selectedProvider),
                            onlineTrace = onlineTrace,
                            activeSessionType = effectiveActiveSessionType,
                            pendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                            memoryLoaded = true,
                            memorySessionCount = memorySnapshot.activeSessionCount
                        ),
                        internetPermissionDecision = policyDecision,
                        onlineTrace = onlineTrace,
                        activeSessionType = effectiveActiveSessionType,
                        pendingConfirmationId = effectivePendingConfirmation?.confirmationId,
                        pendingConfirmationType = effectivePendingConfirmation?.type,
                        recoveryState = request.recoveryState,
                        memorySessionCount = memorySnapshot.activeSessionCount,
                        memoryPendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                        preferences = memorySnapshot.preferences,
                        agentLoopCandidate = taskPlan.loopCapable
                    )
                }
            }

            else -> Unit
        }

        if (provider != null) {
            val primaryAttempt = evaluateProvider(provider, request)
            val primaryAccepted = isAccepted(primaryAttempt.parsedAction)
            val fallbackAttempt = if (primaryAccepted) null else evaluateProvider(fallbackProvider, request)
            val fallbackAccepted = isAccepted(fallbackAttempt?.parsedAction)

            val finalAttempt = when {
                primaryAccepted -> primaryAttempt
                fallbackAccepted && fallbackAttempt != null -> fallbackAttempt
                else -> null
            }

            val finalAction = finalAttempt?.parsedAction ?: fallback(rawText)
            val finalProvider = finalAttempt?.providerName
                ?: fallbackAttempt?.providerName
                ?: providerName(fallbackProvider)
            val fallbackUsed = !primaryAccepted
            val status = runtimeState.copy(
                selectedProvider = finalProvider,
                fallbackActive = fallbackUsed || runtimeState.fallbackActive,
                reason = runtimeReason(policyDecision, fallbackUsed = fallbackUsed, routeDecision = null, finalProvider = finalProvider)
            )

            return BrainDiagnostics(
                userInput = rawText,
                activeCabSession = activeCabSession,
                selectedProvider = primaryAttempt.providerName,
                selectedRole = null,
                routeDecision = null,
                rawModelResponse = primaryAttempt.rawResponse,
                extractedBrainActionJson = primaryAttempt.extractedJson,
                parsedBrainAction = primaryAttempt.parsedAction,
                modelAvailable = null,
                validatorResult = primaryAccepted,
                fallbackUsed = fallbackUsed,
                finalProvider = finalProvider,
                finalBrainAction = finalAction,
                finalSafetyDecision = safetyGate.evaluate(
                    finalAction,
                    pendingConfirmation = request.pendingConfirmation,
                    userConfirmed = request.onlineConsentGiven
                ),
                runtimeStatus = status.copy(
                    activeSessionType = effectiveActiveSessionType,
                    pendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                    memoryLoaded = true,
                    memorySessionCount = memorySnapshot.activeSessionCount
                ),
                internetPermissionDecision = policyDecision,
                onlineTrace = null,
                activeSessionType = effectiveActiveSessionType,
                pendingConfirmationId = effectivePendingConfirmation?.confirmationId,
                pendingConfirmationType = effectivePendingConfirmation?.type,
                recoveryState = request.recoveryState,
                memorySessionCount = memorySnapshot.activeSessionCount,
                memoryPendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                preferences = memorySnapshot.preferences,
                agentLoopCandidate = taskPlan.loopCapable
            )
        }

        val routeDecision = brainRouter.route(request)
        val routedRequest = request.withScreenStateIfNeeded(routeDecision)
        val primaryAttempt = evaluateRouteDecision(routeDecision, routedRequest)
        val primaryAccepted = isAccepted(primaryAttempt.parsedAction)

        val localFallbackDecision = if (routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER && !primaryAccepted) {
            brainRouter.route(request, allowOnlineHelper = false)
        } else {
            null
        }
        val localFallbackAttempt = localFallbackDecision?.let { fallbackDecision ->
            val fallbackRequest = request.withScreenStateIfNeeded(fallbackDecision)
            evaluateRouteDecision(fallbackDecision, fallbackRequest)
        }
        val localFallbackAccepted = isAccepted(localFallbackAttempt?.parsedAction)
        val gemmaReadiness = gemmaRuntime.readinessStatus(
            selectedBrainRole = routeDecision.selectedRole,
            fallbackActive = !primaryAccepted
        )
        val onlineTrace = primaryAttempt.onlineTrace
            ?: localFallbackAttempt?.onlineTrace
            ?: skippedOnlineTrace(
                routeDecision = routeDecision,
                request = request,
                policyDecision = policyDecision
            )

        val finalAction = when {
            primaryAccepted -> primaryAttempt.parsedAction!!
            localFallbackAccepted -> localFallbackAttempt!!.parsedAction!!
            routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER &&
                localFallbackDecision?.selectedRole == BrainModelRole.GEMMA_REASONING &&
                localFallbackAttempt != null ->
                localModelFallbackAction(
                    rawText = rawText,
                    routeDecision = localFallbackDecision,
                    reason = localFallbackAttempt.reason ?: gemmaReadiness.reason
                )

            routeDecision.selectedRole == BrainModelRole.GEMMA_REASONING ->
                localModelFallbackAction(
                    rawText = rawText,
                    routeDecision = routeDecision,
                    reason = primaryAttempt.reason ?: gemmaReadiness.reason
                )

            else -> fallback(rawText)
        }

        val finalProvider = when {
            primaryAccepted -> primaryAttempt.providerName
            localFallbackAccepted -> localFallbackAttempt!!.providerName
            routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER &&
                localFallbackAttempt != null -> localFallbackAttempt.providerName
            routeDecision.selectedRole == BrainModelRole.GEMMA_REASONING ->
                if (primaryAccepted) {
                    primaryAttempt.providerName
                } else {
                    providerName(fallbackProvider)
                }
            else -> providerName(fallbackProvider)
        }
        val fallbackUsed = !primaryAccepted && routeDecision.selectedRole != BrainModelRole.MOCK_FALLBACK
        val gemmaStatusApplies = routeDecision.selectedRole == BrainModelRole.GEMMA_REASONING ||
            (routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER &&
                localFallbackDecision?.selectedRole == BrainModelRole.GEMMA_REASONING)
        val localModelSource = when {
            routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER &&
                localFallbackDecision?.selectedRole == BrainModelRole.GEMMA_REASONING ->
                localFallbackAttempt

            routeDecision.selectedRole == BrainModelRole.GEMMA_REASONING ->
                primaryAttempt

            else -> null
        }
        val status = runtimeState.copy(
            selectedBrainRole = routeDecision.selectedRole,
            modelPathConfigured = if (gemmaStatusApplies) gemmaReadiness.modelPathConfigured else false,
            modelFileExists = if (gemmaStatusApplies) gemmaReadiness.modelFileExists else false,
            runtimeAvailable = if (gemmaStatusApplies) gemmaReadiness.runtimeAvailable else false,
            modelLoaded = if (gemmaStatusApplies) gemmaReadiness.modelLoaded else false,
            selectedLocalModelId = if (gemmaStatusApplies) localModelSource?.localModelId ?: runtimeState.selectedLocalModelId else runtimeState.selectedLocalModelId,
            selectedLocalModelDisplayName = if (gemmaStatusApplies) localModelSource?.localModelDisplayName ?: runtimeState.selectedLocalModelDisplayName else runtimeState.selectedLocalModelDisplayName,
            selectedLocalModelStatus = if (gemmaStatusApplies) localModelSource?.localModelStatus ?: runtimeState.selectedLocalModelStatus else runtimeState.selectedLocalModelStatus,
            selectedLocalModelAssetMissing = if (gemmaStatusApplies) !gemmaReadiness.modelFileExists else runtimeState.selectedLocalModelAssetMissing,
            promptBuilt = if (gemmaStatusApplies) localModelSource?.promptBuilt == true else runtimeState.promptBuilt,
            jsonParseSucceeded = if (gemmaStatusApplies) localModelSource?.jsonParsed == true else runtimeState.jsonParseSucceeded,
            modelLatencyMillis = if (gemmaStatusApplies) localModelSource?.latencyMillis ?: runtimeState.modelLatencyMillis else runtimeState.modelLatencyMillis,
            fallbackActive = fallbackUsed || runtimeState.fallbackActive,
            reason = runtimeReason(
                policyDecision = policyDecision,
                fallbackUsed = fallbackUsed,
                routeDecision = routeDecision,
                finalProvider = finalProvider,
                modelReason = when {
                    routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER && !primaryAttempt.available ->
                        primaryAttempt.reason ?: onlineTrace.reason

                    routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER &&
                        localFallbackDecision?.selectedRole == BrainModelRole.GEMMA_REASONING &&
                        localFallbackAttempt != null ->
                        localFallbackAttempt.reason ?: gemmaReadiness.reason

                    gemmaStatusApplies && !primaryAttempt.available ->
                        primaryAttempt.reason ?: gemmaReadiness.reason

                    gemmaStatusApplies && !primaryAccepted ->
                        "Gemma candidate was rejected by BrainActionValidator."

                    else -> primaryAttempt.reason
                }
            ),
            onlineTrace = onlineTrace
        )

        return BrainDiagnostics(
            userInput = rawText,
            activeCabSession = activeCabSession,
            selectedProvider = primaryAttempt.providerName,
            selectedRole = routeDecision.selectedRole,
            routeDecision = routeDecision,
            rawModelResponse = primaryAttempt.rawResponse,
            extractedBrainActionJson = primaryAttempt.extractedJson,
            parsedBrainAction = primaryAttempt.parsedAction,
            modelAvailable = primaryAttempt.available,
            validatorResult = primaryAccepted,
            fallbackUsed = fallbackUsed,
            finalProvider = finalProvider,
            finalBrainAction = finalAction,
            finalSafetyDecision = safetyGate.evaluate(
                finalAction,
                pendingConfirmation = request.pendingConfirmation,
                userConfirmed = request.onlineConsentGiven
            ),
            runtimeStatus = status.copy(
                activeSessionType = effectiveActiveSessionType,
                pendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
                memoryLoaded = true,
                memorySessionCount = memorySnapshot.activeSessionCount
            ),
            internetPermissionDecision = policyDecision,
            onlineTrace = onlineTrace,
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmationId = effectivePendingConfirmation?.confirmationId,
            pendingConfirmationType = effectivePendingConfirmation?.type,
            recoveryState = request.recoveryState,
            memorySessionCount = memorySnapshot.activeSessionCount,
            memoryPendingConfirmationCount = memorySnapshot.activePendingConfirmationCount,
            preferences = memorySnapshot.preferences,
            agentLoopCandidate = taskPlan.loopCapable
        )
    }

    fun buildTaskPlan(
        rawText: String,
        commandIntent: CommandIntent? = null,
        activeCabSession: Boolean = false,
        activeGrocerySession: Boolean = false,
        activeFoodSession: Boolean = false,
        activeSessionType: BrainSessionType? = null,
        pendingConfirmation: PendingConfirmation? = null,
        screenState: ScreenState? = null
    ): TaskPlan {
        val snapshot = sessionManager.snapshot()
        val effectiveActiveSessionType = activeSessionType ?: snapshot.activeSessionType()
        val effectivePendingConfirmation = pendingConfirmation ?: snapshot.activePendingConfirmation
        val request = BrainRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            screenState = screenState,
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmation = effectivePendingConfirmation,
            memorySnapshot = snapshot,
            preferences = snapshot.preferences,
            recoveryState = effectiveActiveSessionType?.let { snapshot.recoveryStates[it] }
        )
        val routeDecision = brainRouter.route(request)
        val normalized = rawText.lowercase(Locale.US).trim()
        val parsedIntent = commandIntent ?: CommandIntent(rawText = rawText)
        val loopCapable = isLoopEligible(
            normalized = normalized,
            commandIntent = parsedIntent,
            routeDecision = routeDecision,
            screenState = screenState,
            activeSessionType = effectiveActiveSessionType,
            pendingConfirmation = effectivePendingConfirmation
        )

        return TaskPlan(
            goal = rawText,
            loopCapable = loopCapable,
            reason = if (loopCapable) {
                "This looks like a safe multi-step task."
            } else {
                "This is safer and faster as a one-shot command."
            },
            domain = effectiveActiveSessionType,
            requiresScreenContext = routeDecision.requiresScreenContext || normalized.contains("screen"),
            requiresUserConfirmation = requiresUserConfirmation(normalized, parsedIntent, routeDecision),
            allowLoop = true,
            maxSteps = maxStepsFor(parsedIntent, loopCapable),
            maxRetries = if (loopCapable) 1 else 0,
            completionHints = buildCompletionHints(parsedIntent, normalized),
            safetyNotes = routeDecision.safetyNotes
        )
    }

    private fun BrainRequest.withScreenStateIfNeeded(routeDecision: BrainRouteDecision): BrainRequest {
        if (screenState != null) return this
        if (routeDecision.selectedRole != BrainModelRole.SCREEN_UNDERSTANDING &&
            !routeDecision.requiresScreenContext
        ) {
            return this
        }

        val capturedScreenState = screenStateReader.captureScreenState()
        return if (capturedScreenState == null) {
            this
        } else {
            copy(screenState = capturedScreenState)
        }
    }

    private fun isLoopEligible(
        normalized: String,
        commandIntent: CommandIntent,
        routeDecision: BrainRouteDecision,
        screenState: ScreenState?,
        activeSessionType: BrainSessionType?,
        pendingConfirmation: PendingConfirmation?
    ): Boolean {
        if (normalized.isBlank()) return false
        if (screenState?.isSensitiveScreen() == true) return false
        if (pendingConfirmation != null && pendingConfirmation.isExpired()) return false
        if (commandIntent.actionType == com.nova.luna.model.ActionType.STOP_SERVICE) return false

        val multiStepHints = listOf(
            "and then",
            "then ",
            "after that",
            "search ",
            "find ",
            "compare",
            "draft",
            "prepare",
            "read screen",
            "what is on screen",
            "what's on screen",
            "verify",
            "select ",
            "choose ",
            "configure",
            "set up",
            "open app and",
            "launch and",
            "open and",
            "follow up",
            "continue",
            "search for"
        )

        val hasMultiStepCue = multiStepHints.any { normalized.contains(it) }
        val screenDrivenIntent = commandIntent.intentType in setOf(
            IntentType.OPEN_APP,
            IntentType.NAVIGATION,
            IntentType.INTERACTION,
            IntentType.TEXT_ENTRY,
            IntentType.READ_NOTIFICATIONS,
            IntentType.SENSITIVE,
            IntentType.CONTROL,
            IntentType.COMMUNICATION,
            IntentType.CONTENT_CREATION
        )

        val activeSessionSupportsLoop = activeSessionType in setOf(
            BrainSessionType.PHONE,
            BrainSessionType.SCREEN,
            BrainSessionType.BASIC_CONTROL,
            BrainSessionType.ONLINE_HELPER,
            BrainSessionType.LOCAL_LLM
        )

        return hasMultiStepCue &&
            (screenDrivenIntent || routeDecision.selectedRole == BrainModelRole.SCREEN_UNDERSTANDING || activeSessionSupportsLoop)
    }

    private fun requiresUserConfirmation(
        normalized: String,
        commandIntent: CommandIntent,
        routeDecision: BrainRouteDecision
    ): Boolean {
        val confirmationHints = listOf(
            "send",
            "share",
            "follow",
            "subscribe",
            "post",
            "book",
            "order",
            "checkout",
            "pay",
            "final",
            "publish"
        )

        if (routeDecision.selectedRole == BrainModelRole.ONLINE_AI_HELPER) {
            return false
        }

        return commandIntent.intentType in setOf(
            IntentType.COMMUNICATION,
            IntentType.CONTENT_CREATION,
            IntentType.CAB_BOOKING,
            IntentType.FOOD_ORDER,
            IntentType.GROCERY_BOOKING,
            IntentType.SHOPPING
        ) && confirmationHints.any { normalized.contains(it) }
    }

    private fun maxStepsFor(commandIntent: CommandIntent, loopCapable: Boolean): Int {
        if (!loopCapable) return 1
        return when (commandIntent.intentType) {
            IntentType.COMMUNICATION,
            IntentType.CONTENT_CREATION -> 5

            IntentType.OPEN_APP,
            IntentType.NAVIGATION,
            IntentType.INTERACTION,
            IntentType.TEXT_ENTRY,
            IntentType.READ_NOTIFICATIONS,
            IntentType.SENSITIVE,
            IntentType.CONTROL -> 4

            else -> 6
        }
    }

    private fun buildCompletionHints(commandIntent: CommandIntent, normalized: String): List<String> {
        val baseHints = when (commandIntent.intentType) {
            IntentType.OPEN_APP -> listOf("opened", "launched", "ready")
            IntentType.NAVIGATION -> listOf("home", "back", "recents", "notifications")
            IntentType.INTERACTION -> listOf("selected", "tapped", "opened")
            IntentType.TEXT_ENTRY -> listOf("typed", "entered", "filled")
            IntentType.READ_NOTIFICATIONS -> listOf("notification", "message", "alert")
            IntentType.COMMUNICATION -> listOf("draft", "prepared", "reply", "message ready")
            IntentType.CONTENT_CREATION -> listOf("draft", "prepared", "export", "share")
            IntentType.MEDIA_CONTROL -> listOf("playing", "paused", "started", "ready")
            IntentType.SHOPPING -> listOf("cart", "compare", "product", "ready")
            IntentType.CAB_BOOKING,
            IntentType.FOOD_ORDER,
            IntentType.GROCERY_BOOKING -> listOf("options", "ready", "compare")
            IntentType.SENSITIVE -> listOf("screen", "settings", "permission")
            IntentType.CONTROL -> listOf("done", "ready", "complete")
            else -> emptyList()
        }

        val keywordHints = buildList {
            if (normalized.contains("search")) add("search")
            if (normalized.contains("compare")) add("compare")
            if (normalized.contains("draft")) add("draft")
            if (normalized.contains("prepare")) add("prepared")
            if (normalized.contains("screen")) add("screen")
        }

        return (baseHints + keywordHints).distinct()
    }

    private fun evaluateRouteDecision(
        routeDecision: BrainRouteDecision,
        request: BrainRequest
    ): BrainAttempt {
        return when (routeDecision.selectedRole) {
            BrainModelRole.ONLINE_AI_HELPER -> evaluateModel(onlineAiHelper, request, routeDecision)
            BrainModelRole.GEMMA_REASONING -> evaluateModel(gemmaBrainModel, request, routeDecision)
            BrainModelRole.ACTION_JSON -> evaluateModel(actionJsonModel, request, routeDecision)
            BrainModelRole.LITE_COMMAND -> evaluateModel(liteCommandModel, request, routeDecision)
            BrainModelRole.SCREEN_UNDERSTANDING -> evaluateModel(screenUnderstandingModel, request, routeDecision)
            BrainModelRole.MOCK_FALLBACK -> evaluateProvider(fallbackProvider, request)
        }
    }

    private fun evaluateModel(
        model: PhoneBrainModel,
        request: BrainRequest,
        routeDecision: BrainRouteDecision
    ): BrainAttempt {
        val result = runCatching { model.generate(request, routeDecision) }.getOrElse {
            BrainModelResult.unavailable(
                role = model.role,
                reason = "Model execution failed: ${it.message.orEmpty()}",
                safetyNotes = routeDecision.safetyNotes
            )
        }

        return BrainAttempt(
            providerName = modelName(model),
            rawResponse = result.rawResponse,
            extractedJson = result.rawResponse,
            parsedAction = result.candidateAction,
            available = result.available,
            reason = result.reason,
            localModelId = result.localModelId?.wireValue,
            localModelDisplayName = result.localModelDisplayName,
            localModelStatus = result.localModelStatus?.wireValue,
            promptBuilt = result.promptBuilt,
            jsonParsed = result.jsonParsed,
            latencyMillis = result.latencyMillis,
            onlineTrace = result.onlineTrace
        )
    }

    private fun evaluateProvider(provider: BrainProvider, request: BrainRequest): BrainAttempt {
        val diagnostics = (provider as? BrainProviderDiagnostics)?.diagnose(request)
        if (diagnostics != null) {
            return BrainAttempt(
                providerName = diagnostics.providerName,
                rawResponse = diagnostics.rawResponse,
                extractedJson = diagnostics.extractedJson,
                parsedAction = diagnostics.parsedAction,
                available = diagnostics.parsedAction != null,
                reason = diagnostics.error
            )
        }

        val json = runCatching { provider.analyze(request) }.getOrNull()
        val action = json?.let(codec::decode)
        return BrainAttempt(
            providerName = providerName(provider),
            rawResponse = json,
            extractedJson = json,
            parsedAction = action,
            available = action != null,
            reason = if (action == null) "invalid_or_rejected_output" else null
        )
    }

    private fun isAccepted(action: BrainAction?): Boolean {
        return action != null && validator.isAcceptable(action)
    }

    private fun providerName(provider: BrainProvider): String {
        return provider::class.java.simpleName.takeIf { it.isNotBlank() }
            ?: provider::class.qualifiedName.orEmpty()
    }

    private fun modelName(model: PhoneBrainModel): String {
        return model::class.java.simpleName.takeIf { it.isNotBlank() }
            ?: model::class.qualifiedName.orEmpty()
    }

    private fun skippedOnlineTrace(
        routeDecision: BrainRouteDecision,
        request: BrainRequest,
        policyDecision: InternetPermissionDecision
    ): OnlineAiTrace {
        val providerType = onlineAiProvider.providerType
        val providerName = onlineAiProvider.diagnostics().ifBlank {
            onlineAiProvider.providerType.wireValue
        }
        return OnlineAiTrace.skipped(
            providerType = providerType,
            providerName = providerName,
            reason = when {
                routeDecision.selectedRole == BrainModelRole.MOCK_FALLBACK ->
                    "Online helper was not needed because the request stayed on the fallback path."

                policyDecision.category == InternetPermissionCategory.BLOCKED_SENSITIVE ->
                    "Online helper was skipped because the request is sensitive and must stay local."

                policyDecision.category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO && !internetAvailable ->
                    "Online helper was skipped because internet is unavailable."

                else ->
                    "Online helper was skipped because a local model handled the request instead."
            },
            networkAvailable = internetAvailable,
            userConsentGiven = request.onlineConsentGiven
        )
    }

    private fun runtimeBaseStatus(policyDecision: InternetPermissionDecision): BrainRuntimeStatus {
        val localReadiness = gemmaRuntime.localReadinessStatus()
        val baseStatus = if (provider != null) {
            BrainRuntimeStatus(
                selectedProvider = providerName(provider),
                capabilityMode = runtimeConfig.capabilityMode,
                internetAvailable = internetAvailable,
                localModelAvailable = runtimeConfig.useLocalLlm(),
                fallbackActive = false,
                reason = runtimeReason(policyDecision, fallbackUsed = false, routeDecision = null, finalProvider = providerName(provider)),
                safetyChainActive = true,
                selectedLocalModelId = localReadiness.selectedModelId?.wireValue,
                selectedLocalModelDisplayName = localReadiness.selectedModelDisplayName,
                selectedLocalModelStatus = localReadiness.status.wireValue,
                selectedLocalModelAssetMissing = localReadiness.assetMissing
            )
        } else {
            runtimeSelection.runtimeStatus.copy(
                reason = runtimeReason(policyDecision, fallbackUsed = false, routeDecision = null, finalProvider = runtimeSelection.runtimeStatus.selectedProvider),
                selectedLocalModelId = localReadiness.selectedModelId?.wireValue,
                selectedLocalModelDisplayName = localReadiness.selectedModelDisplayName,
                selectedLocalModelStatus = localReadiness.status.wireValue,
                selectedLocalModelAssetMissing = localReadiness.assetMissing
            )
        }

        return baseStatus
    }

    private fun runtimeReason(
        policyDecision: InternetPermissionDecision,
        fallbackUsed: Boolean,
        routeDecision: BrainRouteDecision?,
        finalProvider: String,
        modelReason: String? = null
    ): String {
        return when {
            policyDecision.category == InternetPermissionCategory.BLOCKED_SENSITIVE ->
                "Blocked by InternetPermissionPolicy before provider execution."

            policyDecision.category == InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO && !internetAvailable ->
                "Offline limitation: internet is required for this information lookup."

            fallbackUsed && routeDecision != null ->
                buildString {
                    append("Route ")
                    append(routeDecision.selectedRole.wireValue)
                    append(" fell back to ")
                    append(finalProvider)
                    append(" after the selected phone model was rejected or unavailable.")
                    modelReason?.takeIf { it.isNotBlank() }?.let {
                        append(' ')
                        append(it)
                    }
                }

            fallbackUsed ->
                buildString {
                    append("Primary provider response was rejected, so the local mock fallback was used.")
                    modelReason?.takeIf { it.isNotBlank() }?.let {
                        append(' ')
                        append(it)
                    }
                }

            provider != null ->
                "Explicit provider override is active."

            routeDecision != null ->
                "Phone-only routing selected ${routeDecision.selectedRole.wireValue}."

            else ->
                runtimeSelection.runtimeStatus.reason
        }
    }

    private fun localModelFallbackAction(
        rawText: String,
        routeDecision: BrainRouteDecision,
        reason: String
    ): BrainAction {
        val readiness = gemmaRuntime.localReadinessStatus()
        return BrainAction(
            intent = "local_model_unavailable",
            reply = "Local AI model is not ready yet, but I can still handle basic commands.",
            actionType = BrainActionType.NONE,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = buildMap {
                put("rawText", rawText)
                put("routeRole", routeDecision.selectedRole.wireValue)
                put("routeReason", routeDecision.reason)
                put("localModelStatus", readiness.status.wireValue)
                put("localModelAvailable", readiness.available.toString())
                put("runtimeAvailable", readiness.runtimeAvailable.toString())
                put("assetMissing", readiness.assetMissing.toString())
                readiness.selectedModelId?.wireValue?.let { put("localModelId", it) }
                readiness.selectedModelDisplayName?.takeIf { it.isNotBlank() }?.let { put("localModelDisplayName", it) }
                put("reason", reason)
            },
            nextQuestion = "Try a simple command like open app, home, or stop listening."
        )
    }

    private fun blockedHumanOnlyAction(rawText: String, reason: String): BrainAction {
        return BrainAction(
            intent = "human_only",
            reply = reason,
            actionType = BrainActionType.HUMAN_ONLY,
            riskLevel = BrainRiskLevel.BLOCKED,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to rawText,
                "reason" to reason
            ),
            nextQuestion = "Please handle that manually."
        )
    }

    private fun offlineInfoAction(rawText: String, reason: String): BrainAction {
        return BrainAction(
            intent = "internet_unavailable",
            reply = "I need internet for live information. I can still help with offline-only actions.",
            actionType = BrainActionType.NONE,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to rawText,
                "reason" to reason
            ),
            nextQuestion = "Would you like an offline-only alternative?"
        )
    }

    private fun rememberBrainAction(
        request: BrainRequest,
        routeDecision: BrainRouteDecision?,
        brainAction: BrainAction
    ): BrainAction {
        val safetyDecision = safetyGate.evaluate(
            brainAction,
            pendingConfirmation = request.pendingConfirmation,
            userConfirmed = request.onlineConsentGiven
        )
        sessionManager.recordBrainAction(
            request = request,
            routeDecision = routeDecision,
            brainAction = brainAction,
            safetyDecision = safetyDecision
        )
        return brainAction
    }

    private fun fallback(rawText: String): BrainAction {
        return BrainAction(
            intent = "unknown",
            reply = "I did not quite catch that. Please try again.",
            actionType = BrainActionType.NONE,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to rawText),
            nextQuestion = "What would you like me to do?"
        )
    }

    private data class BrainAttempt(
        val providerName: String,
        val rawResponse: String?,
        val extractedJson: String?,
        val parsedAction: BrainAction?,
        val available: Boolean,
        val reason: String? = null,
        val localModelId: String? = null,
        val localModelDisplayName: String? = null,
        val localModelStatus: String? = null,
        val promptBuilt: Boolean = false,
        val jsonParsed: Boolean = false,
        val latencyMillis: Long? = null,
        val onlineTrace: OnlineAiTrace? = null
    )
}
