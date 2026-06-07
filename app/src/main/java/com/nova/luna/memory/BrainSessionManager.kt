package com.nova.luna.memory

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.model.SafetyDecision
import com.nova.luna.screen.ScreenState
import java.util.UUID

class BrainSessionManager(
    private val store: BrainMemoryStore = InMemoryBrainMemoryStore(),
    private val redactor: MemoryRedactor = MemoryRedactor(),
    private val confirmationResolver: ConfirmationResolver = ConfirmationResolver(),
    private val followUpResolver: FollowUpResolver = FollowUpResolver(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun snapshot(): BrainMemorySnapshot = store.snapshot()

    fun replace(snapshot: BrainMemorySnapshot) {
        store.replace(snapshot)
    }

    fun update(transform: (BrainMemorySnapshot) -> BrainMemorySnapshot): BrainMemorySnapshot {
        return store.update(transform)
    }

    fun getPreferences(): LocalUserPreferences = store.getPreferences()

    fun setPreferences(preferences: LocalUserPreferences) {
        store.setPreferences(preferences)
    }

    fun updatePreferences(transform: (LocalUserPreferences) -> LocalUserPreferences): LocalUserPreferences {
        return store.updatePreferences(transform)
    }

    fun activePendingConfirmation(): PendingConfirmation? = snapshot().activePendingConfirmation

    fun activeSessionType(): BrainSessionType? = snapshot().activeSessionType(defaultPriority())

    fun activeSession(sessionType: BrainSessionType): BrainSession? = snapshot().sessions[sessionType]

    fun resolvePendingConfirmation(rawText: String): ConfirmationResolution? {
        return confirmationResolver.resolve(rawText, activePendingConfirmation())
    }

    fun resolveFollowUp(rawText: String, preferredSessionType: BrainSessionType? = null): FollowUpResolution? {
        return followUpResolver.resolve(rawText, snapshot(), preferredSessionType)
    }

    fun queueConfirmation(
        confirmation: PendingConfirmation
    ): PendingConfirmation {
        return update { current ->
            val filtered = current.pendingConfirmations.filterNot { it.confirmationId == confirmation.confirmationId }
            current.copy(
                pendingConfirmations = filtered + confirmation,
                updatedAtMillis = clock()
            )
        }.pendingConfirmations.last { it.confirmationId == confirmation.confirmationId }
    }

    fun queueConfirmation(
        sessionType: BrainSessionType,
        actionSummary: String,
        type: PendingConfirmationType,
        rawText: String,
        brainAction: BrainAction? = null,
        sessionId: String? = null,
        confirmationPhraseExpected: String = "yes",
        denialPhraseExpected: String = "no",
        requiresBiometric: Boolean = false,
        requiresManualHandoff: Boolean = false,
        metadata: Map<String, String> = emptyMap()
    ): PendingConfirmation {
        val confirmationId = buildConfirmationId(sessionType, type, rawText, sessionId, brainAction)
        val confirmation = PendingConfirmation(
            confirmationId = confirmationId,
            type = type,
            sessionId = sessionId,
            sessionType = sessionType,
            createdAtMillis = clock(),
            expiresAtMillis = clock() + confirmationTtlMillis,
            userFacingSummary = redactor.redactTextForMemory(actionSummary),
            actionSummary = redactor.redactTextForMemory(actionSummary),
            riskLevel = brainAction?.riskLevel ?: BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresBiometric = requiresBiometric,
            requiresManualHandoff = requiresManualHandoff,
            brainAction = brainAction,
            sanitizedMetadata = metadata + buildMap {
                put("rawText", redactor.redactTextForMemory(rawText))
                put("sessionType", sessionType.wireValue)
                sessionId?.takeIf { it.isNotBlank() }?.let { put("sessionId", it) }
                brainAction?.intent?.takeIf { it.isNotBlank() }?.let { put("brainActionIntent", it) }
            },
            confirmationPhraseExpected = confirmationPhraseExpected,
            denialPhraseExpected = denialPhraseExpected
        )
        return queueConfirmation(confirmation)
    }

    fun consumePendingConfirmation(confirmationId: String? = null): PendingConfirmation? {
        val candidate = confirmationId?.let { id ->
            snapshot().pendingConfirmations.firstOrNull { it.confirmationId == id && it.isPending(clock()) }
        } ?: activePendingConfirmation()

        if (candidate == null) {
            return null
        }

        update { current ->
            current.copy(
                pendingConfirmations = current.pendingConfirmations.map { pending ->
                    if (pending.confirmationId == candidate.confirmationId) pending.consume(clock()) else pending
                },
                updatedAtMillis = clock()
            )
        }

        return candidate.consume(clock())
    }

    fun clearPendingConfirmations(sessionType: BrainSessionType? = null) {
        update { current ->
            val pending = if (sessionType == null) {
                emptyList()
            } else {
                current.pendingConfirmations.filterNot { it.sessionType == sessionType }
            }
            current.copy(
                pendingConfirmations = pending,
                updatedAtMillis = clock()
            )
        }
    }

    fun rememberScreenState(
        screenState: ScreenState,
        sessionId: String? = null
    ): ScreenMemorySnapshot {
        val snapshot = redactor.redactScreenState(screenState).copy(sessionId = sessionId)
        update { current ->
            current.copy(
                screenSnapshots = current.screenSnapshots + (snapshot.snapshotId to snapshot),
                updatedAtMillis = clock()
            )
        }
        return snapshot
    }

    fun startSession(
        sessionType: BrainSessionType,
        sourceCommand: String,
        normalizedGoal: String,
        selectedAppProvider: String? = null,
        currentStep: String? = null,
        pendingUserInputType: String? = null,
        selectedOption: String? = null,
        metadata: Map<String, String> = emptyMap(),
        expiresInMillis: Long? = defaultSessionTtlMillis
    ): BrainSession {
        val now = clock()
        val session = BrainSession(
            sessionId = metadata["sessionId"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            sessionType = sessionType,
            status = BrainSessionStatus.ACTIVE,
            startedAtMillis = now,
            updatedAtMillis = now,
            expiresAtMillis = expiresInMillis?.let { now + it },
            sourceCommand = redactor.redactTextForMemory(sourceCommand),
            normalizedGoal = redactor.redactTextForMemory(normalizedGoal),
            activeDomain = sessionType,
            selectedAppProvider = selectedAppProvider,
            currentStep = currentStep,
            lastSafeAction = null,
            lastSafeResult = null,
            lastSpokenReply = null,
            lastOptionsShown = emptyList(),
            selectedOption = selectedOption,
            pendingUserInputType = pendingUserInputType,
            retryCount = 0,
            recoveryState = null,
            safeScreenSummaryId = null,
            metadata = redactor.redactMetadata(metadata)
        )
        return upsertSession(sessionType, session)
    }

    fun updateSession(
        sessionType: BrainSessionType,
        transform: (BrainSession?) -> BrainSession
    ): BrainSession {
        return upsertSession(sessionType, transform(snapshot().sessions[sessionType]))
    }

    fun completeSession(
        sessionType: BrainSessionType,
        reason: String? = null
    ): BrainSession? {
        val current = snapshot().sessions[sessionType] ?: return null
        val updated = current.copy(
            status = BrainSessionStatus.COMPLETED,
            updatedAtMillis = clock(),
            lastSafeResult = reason?.let { redactor.redactTextForMemory(it) } ?: current.lastSafeResult
        )
        return upsertSession(sessionType, updated)
    }

    fun cancelSession(
        sessionType: BrainSessionType,
        reason: String? = null
    ): BrainSession? {
        val current = snapshot().sessions[sessionType] ?: return null
        val updated = current.copy(
            status = BrainSessionStatus.CANCELLED,
            updatedAtMillis = clock(),
            lastSafeResult = reason?.let { redactor.redactTextForMemory(it) } ?: current.lastSafeResult
        )
        return upsertSession(sessionType, updated)
    }

    fun recordRecovery(
        sessionType: BrainSessionType,
        failureReason: String,
        suggestion: String? = null,
        canRetry: Boolean = true
    ): RecoveryState {
        val state = buildRecoveryState(
            sessionType = sessionType,
            failureReason = failureReason,
            suggestion = suggestion,
            canRetry = canRetry
        )
        updateRecoveryState(sessionType, state)
        return state
    }

    fun clearRecovery(sessionType: BrainSessionType) {
        update { current ->
            current.copy(
                recoveryStates = current.recoveryStates - sessionType,
                updatedAtMillis = clock()
            )
        }
    }

    fun recordBrainAction(
        request: com.nova.luna.brain.BrainRequest,
        routeDecision: BrainRouteDecision?,
        brainAction: BrainAction,
        safetyDecision: SafetyDecision? = null
    ): BrainMemorySnapshot {
        val sessionType = request.activeSessionType
            ?: routeDecision?.selectedRole?.toBrainSessionType()
            ?: brainAction.toBrainSessionType()

        if (request.screenState != null) {
            rememberScreenState(request.screenState, sessionType?.wireValue)
        }

        if (sessionType != null) {
            val recoveryState = if (
                brainAction.actionType == BrainActionType.HUMAN_ONLY ||
                brainAction.riskLevel == BrainRiskLevel.BLOCKED
            ) {
                buildRecoveryState(
                    sessionType = sessionType,
                    failureReason = brainAction.reply.ifBlank { "Brain action failed." },
                    suggestion = brainAction.nextQuestion,
                    canRetry = brainAction.actionType != BrainActionType.HUMAN_ONLY
                )
            } else {
                null
            }

            upsertSession(sessionType, transform = { current ->
                val now = clock()
                val status = sessionStatusForBrainAction(brainAction, safetyDecision)
                val sessionId = current?.sessionId ?: UUID.randomUUID().toString()
                val base = current ?: BrainSession(
                    sessionId = sessionId,
                    sessionType = sessionType,
                    status = status,
                    startedAtMillis = now,
                    updatedAtMillis = now,
                    sourceCommand = redactor.redactTextForMemory(request.rawText),
                    normalizedGoal = redactor.redactTextForMemory(request.rawText),
                    activeDomain = sessionType
                )

                base.copy(
                    status = status,
                    updatedAtMillis = now,
                    currentStep = brainAction.intent,
                    lastSafeAction = redactor.redactTextForMemory(brainAction.reply),
                    lastSafeResult = redactor.redactTextForMemory(brainAction.reply),
                    lastSpokenReply = redactor.redactTextForMemory(brainAction.reply),
                    selectedOption = base.selectedOption ?: extractSelectedOption(brainAction.params),
                    pendingUserInputType = if (status == BrainSessionStatus.WAITING_FOR_CONFIRMATION) "confirmation" else base.pendingUserInputType,
                    recoveryState = recoveryState,
                    metadata = base.metadata + buildMap {
                        put("brainIntent", brainAction.intent)
                        put("brainActionType", brainAction.actionType.wireValue)
                        put("brainRiskLevel", brainAction.riskLevel.wireValue)
                        put("rawText", redactor.redactTextForMemory(request.rawText))
                    }
                )
            })

            if (recoveryState != null) {
                updateRecoveryState(sessionType, recoveryState)
            } else {
                clearRecovery(sessionType)
            }
        }

        val shouldQueueConfirmation = safetyDecision?.requiresConfirmation == true ||
            brainAction.requiresConfirmation ||
            brainAction.actionType == BrainActionType.PREPARE

        if (shouldQueueConfirmation && sessionType != null) {
            queueConfirmation(
                sessionType = sessionType,
                actionSummary = brainAction.nextQuestion ?: brainAction.reply,
                type = pendingConfirmationTypeForBrainAction(brainAction),
                rawText = request.rawText,
                brainAction = brainAction,
                metadata = buildMap {
                    put("source", "brain_action")
                    put("routeRole", routeDecision?.selectedRole?.wireValue.orEmpty())
                    routeDecision?.reason?.takeIf { it.isNotBlank() }?.let { put("routeReason", redactor.redactTextForMemory(it)) }
                }
            )
        }

        return snapshot()
    }

    fun recordCommandResult(
        rawText: String,
        commandIntent: CommandIntent,
        result: CommandResult,
        sessionTypeHint: BrainSessionType? = null,
        screenState: ScreenState? = null
    ): BrainMemorySnapshot {
        val sessionType = sessionTypeHint
            ?: result.memorySessionType
            ?: sessionTypeForCommandIntent(commandIntent, result)

        if (screenState != null) {
            rememberScreenState(screenState, sessionType?.wireValue)
        }

        if (sessionType != null) {
            val recoveryState = if (result.success) {
                null
            } else {
                buildRecoveryState(
                    sessionType = sessionType,
                    failureReason = result.message,
                    suggestion = result.entities["nextQuestion"] ?: result.entities["suggestion"],
                    canRetry = !result.safetyDecision.humanRequired
                )
            }

            upsertSession(sessionType, transform = { current ->
                val now = clock()
                val status = sessionStatusForCommandResult(result)
                val sessionId = current?.sessionId ?: result.memorySessionId ?: UUID.randomUUID().toString()
                val base = current ?: BrainSession(
                    sessionId = sessionId,
                    sessionType = sessionType,
                    status = status,
                    startedAtMillis = now,
                    updatedAtMillis = now,
                    sourceCommand = redactor.redactTextForMemory(rawText),
                    normalizedGoal = redactor.redactTextForMemory(commandIntent.normalizedText),
                    activeDomain = sessionType
                )

                base.copy(
                    status = status,
                    updatedAtMillis = now,
                    currentStep = result.entities["currentState"] ?: result.entities["bookingStatus"] ?: result.entities["status"] ?: base.currentStep,
                    lastSafeAction = redactor.redactTextForMemory(result.message),
                    lastSafeResult = redactor.redactTextForMemory(result.message),
                    lastSpokenReply = redactor.redactTextForMemory(result.message),
                    lastOptionsShown = collectVisibleOptions(result),
                    selectedOption = result.entities["selectedOption"]
                        ?: result.entities["selectedProvider"]
                        ?: result.entities["selectedApp"]
                        ?: result.entities["selectedCandidate"]
                        ?: base.selectedOption,
                    pendingUserInputType = if (result.awaitingConfirmation) "confirmation" else base.pendingUserInputType,
                    retryCount = if (result.success) 0 else base.retryCount + 1,
                    recoveryState = recoveryState,
                    safeScreenSummaryId = result.screenMemorySnapshotId ?: base.safeScreenSummaryId,
                    metadata = base.metadata + buildMap {
                        put("resultIntentType", result.intentType.name)
                        put("resultActionType", result.actionType.name)
                        put("resultSuccess", result.success.toString())
                        put("rawText", redactor.redactTextForMemory(rawText))
                        result.memoryMetadata.forEach { (key, value) ->
                            put(key, redactor.redactTextForMemory(value))
                        }
                    }
                )
            })

            if (recoveryState != null) {
                updateRecoveryState(sessionType, recoveryState)
            } else {
                clearRecovery(sessionType)
            }
        }

        val shouldQueueConfirmation = result.awaitingConfirmation ||
            result.safetyDecision.requiresConfirmation ||
            result.pendingConfirmationType != null

        if (shouldQueueConfirmation && sessionType != null) {
            queueConfirmation(
                sessionType = sessionType,
                actionSummary = result.message,
                type = result.pendingConfirmationType ?: pendingConfirmationTypeForCommandResult(result),
                rawText = rawText,
                sessionId = result.memorySessionId,
                metadata = buildMap {
                    put("source", "command_result")
                    put("intentType", result.intentType.name)
                    put("actionType", result.actionType.name)
                    put("awaitingConfirmation", result.awaitingConfirmation.toString())
                }
            )
        }

        return snapshot()
    }

    private fun upsertSession(
        sessionType: BrainSessionType,
        session: BrainSession
    ): BrainSession {
        update { current ->
            current.copy(
                sessions = current.sessions + (sessionType to session),
                updatedAtMillis = clock()
            )
        }
        return snapshot().sessions[sessionType] ?: session
    }

    private fun upsertSession(
        sessionType: BrainSessionType,
        transform: (BrainSession?) -> BrainSession
    ): BrainSession {
        return upsertSession(sessionType, transform(snapshot().sessions[sessionType]))
    }

    private fun updateRecoveryState(sessionType: BrainSessionType, state: RecoveryState) {
        update { current ->
            current.copy(
                recoveryStates = current.recoveryStates + (sessionType to state),
                updatedAtMillis = clock()
            )
        }
    }

    private fun sessionStatusForBrainAction(
        brainAction: BrainAction,
        safetyDecision: SafetyDecision?
    ): BrainSessionStatus {
        if (brainAction.actionType == BrainActionType.HUMAN_ONLY || brainAction.riskLevel == BrainRiskLevel.BLOCKED) {
            return BrainSessionStatus.BLOCKED
        }

        if (safetyDecision?.requiresConfirmation == true ||
            brainAction.requiresConfirmation ||
            brainAction.actionType == BrainActionType.PREPARE
        ) {
            return BrainSessionStatus.WAITING_FOR_CONFIRMATION
        }

        return if (brainAction.actionType == BrainActionType.READ_ONLY) {
            BrainSessionStatus.WAITING_FOR_USER
        } else {
            BrainSessionStatus.ACTIVE
        }
    }

    private fun sessionStatusForCommandResult(result: CommandResult): BrainSessionStatus {
        val entityStatus = result.entities["currentState"]
            ?: result.entities["bookingStatus"]
            ?: result.entities["status"]

        if (!entityStatus.isNullOrBlank()) {
            BrainSessionStatus.fromWireValue(entityStatus)?.let { return it }
        }

        return when {
            result.awaitingConfirmation || result.safetyDecision.requiresConfirmation -> BrainSessionStatus.WAITING_FOR_CONFIRMATION
            !result.success && result.safetyDecision.humanRequired -> BrainSessionStatus.BLOCKED
            !result.success -> BrainSessionStatus.FAILED
            result.shouldStopListening -> BrainSessionStatus.COMPLETED
            else -> BrainSessionStatus.ACTIVE
        }
    }

    private fun pendingConfirmationTypeForBrainAction(brainAction: BrainAction): PendingConfirmationType {
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

    private fun pendingConfirmationTypeForCommandResult(result: CommandResult): PendingConfirmationType {
        return when {
            result.pendingConfirmationType != null -> result.pendingConfirmationType
            result.intentType == IntentType.CAB_BOOKING -> PendingConfirmationType.BOOK_RIDE
            result.intentType == IntentType.FOOD_ORDER -> PendingConfirmationType.PLACE_ORDER
            result.intentType == IntentType.GROCERY_BOOKING -> PendingConfirmationType.PLACE_ORDER
            result.intentType == IntentType.SHOPPING -> PendingConfirmationType.PLACE_ORDER
            result.intentType == IntentType.COMMUNICATION -> PendingConfirmationType.SEND_MESSAGE
            result.intentType == IntentType.CONTENT_CREATION -> PendingConfirmationType.EXPORT_CONTENT
            else -> PendingConfirmationType.GENERIC_SAFE_ACTION
        }
    }

    private fun sessionTypeForCommandIntent(
        commandIntent: CommandIntent,
        result: CommandResult
    ): BrainSessionType? {
        result.memorySessionType?.let { return it }

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
            commandIntent.intentType == IntentType.UNKNOWN && result.awaitingConfirmation -> result.pendingConfirmationType?.toSessionType()
            else -> null
        }
    }

    private fun BrainAction.toBrainSessionType(): BrainSessionType? {
        return when {
            intent.startsWith("cab", ignoreCase = true) -> BrainSessionType.CAB
            intent.startsWith("food", ignoreCase = true) -> BrainSessionType.FOOD
            intent.startsWith("grocery", ignoreCase = true) -> BrainSessionType.GROCERY
            intent.startsWith("shopping", ignoreCase = true) -> BrainSessionType.SHOPPING
            intent.startsWith("music", ignoreCase = true) -> BrainSessionType.MUSIC
            intent.startsWith("media", ignoreCase = true) -> BrainSessionType.MEDIA
            intent.startsWith("content", ignoreCase = true) -> BrainSessionType.CONTENT
            intent.startsWith("communication", ignoreCase = true) -> BrainSessionType.COMMUNICATION
            intent.startsWith("phone", ignoreCase = true) -> BrainSessionType.PHONE
            intent.startsWith("screen", ignoreCase = true) -> BrainSessionType.SCREEN
            intent.equals("online_ai_permission", ignoreCase = true) -> BrainSessionType.ONLINE_HELPER
            intent.equals("local_model_unavailable", ignoreCase = true) -> BrainSessionType.LOCAL_LLM
            else -> null
        }
    }

    private fun BrainModelRole.toBrainSessionType(): BrainSessionType? {
        return when (this) {
            BrainModelRole.SCREEN_UNDERSTANDING -> BrainSessionType.SCREEN
            BrainModelRole.ONLINE_AI_HELPER -> BrainSessionType.ONLINE_HELPER
            BrainModelRole.GEMMA_REASONING -> BrainSessionType.LOCAL_LLM
            BrainModelRole.LITE_COMMAND -> BrainSessionType.BASIC_CONTROL
            BrainModelRole.ACTION_JSON -> BrainSessionType.UNKNOWN
            BrainModelRole.MOCK_FALLBACK -> BrainSessionType.UNKNOWN
        }
    }

    private fun PendingConfirmationType.toSessionType(): BrainSessionType? {
        return when (this) {
            PendingConfirmationType.ADD_TO_CART -> BrainSessionType.SHOPPING
            PendingConfirmationType.BOOK_RIDE -> BrainSessionType.CAB
            PendingConfirmationType.FOLLOW,
            PendingConfirmationType.SUBSCRIBE -> BrainSessionType.MEDIA
            PendingConfirmationType.PLACE_ORDER -> BrainSessionType.GROCERY
            PendingConfirmationType.SEND_MESSAGE -> BrainSessionType.COMMUNICATION
            PendingConfirmationType.SEND_EMAIL -> BrainSessionType.COMMUNICATION
            PendingConfirmationType.POST,
            PendingConfirmationType.SHARE,
            PendingConfirmationType.EXPORT_CONTENT -> BrainSessionType.CONTENT
            PendingConfirmationType.CALL_CONTACT,
            PendingConfirmationType.SAVE_CONTACT -> BrainSessionType.PHONE
            PendingConfirmationType.OPEN_PAYMENT_PAGE -> BrainSessionType.SHOPPING
            PendingConfirmationType.ONLINE_AI_USE -> BrainSessionType.ONLINE_HELPER
            PendingConfirmationType.TYPE_TEXT,
            PendingConfirmationType.TAP_TARGET,
            PendingConfirmationType.GENERIC_SAFE_ACTION,
            PendingConfirmationType.MANUAL_HANDOFF -> BrainSessionType.BASIC_CONTROL
        }
    }

    private fun collectVisibleOptions(result: CommandResult): List<String> {
        val options = buildList {
            result.entities["selectedProvider"]?.let { add(it) }
            result.entities["selectedOption"]?.let { add(it) }
            result.entities["selectedApp"]?.let { add(it) }
            result.entities["availableProviders"]?.let { add(it) }
            result.entities["providerResults"]?.let { add(it) }
        }
        return options.map { redactor.redactTextForMemory(it) }.distinct()
    }

    private fun extractSelectedOption(params: Map<String, String>): String? {
        return params["selectedOption"]
            ?: params["selectedProvider"]
            ?: params["selectedApp"]
            ?: params["selectedCandidate"]
    }

    private fun defaultPriority(): List<BrainSessionType> {
        return listOf(
            BrainSessionType.GROCERY,
            BrainSessionType.FOOD,
            BrainSessionType.CAB,
            BrainSessionType.SHOPPING,
            BrainSessionType.COMMUNICATION,
            BrainSessionType.CONTENT,
            BrainSessionType.MUSIC,
            BrainSessionType.MEDIA,
            BrainSessionType.PHONE,
            BrainSessionType.SCREEN,
            BrainSessionType.BASIC_CONTROL,
            BrainSessionType.ONLINE_HELPER,
            BrainSessionType.LOCAL_LLM,
            BrainSessionType.UNKNOWN
        )
    }

    private fun buildConfirmationId(
        sessionType: BrainSessionType,
        type: PendingConfirmationType,
        rawText: String,
        sessionId: String?,
        brainAction: BrainAction?
    ): String {
        return buildString {
            append(sessionType.wireValue)
            append(':')
            append(type.wireValue)
            append(':')
            append(sessionId.orEmpty())
            append(':')
            append(rawText.lowercase().hashCode())
            append(':')
            append(brainAction?.intent.orEmpty())
        }
    }

    private fun buildRecoveryState(
        sessionType: BrainSessionType,
        failureReason: String,
        suggestion: String? = null,
        canRetry: Boolean = true
    ): RecoveryState {
        return RecoveryState(
            sessionType = sessionType,
            retryCount = snapshot().recoveryStates[sessionType]?.retryCount?.plus(1) ?: 1,
            lastFailureReason = redactor.redactTextForMemory(failureReason),
            lastSuggestion = suggestion?.let { redactor.redactTextForMemory(it) },
            canRetry = canRetry,
            updatedAtMillis = clock()
        )
    }

    private val confirmationTtlMillis: Long = 10 * 60 * 1000
    private val defaultSessionTtlMillis: Long = 60 * 60 * 1000
}
