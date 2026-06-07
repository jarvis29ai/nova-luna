package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.BrainRuntimeStatus
import com.nova.luna.model.InternetPermissionCategory
import com.nova.luna.model.InternetPermissionDecision
import com.nova.luna.safety.SafetyGate

class BrainService(
    private val provider: BrainProvider? = null,
    private val fallbackProvider: BrainProvider = LocalMockBrainProvider(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec(),
    private val validator: BrainActionValidator = BrainActionValidator(),
    private val safetyGate: SafetyGate = SafetyGate(),
    private val runtimeConfig: BrainRuntimeConfig = BrainRuntimeConfig.fromBuildConfig(),
    private val internetPermissionPolicy: InternetPermissionPolicy = InternetPermissionPolicy(),
    private val internetAvailable: Boolean = false,
    private val phoneBrainProvider: PhoneBrainProvider = UnavailablePhoneBrainProvider(),
    private val ollamaClient: OllamaClient = HttpOllamaClient(),
    private val brainRouter: BrainRouter = BrainRouter(),
    private val gemmaRuntime: PhoneGemmaRuntime = PhoneGemmaRuntime(),
    private val gemmaBrainModel: PhoneBrainModel = GemmaBrainModel(gemmaRuntime),
    private val actionJsonModel: PhoneBrainModel = ActionJsonModel(),
    private val liteCommandModel: PhoneBrainModel = LiteCommandModel(),
    private val screenUnderstandingModel: PhoneBrainModel = ScreenUnderstandingModel()
) {
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
        activeFoodSession: Boolean = false
    ): BrainAction {
        val request = BrainRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession
        )

        val policyDecision = internetPermissionPolicy.classify(rawText)
        when (policyDecision.category) {
            InternetPermissionCategory.BLOCKED_SENSITIVE -> {
                return blockedHumanOnlyAction(rawText, policyDecision.reason)
            }

            InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO -> {
                if (!internetAvailable) {
                    return offlineInfoAction(rawText, policyDecision.reason)
                }
            }

            else -> Unit
        }

        if (provider != null) {
            val primaryAttempt = evaluateProvider(provider, request)
            if (isAccepted(primaryAttempt.parsedAction)) {
                return primaryAttempt.parsedAction!!
            }

            val fallbackAttempt = evaluateProvider(fallbackProvider, request)
            if (isAccepted(fallbackAttempt.parsedAction)) {
                return fallbackAttempt.parsedAction!!
            }

            return fallback(rawText)
        }

        val routeDecision = brainRouter.route(request)
        val primaryAttempt = evaluateRouteDecision(routeDecision, request)
        if (isAccepted(primaryAttempt.parsedAction)) {
            return primaryAttempt.parsedAction!!
        }

        val fallbackAttempt = if (routeDecision.selectedRole == BrainModelRole.MOCK_FALLBACK) {
            null
        } else {
            evaluateProvider(fallbackProvider, request)
        }

        if (isAccepted(fallbackAttempt?.parsedAction)) {
            return fallbackAttempt!!.parsedAction!!
        }

        return fallback(rawText)
    }

    fun diagnose(
        rawText: String,
        activeCabSession: Boolean = false,
        activeGrocerySession: Boolean = false,
        activeFoodSession: Boolean = false
    ): BrainDiagnostics {
        val request = BrainRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession
        )

        val policyDecision = internetPermissionPolicy.classify(rawText)
        val runtimeState = runtimeBaseStatus(policyDecision)

        when (policyDecision.category) {
            InternetPermissionCategory.BLOCKED_SENSITIVE -> {
                val action = blockedHumanOnlyAction(rawText, policyDecision.reason)
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
                    finalSafetyDecision = safetyGate.evaluate(action, userConfirmed = false),
                    runtimeStatus = runtimeState,
                    internetPermissionDecision = policyDecision
                )
            }

            InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO -> {
                if (!internetAvailable) {
                    val action = offlineInfoAction(rawText, policyDecision.reason)
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
                        finalSafetyDecision = safetyGate.evaluate(action, userConfirmed = false),
                        runtimeStatus = runtimeState.copy(
                            reason = runtimeReason(policyDecision, fallbackUsed = false, routeDecision = null, finalProvider = runtimeState.selectedProvider)
                        ),
                        internetPermissionDecision = policyDecision
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
                finalSafetyDecision = safetyGate.evaluate(finalAction, userConfirmed = false),
                runtimeStatus = status,
                internetPermissionDecision = policyDecision
            )
        }

        val routeDecision = brainRouter.route(request)
        val primaryAttempt = evaluateRouteDecision(routeDecision, request)
        val primaryAccepted = isAccepted(primaryAttempt.parsedAction)
        val fallbackAttempt = if (primaryAccepted || routeDecision.selectedRole == BrainModelRole.MOCK_FALLBACK) {
            null
        } else {
            evaluateProvider(fallbackProvider, request)
        }
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
        val fallbackUsed = !primaryAccepted && routeDecision.selectedRole != BrainModelRole.MOCK_FALLBACK
        val gemmaReadiness = gemmaRuntime.readinessStatus(
            selectedBrainRole = routeDecision.selectedRole,
            fallbackActive = fallbackUsed
        )
        val gemmaStatusApplies = routeDecision.selectedRole == BrainModelRole.GEMMA_REASONING
        val status = runtimeState.copy(
            selectedBrainRole = routeDecision.selectedRole,
            modelPathConfigured = if (gemmaStatusApplies) gemmaReadiness.modelPathConfigured else false,
            modelFileExists = if (gemmaStatusApplies) gemmaReadiness.modelFileExists else false,
            runtimeAvailable = if (gemmaStatusApplies) gemmaReadiness.runtimeAvailable else false,
            modelLoaded = if (gemmaStatusApplies) gemmaReadiness.modelLoaded else false,
            fallbackActive = fallbackUsed || runtimeState.fallbackActive,
            reason = runtimeReason(
                policyDecision = policyDecision,
                fallbackUsed = fallbackUsed,
                routeDecision = routeDecision,
                finalProvider = finalProvider,
                modelReason = when {
                    gemmaStatusApplies && !primaryAttempt.available -> primaryAttempt.reason ?: gemmaReadiness.reason
                    gemmaStatusApplies && !primaryAccepted -> "Gemma candidate was rejected by BrainActionValidator."
                    else -> primaryAttempt.reason
                }
            )
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
            finalSafetyDecision = safetyGate.evaluate(finalAction, userConfirmed = false),
            runtimeStatus = status,
            internetPermissionDecision = policyDecision
        )
    }

    private fun evaluateRouteDecision(
        routeDecision: BrainRouteDecision,
        request: BrainRequest
    ): BrainAttempt {
        return when (routeDecision.selectedRole) {
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
            reason = result.reason
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

    private fun runtimeBaseStatus(policyDecision: InternetPermissionDecision): BrainRuntimeStatus {
        val baseStatus = if (provider != null) {
            BrainRuntimeStatus(
                selectedProvider = providerName(provider),
                capabilityMode = runtimeConfig.capabilityMode,
                internetAvailable = internetAvailable,
                localModelAvailable = runtimeConfig.useLocalLlm(),
                fallbackActive = false,
                reason = runtimeReason(policyDecision, fallbackUsed = false, routeDecision = null, finalProvider = providerName(provider)),
                safetyChainActive = true
            )
        } else {
            runtimeSelection.runtimeStatus.copy(
                reason = runtimeReason(policyDecision, fallbackUsed = false, routeDecision = null, finalProvider = runtimeSelection.runtimeStatus.selectedProvider)
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
        val reason: String? = null
    )
}
