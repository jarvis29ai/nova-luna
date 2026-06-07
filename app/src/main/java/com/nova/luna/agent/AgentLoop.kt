package com.nova.luna.agent

import com.nova.luna.brain.BrainActionRuntime
import com.nova.luna.brain.BrainService
import com.nova.luna.brain.BrainRequest
import com.nova.luna.brain.toCommandIntent
import com.nova.luna.memory.BrainMemorySnapshot
import com.nova.luna.memory.BrainSessionManager
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateReader
import java.util.UUID

class AgentLoop(
    private val brainService: BrainService,
    private val brainActionRuntime: BrainActionRuntime,
    private val brainSessionManager: BrainSessionManager,
    private val screenStateReader: ScreenStateReader = ScreenStateReader(),
    private val verifier: AgentLoopVerifier = AgentLoopVerifier(),
    private val decisionMaker: AgentLoopDecisionMaker = AgentLoopDecisionMaker(),
    private val config: AgentLoopConfig = AgentLoopConfig.safeDefaults(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun buildPlan(
        rawText: String,
        commandIntent: CommandIntent,
        activeSessionType: BrainSessionType? = null,
        pendingConfirmation: PendingConfirmation? = null,
        screenState: ScreenState? = null
    ): TaskPlan {
        return brainService.buildTaskPlan(
            rawText = rawText,
            commandIntent = commandIntent,
            activeSessionType = activeSessionType,
            pendingConfirmation = pendingConfirmation,
            screenState = screenState
        )
    }

    fun shouldRun(
        rawText: String,
        commandIntent: CommandIntent,
        activeSessionType: BrainSessionType? = null,
        pendingConfirmation: PendingConfirmation? = null,
        screenState: ScreenState? = null
    ): Boolean {
        return buildPlan(
            rawText = rawText,
            commandIntent = commandIntent,
            activeSessionType = activeSessionType,
            pendingConfirmation = pendingConfirmation,
            screenState = screenState
        ).loopCapable
    }

    fun run(
        rawText: String,
        commandIntent: CommandIntent,
        activeCabSession: Boolean = false,
        activeGrocerySession: Boolean = false,
        activeFoodSession: Boolean = false,
        activeSessionType: BrainSessionType? = null,
        pendingConfirmation: PendingConfirmation? = null,
        onlineConsentGiven: Boolean = false,
        memorySnapshot: BrainMemorySnapshot = brainSessionManager.snapshot()
    ): AgentLoopResult {
        val initialScreen = screenStateReader.captureScreenState()
        val plan = buildPlan(
            rawText = rawText,
            commandIntent = commandIntent,
            activeSessionType = activeSessionType ?: memorySnapshot.activeSessionType(),
            pendingConfirmation = pendingConfirmation ?: memorySnapshot.activePendingConfirmation,
            screenState = initialScreen
        )

        if (!plan.allowLoop || !plan.loopCapable) {
            return runSingleStep(
                rawText = rawText,
                commandIntent = commandIntent,
                plan = plan.copy(allowLoop = false, loopCapable = false),
                activeCabSession = activeCabSession,
                activeGrocerySession = activeGrocerySession,
                activeFoodSession = activeFoodSession,
                activeSessionType = activeSessionType ?: memorySnapshot.activeSessionType(),
                pendingConfirmation = pendingConfirmation ?: memorySnapshot.activePendingConfirmation,
                onlineConsentGiven = onlineConsentGiven,
                initialScreen = initialScreen,
                memorySnapshot = memorySnapshot
            )
        }

        val loopId = UUID.randomUUID().toString()
        val startedAt = clock()
        var state = AgentLoopState(
            loopId = loopId,
            sessionId = memorySnapshot.activeSessionType()?.wireValue,
            taskGoal = plan.goal,
            domainSessionType = plan.domain ?: activeSessionType ?: memorySnapshot.activeSessionType(),
            currentStepNumber = 0,
            maxSteps = plan.maxSteps.coerceAtMost(config.maxStepsPerTask),
            retryCount = 0,
            maxRetries = plan.maxRetries.coerceAtMost(config.maxRetriesPerStep),
            startedAtMillis = startedAt,
            lastUpdatedAtMillis = startedAt,
            userFacingStatus = "Starting task loop."
        )

        val steps = mutableListOf<TaskStep>()
        var currentScreen = initialScreen
        var currentPendingConfirmation = pendingConfirmation ?: memorySnapshot.activePendingConfirmation
        var lastResult: CommandResult? = null
        var lastVerification: AgentLoopVerification? = null
        var recoveryUsed = false
        var askedUser = false
        var stopReason = AgentLoopStopReason.UNKNOWN_FAILURE
        var completed = false

        var iteration = 0
        while (iteration < state.maxSteps) {
            val elapsedMillis = clock() - startedAt
            if (elapsedMillis >= config.maxElapsedMillis) {
                stopReason = AgentLoopStopReason.MAX_STEPS_REACHED
                break
            }

            val screenBefore = currentScreen ?: screenStateReader.captureScreenState()
            val readSnapshot = screenBefore?.let { brainSessionManager.rememberScreenState(it, state.sessionId) }
            val readStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.READ_SCREEN,
                status = if (screenBefore == null) TaskStepStatus.FAILED else TaskStepStatus.SUCCEEDED,
                screenSnapshotId = readSnapshot?.snapshotId,
                screenSummary = screenBefore?.summarizedState,
                message = screenBefore?.summarizedState ?: "I could not read the screen yet.",
                retryable = screenBefore == null
            )
            steps += readStep
            state = state.record(readStep)

            if (screenBefore == null) {
                val decision = decisionMaker.decide(
                    plan = plan,
                    state = state,
                    screenState = null,
                    lastResult = lastResult,
                    verification = lastVerification,
                    pendingConfirmation = currentPendingConfirmation
                )
                when (decision) {
                    is AgentLoopDecision.Recover -> {
                        recoveryUsed = true
                        state = state.withRetryCount(state.retryCount + 1, decision.message)
                        steps += TaskStep(
                            stepNumber = iteration + 1,
                            step = decision.step,
                            status = TaskStepStatus.RUNNING,
                            message = decision.message,
                            stopReason = decision.stopReason,
                            retryable = decision.retryable
                        )
                        if (!decision.retryable || !canRetry(state)) {
                            stopReason = decision.stopReason ?: AgentLoopStopReason.NO_ACCESSIBILITY
                            break
                        }
                        iteration++
                        continue
                    }

                    is AgentLoopDecision.AskUser -> {
                        askedUser = true
                        stopReason = decision.stopReason ?: AgentLoopStopReason.NEEDS_USER_INPUT
                        steps += TaskStep(
                            stepNumber = iteration + 1,
                            step = decision.step,
                            status = TaskStepStatus.ASK_USER,
                            message = decision.message,
                            stopReason = decision.stopReason
                        )
                        state = state.withStop(stopReason, decision.message, TaskStepStatus.ASK_USER)
                        break
                    }

                    is AgentLoopDecision.ManualHandoff -> {
                        askedUser = true
                        stopReason = decision.stopReason ?: AgentLoopStopReason.MANUAL_HANDOFF
                        steps += TaskStep(
                            stepNumber = iteration + 1,
                            step = decision.step,
                            status = TaskStepStatus.MANUAL_HANDOFF,
                            message = decision.message,
                            stopReason = decision.stopReason
                        )
                        state = state.withStop(stopReason, decision.message, TaskStepStatus.MANUAL_HANDOFF)
                        break
                    }

                    is AgentLoopDecision.Complete -> {
                        completed = true
                        stopReason = decision.stopReason ?: AgentLoopStopReason.COMPLETED
                        steps += TaskStep(
                            stepNumber = iteration + 1,
                            step = decision.step,
                            status = TaskStepStatus.COMPLETE,
                            message = decision.message,
                            stopReason = decision.stopReason
                        )
                        state = state.withStop(stopReason, decision.message, TaskStepStatus.COMPLETE)
                        break
                    }

                    is AgentLoopDecision.Stop -> {
                        stopReason = decision.stopReason ?: AgentLoopStopReason.UNKNOWN_FAILURE
                        steps += TaskStep(
                            stepNumber = iteration + 1,
                            step = decision.step,
                            status = TaskStepStatus.STOPPED,
                            message = decision.message,
                            stopReason = decision.stopReason
                        )
                        state = state.withStop(stopReason, decision.message, TaskStepStatus.STOPPED)
                        break
                    }

                    is AgentLoopDecision.Continue -> {
                        iteration++
                        continue
                    }
                }
            }

            val planStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.PLAN_NEXT_STEP,
                status = TaskStepStatus.RUNNING,
                screenSummary = screenBefore?.summarizedState,
                message = "Planning the next safe action."
            )
            steps += planStep
            state = state.record(planStep)

            val brainAction = brainService.process(
                rawText = rawText,
                activeCabSession = activeCabSession,
                activeGrocerySession = activeGrocerySession,
                activeFoodSession = activeFoodSession,
                onlineConsentGiven = onlineConsentGiven,
                activeSessionType = state.domainSessionType,
                pendingConfirmation = currentPendingConfirmation,
                screenState = screenBefore
            )
            val validateStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.VALIDATE_ACTION,
                status = if (brainActionRuntime.isAcceptable(brainAction)) {
                    TaskStepStatus.SUCCEEDED
                } else {
                    TaskStepStatus.FAILED
                },
                brainAction = brainAction,
                message = brainAction.reply,
                retryable = false
            )
            steps += validateStep
            state = state.record(validateStep)

            if (!brainActionRuntime.isAcceptable(brainAction)) {
                stopReason = AgentLoopStopReason.BLOCKED_BY_SAFETY
                state = state.withStop(stopReason, "BrainActionValidator rejected the candidate.", TaskStepStatus.FAILED)
                steps += TaskStep(
                    stepNumber = iteration + 1,
                    step = AgentLoopStep.FAILED,
                    status = TaskStepStatus.FAILED,
                    brainAction = brainAction,
                    message = "BrainActionValidator rejected the candidate.",
                    stopReason = stopReason
                )
                break
            }

            val safetyDecision = brainActionRuntime.evaluateSafety(
                brainAction = brainAction,
                pendingConfirmation = currentPendingConfirmation,
                userConfirmed = onlineConsentGiven
            )
            val safetyStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.SAFETY_CHECK,
                status = if (safetyDecision.allowed) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED,
                brainAction = brainAction,
                safetyDecision = safetyDecision,
                message = safetyDecision.message,
                retryable = safetyDecision.allowed.not()
            )
            steps += safetyStep
            state = state.record(safetyStep)

            if (!safetyDecision.allowed) {
                stopReason = if (safetyDecision.requiresConfirmation) {
                    AgentLoopStopReason.NEEDS_CONFIRMATION
                } else if (safetyDecision.requiresBiometric) {
                    AgentLoopStopReason.OTP_OR_SECRET_REQUIRED
                } else {
                    AgentLoopStopReason.BLOCKED_BY_SAFETY
                }
                val stepStatus = if (safetyDecision.requiresConfirmation) {
                    TaskStepStatus.ASK_USER
                } else if (safetyDecision.requiresBiometric) {
                    TaskStepStatus.MANUAL_HANDOFF
                } else {
                    TaskStepStatus.FAILED
                }
                steps += TaskStep(
                    stepNumber = iteration + 1,
                    step = if (safetyDecision.requiresConfirmation) {
                        AgentLoopStep.WAIT_FOR_CONFIRMATION
                    } else {
                        AgentLoopStep.MANUAL_HANDOFF
                    },
                    status = stepStatus,
                    brainAction = brainAction,
                    safetyDecision = safetyDecision,
                    message = safetyDecision.message,
                    stopReason = stopReason
                )
                state = state.withStop(stopReason, safetyDecision.message, stepStatus)
                askedUser = true
                break
            }

            val commandIntentForAction = brainAction.toCommandIntent()
            val commandResult = brainActionRuntime.execute(
                brainAction = brainAction,
                rawText = rawText,
                parsed = commandIntentForAction,
                pendingConfirmation = currentPendingConfirmation,
                userConfirmed = onlineConsentGiven
            )

            if (commandResult == null) {
                stopReason = AgentLoopStopReason.BLOCKED_BY_SAFETY
                steps += TaskStep(
                    stepNumber = iteration + 1,
                    step = AgentLoopStep.EXECUTE_ACTION,
                    status = TaskStepStatus.FAILED,
                    brainAction = brainAction,
                    safetyDecision = safetyDecision,
                    message = "The candidate action was rejected before execution.",
                    stopReason = stopReason
                )
                state = state.withStop(stopReason, "The candidate action was rejected before execution.", TaskStepStatus.FAILED)
                break
            }

            val executedCommandResult = commandResult!!

            val executeStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.EXECUTE_ACTION,
                status = if (executedCommandResult.success) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED,
                brainAction = brainAction,
                safetyDecision = safetyDecision,
                commandResult = executedCommandResult,
                message = executedCommandResult.message,
                retryable = !executedCommandResult.success && !executedCommandResult.safetyDecision.humanRequired
            )
            steps += executeStep
            state = state.record(executeStep)

            val screenAfter = screenStateReader.captureScreenState()
            val screenAfterSnapshot = screenAfter?.let { brainSessionManager.rememberScreenState(it, state.sessionId) }
            val verification = verifier.verify(
                before = screenBefore,
                after = screenAfter,
                commandIntent = commandIntentForAction,
                commandResult = executedCommandResult
            )
            lastVerification = verification
            val verifyStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.VERIFY_RESULT,
                status = if (verification.verified || verification.progressObserved) {
                    TaskStepStatus.SUCCEEDED
                } else {
                    TaskStepStatus.FAILED
                },
                screenSnapshotId = screenAfterSnapshot?.snapshotId,
                screenSummary = screenAfter?.summarizedState,
                brainAction = brainAction,
                safetyDecision = safetyDecision,
                commandResult = executedCommandResult,
                verification = verification,
                message = verification.message,
                retryable = !verification.verified && executedCommandResult.success
            )
            steps += verifyStep
            state = state.record(verifyStep)

            val memoryResult = executedCommandResult.copy(
                screenMemorySnapshotId = screenAfterSnapshot?.snapshotId ?: executedCommandResult.screenMemorySnapshotId,
                memoryMetadata = executedCommandResult.memoryMetadata + buildMemoryMetadata(
                    loopId = loopId,
                    taskPlan = plan,
                    stepCount = steps.size,
                    stopReason = null,
                    verification = verification,
                    recoveryUsed = recoveryUsed,
                    screenBefore = screenBefore,
                    screenAfter = screenAfter
                )
            )
            val stepRequest = buildLoopRequest(
                rawText = rawText,
                activeCabSession = activeCabSession,
                activeGrocerySession = activeGrocerySession,
                activeFoodSession = activeFoodSession,
                activeSessionType = state.domainSessionType,
                pendingConfirmation = currentPendingConfirmation,
                screenState = screenBefore
            )
            brainSessionManager.recordBrainAction(
                request = stepRequest,
                routeDecision = null,
                brainAction = brainAction,
                safetyDecision = safetyDecision
            )
            brainSessionManager.recordCommandResult(
                rawText = rawText,
                commandIntent = commandIntentForAction,
                result = memoryResult,
                sessionTypeHint = memoryResult.memorySessionType ?: state.domainSessionType ?: activeSessionType,
                screenState = screenAfter
            )
            val updateStep = TaskStep(
                stepNumber = iteration + 1,
                step = AgentLoopStep.UPDATE_MEMORY,
                status = TaskStepStatus.SUCCEEDED,
                screenSnapshotId = screenAfterSnapshot?.snapshotId,
                screenSummary = screenAfter?.summarizedState,
                brainAction = brainAction,
                safetyDecision = safetyDecision,
                commandResult = memoryResult,
                verification = verification,
                message = "Memory updated safely.",
                retryable = false
            )
            steps += updateStep
            state = state.record(updateStep)
            lastResult = memoryResult
            currentScreen = screenAfter

            val decision = decisionMaker.decide(
                plan = plan,
                state = state,
                screenState = screenAfter,
                lastResult = memoryResult,
                verification = verification,
                pendingConfirmation = currentPendingConfirmation
            )

            when (decision) {
                is AgentLoopDecision.Complete -> {
                    completed = true
                    stopReason = decision.stopReason ?: AgentLoopStopReason.COMPLETED
                    steps += TaskStep(
                        stepNumber = iteration + 1,
                        step = decision.step,
                        status = TaskStepStatus.COMPLETE,
                        screenSnapshotId = screenAfterSnapshot?.snapshotId,
                        screenSummary = screenAfter?.summarizedState,
                        brainAction = brainAction,
                        safetyDecision = safetyDecision,
                        commandResult = memoryResult,
                        verification = verification,
                        message = decision.message,
                        stopReason = stopReason
                    )
                    state = state.withStop(stopReason, decision.message, TaskStepStatus.COMPLETE)
                    iteration = state.maxSteps
                }

                is AgentLoopDecision.AskUser -> {
                    askedUser = true
                    stopReason = decision.stopReason ?: AgentLoopStopReason.NEEDS_USER_INPUT
                    steps += TaskStep(
                        stepNumber = iteration + 1,
                        step = decision.step,
                        status = TaskStepStatus.ASK_USER,
                        screenSnapshotId = screenAfterSnapshot?.snapshotId,
                        screenSummary = screenAfter?.summarizedState,
                        brainAction = brainAction,
                        safetyDecision = safetyDecision,
                        commandResult = memoryResult,
                        verification = verification,
                        message = decision.message,
                        stopReason = stopReason
                    )
                    state = state.withStop(stopReason, decision.message, TaskStepStatus.ASK_USER)
                    iteration = state.maxSteps
                }

                is AgentLoopDecision.ManualHandoff -> {
                    askedUser = true
                    stopReason = decision.stopReason ?: AgentLoopStopReason.MANUAL_HANDOFF
                    steps += TaskStep(
                        stepNumber = iteration + 1,
                        step = decision.step,
                        status = TaskStepStatus.MANUAL_HANDOFF,
                        screenSnapshotId = screenAfterSnapshot?.snapshotId,
                        screenSummary = screenAfter?.summarizedState,
                        brainAction = brainAction,
                        safetyDecision = safetyDecision,
                        commandResult = memoryResult,
                        verification = verification,
                        message = decision.message,
                        stopReason = stopReason
                    )
                    state = state.withStop(stopReason, decision.message, TaskStepStatus.MANUAL_HANDOFF)
                    iteration = state.maxSteps
                }

                is AgentLoopDecision.Stop -> {
                    stopReason = decision.stopReason ?: AgentLoopStopReason.UNKNOWN_FAILURE
                    steps += TaskStep(
                        stepNumber = iteration + 1,
                        step = decision.step,
                        status = TaskStepStatus.STOPPED,
                        screenSnapshotId = screenAfterSnapshot?.snapshotId,
                        screenSummary = screenAfter?.summarizedState,
                        brainAction = brainAction,
                        safetyDecision = safetyDecision,
                        commandResult = memoryResult,
                        verification = verification,
                        message = decision.message,
                        stopReason = stopReason
                    )
                    state = state.withStop(stopReason, decision.message, TaskStepStatus.STOPPED)
                    iteration = state.maxSteps
                }

                is AgentLoopDecision.Recover -> {
                    recoveryUsed = true
                    state = state.withRetryCount(state.retryCount + 1, decision.message)
                    steps += TaskStep(
                        stepNumber = iteration + 1,
                        step = decision.step,
                        status = TaskStepStatus.RUNNING,
                        screenSnapshotId = screenAfterSnapshot?.snapshotId,
                        screenSummary = screenAfter?.summarizedState,
                        brainAction = brainAction,
                        safetyDecision = safetyDecision,
                        commandResult = memoryResult,
                        verification = verification,
                        message = decision.message,
                        retryable = decision.retryable
                    )
                    if (!decision.retryable || !canRetry(state)) {
                        stopReason = decision.stopReason ?: AgentLoopStopReason.MAX_RETRIES_REACHED
                        steps += TaskStep(
                            stepNumber = iteration + 1,
                            step = AgentLoopStep.FAILED,
                            status = TaskStepStatus.STOPPED,
                            screenSnapshotId = screenAfterSnapshot?.snapshotId,
                            screenSummary = screenAfter?.summarizedState,
                            brainAction = brainAction,
                            safetyDecision = safetyDecision,
                            commandResult = memoryResult,
                            verification = verification,
                            message = decision.message,
                            stopReason = stopReason
                        )
                        state = state.withStop(stopReason, decision.message, TaskStepStatus.STOPPED)
                        iteration = state.maxSteps
                    }
                }

                is AgentLoopDecision.Continue -> {
                    steps += TaskStep(
                        stepNumber = iteration + 1,
                        step = decision.step,
                        status = TaskStepStatus.RUNNING,
                        screenSnapshotId = screenAfterSnapshot?.snapshotId,
                        screenSummary = screenAfter?.summarizedState,
                        brainAction = brainAction,
                        safetyDecision = safetyDecision,
                        commandResult = memoryResult,
                        verification = verification,
                        message = decision.message,
                        retryable = false
                    )
                }
            }

            if (completed || askedUser || state.stopReason != null) {
                break
            }

            if (stuckDetectorTriggered(steps, currentScreen)) {
                stopReason = AgentLoopStopReason.STUCK_DETECTED
                state = state.withStop(stopReason, "I detected a repeated screen, so I stopped safely.")
                steps += TaskStep(
                    stepNumber = iteration + 1,
                    step = AgentLoopStep.STOPPED,
                    status = TaskStepStatus.STOPPED,
                    screenSnapshotId = screenAfterSnapshot?.snapshotId,
                    screenSummary = screenAfter?.summarizedState,
                    brainAction = brainAction,
                    safetyDecision = safetyDecision,
                    commandResult = memoryResult,
                    verification = verification,
                    message = "I detected a repeated screen, so I stopped safely.",
                    stopReason = stopReason
                )
                break
            }

            iteration++
        }

        val finalResult = lastResult ?: CommandResult.failure(
            message = state.userFacingStatus.ifBlank { "I could not complete the task safely." },
            intentType = commandIntent.intentType,
            actionType = commandIntent.actionType,
            entities = commandIntent.entities,
            memorySessionType = state.domainSessionType ?: activeSessionType
        )

        val finalMetadata = buildMemoryMetadata(
            loopId = loopId,
            taskPlan = plan,
            stepCount = steps.size,
            stopReason = stopReason,
            verification = lastVerification,
            recoveryUsed = recoveryUsed,
            screenBefore = currentScreen,
            screenAfter = currentScreen
        ) + mapOf(
            "agentLoopRecorded" to "true",
            "agentLoopStarted" to "true",
            "agentLoopCompleted" to completed.toString(),
            "agentLoopAskedUser" to askedUser.toString()
        )

        return AgentLoopResult(
            taskPlan = plan,
            state = state.copy(
                userFacingStatus = state.userFacingStatus.ifBlank { finalResult.message },
                stopReason = state.stopReason ?: stopReason,
                completionStatus = if (completed) TaskStepStatus.COMPLETE else state.completionStatus
            ),
            finalCommandResult = finalResult.copy(memoryMetadata = finalResult.memoryMetadata + finalMetadata),
            steps = steps,
            started = true,
            completed = completed,
            recoveryUsed = recoveryUsed,
            askedUser = askedUser,
            stopReason = stopReason
        )
    }

    private fun runSingleStep(
        rawText: String,
        commandIntent: CommandIntent,
        plan: TaskPlan,
        activeCabSession: Boolean,
        activeGrocerySession: Boolean,
        activeFoodSession: Boolean,
        activeSessionType: BrainSessionType?,
        pendingConfirmation: PendingConfirmation?,
        onlineConsentGiven: Boolean,
        initialScreen: ScreenState?,
        memorySnapshot: BrainMemorySnapshot
    ): AgentLoopResult {
        val loopId = UUID.randomUUID().toString()
        val startedAt = clock()
        var state = AgentLoopState(
            loopId = loopId,
            sessionId = memorySnapshot.activeSessionType()?.wireValue,
            taskGoal = plan.goal,
            domainSessionType = plan.domain ?: activeSessionType ?: memorySnapshot.activeSessionType(),
            currentStepNumber = 0,
            maxSteps = 1,
            retryCount = 0,
            maxRetries = 0,
            startedAtMillis = startedAt,
            lastUpdatedAtMillis = startedAt,
            userFacingStatus = "Running a one-shot task."
        )
        val steps = mutableListOf<TaskStep>()
        val screenBefore = initialScreen
        val brainAction = brainService.process(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            onlineConsentGiven = onlineConsentGiven,
            activeSessionType = state.domainSessionType,
            pendingConfirmation = pendingConfirmation,
            screenState = screenBefore
        )
        val safetyDecision = brainActionRuntime.evaluateSafety(
            brainAction = brainAction,
            pendingConfirmation = pendingConfirmation,
            userConfirmed = onlineConsentGiven
        )
        val commandIntentForAction = brainAction.toCommandIntent()
        val commandResult = brainActionRuntime.execute(
            brainAction = brainAction,
            rawText = rawText,
            parsed = commandIntent,
            pendingConfirmation = pendingConfirmation,
            userConfirmed = onlineConsentGiven
        ) ?: CommandResult.blocked(
            message = "The candidate action was rejected before execution.",
            intentType = commandIntent.intentType,
            actionType = commandIntent.actionType,
            entities = commandIntent.entities,
            memorySessionType = state.domainSessionType
        )
        val screenAfter = screenStateReader.captureScreenState()
        val verification = verifier.verify(
            before = screenBefore,
            after = screenAfter,
            commandIntent = commandIntentForAction,
            commandResult = commandResult
        )
        val readSnapshot = screenBefore?.let { brainSessionManager.rememberScreenState(it, state.sessionId) }
        val afterSnapshot = screenAfter?.let { brainSessionManager.rememberScreenState(it, state.sessionId) }
        val memoryResult = commandResult.copy(
            screenMemorySnapshotId = afterSnapshot?.snapshotId ?: commandResult.screenMemorySnapshotId,
            memoryMetadata = commandResult.memoryMetadata + buildMemoryMetadata(
                loopId = loopId,
                taskPlan = plan,
                stepCount = 1,
                stopReason = null,
                verification = verification,
                recoveryUsed = false,
                screenBefore = screenBefore,
                screenAfter = screenAfter
            ) + mapOf(
                "agentLoopRecorded" to "true",
                "agentLoopStarted" to "false",
                "agentLoopCompleted" to commandResult.success.toString()
            )
        )
        val request = buildLoopRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            activeSessionType = state.domainSessionType,
            pendingConfirmation = pendingConfirmation,
            screenState = screenBefore
        )
        brainSessionManager.recordBrainAction(
            request = request,
            routeDecision = null,
            brainAction = brainAction,
            safetyDecision = safetyDecision
        )
        brainSessionManager.recordCommandResult(
            rawText = rawText,
            commandIntent = commandIntentForAction,
            result = memoryResult,
            sessionTypeHint = memoryResult.memorySessionType ?: state.domainSessionType ?: activeSessionType,
            screenState = screenAfter
        )

        val readStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.READ_SCREEN,
            status = if (screenBefore == null) TaskStepStatus.FAILED else TaskStepStatus.SUCCEEDED,
            screenSnapshotId = readSnapshot?.snapshotId,
            screenSummary = screenBefore?.summarizedState,
            message = screenBefore?.summarizedState ?: "I could not read the screen yet.",
            retryable = screenBefore == null
        )
        val planStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.PLAN_NEXT_STEP,
            status = TaskStepStatus.SUCCEEDED,
            screenSummary = screenBefore?.summarizedState,
            brainAction = brainAction,
            message = brainAction.reply
        )
        val validateStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.VALIDATE_ACTION,
            status = if (brainActionRuntime.isAcceptable(brainAction)) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED,
            brainAction = brainAction,
            message = brainAction.reply
        )
        val safetyStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.SAFETY_CHECK,
            status = if (safetyDecision.allowed) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED,
            brainAction = brainAction,
            safetyDecision = safetyDecision,
            message = safetyDecision.message
        )
        val executeStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.EXECUTE_ACTION,
            status = if (memoryResult.success) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED,
            brainAction = brainAction,
            safetyDecision = safetyDecision,
            commandResult = memoryResult,
            message = memoryResult.message
        )
        val verifyStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.VERIFY_RESULT,
            status = if (verification.verified || verification.progressObserved) TaskStepStatus.SUCCEEDED else TaskStepStatus.FAILED,
            screenSnapshotId = afterSnapshot?.snapshotId,
            screenSummary = screenAfter?.summarizedState,
            brainAction = brainAction,
            safetyDecision = safetyDecision,
            commandResult = memoryResult,
            verification = verification,
            message = verification.message
        )
        val updateStep = TaskStep(
            stepNumber = 1,
            step = AgentLoopStep.UPDATE_MEMORY,
            status = TaskStepStatus.SUCCEEDED,
            screenSnapshotId = afterSnapshot?.snapshotId,
            screenSummary = screenAfter?.summarizedState,
            brainAction = brainAction,
            safetyDecision = safetyDecision,
            commandResult = memoryResult,
            verification = verification,
            message = "Memory updated safely."
        )
        steps += listOf(readStep, planStep, validateStep, safetyStep, executeStep, verifyStep, updateStep)
        state = state
            .record(readStep)
            .record(planStep)
            .record(validateStep)
            .record(safetyStep)
            .record(executeStep)
            .record(verifyStep)
            .record(updateStep)

        val completed = memoryResult.success && !memoryResult.awaitingConfirmation
        val stopReason = when {
            completed -> AgentLoopStopReason.COMPLETED
            memoryResult.awaitingConfirmation || memoryResult.safetyDecision.requiresConfirmation -> AgentLoopStopReason.NEEDS_CONFIRMATION
            !memoryResult.success && memoryResult.safetyDecision.humanRequired -> AgentLoopStopReason.MANUAL_HANDOFF
            !memoryResult.success -> AgentLoopStopReason.UNKNOWN_FAILURE
            else -> AgentLoopStopReason.UNKNOWN_FAILURE
        }

        return AgentLoopResult(
            taskPlan = plan,
            state = state.copy(
                stopReason = stopReason,
                completionStatus = if (completed) TaskStepStatus.COMPLETE else TaskStepStatus.STOPPED,
                userFacingStatus = memoryResult.message
            ),
            finalCommandResult = memoryResult.copy(
                memoryMetadata = memoryResult.memoryMetadata + mapOf(
                    "agentLoopStarted" to "false",
                    "agentLoopRecorded" to "true",
                    "agentLoopCompleted" to completed.toString(),
                    "agentLoopStepCount" to steps.size.toString(),
                    "agentLoopStopReason" to stopReason.name
                )
            ),
            steps = steps,
            started = false,
            completed = completed,
            recoveryUsed = false,
            askedUser = memoryResult.awaitingConfirmation || memoryResult.safetyDecision.requiresConfirmation,
            stopReason = stopReason,
            verification = verification
        )
    }

    private fun buildLoopRequest(
        rawText: String,
        activeCabSession: Boolean,
        activeGrocerySession: Boolean,
        activeFoodSession: Boolean,
        activeSessionType: BrainSessionType?,
        pendingConfirmation: PendingConfirmation?,
        screenState: ScreenState?
    ): BrainRequest {
        val snapshot = brainSessionManager.snapshot()
        return BrainRequest(
            rawText = rawText,
            activeCabSession = activeCabSession,
            activeGrocerySession = activeGrocerySession,
            activeFoodSession = activeFoodSession,
            screenState = screenState,
            activeSessionType = activeSessionType ?: snapshot.activeSessionType(),
            pendingConfirmation = pendingConfirmation,
            memorySnapshot = snapshot,
            preferences = snapshot.preferences,
            recoveryState = activeSessionType?.let { snapshot.recoveryStates[it] }
        )
    }

    private fun buildMemoryMetadata(
        loopId: String,
        taskPlan: TaskPlan,
        stepCount: Int,
        stopReason: AgentLoopStopReason?,
        verification: AgentLoopVerification?,
        recoveryUsed: Boolean,
        screenBefore: ScreenState?,
        screenAfter: ScreenState?
    ): Map<String, String> {
        return buildMap {
            put("agentLoopId", loopId)
            put("agentLoopStepCount", stepCount.toString())
            put("agentLoopLoopCapable", taskPlan.loopCapable.toString())
            put("agentLoopReason", taskPlan.reason)
            put("agentLoopRecoveryUsed", recoveryUsed.toString())
            stopReason?.let { put("agentLoopStopReason", it.name) }
            verification?.let {
                put("agentLoopVerificationStatus", it.screenVerification.status.name)
                put("agentLoopVerificationMessage", it.message)
            }
            screenBefore?.summarizedState?.takeIf { it.isNotBlank() }?.let { put("screenBeforeSummary", it) }
            screenAfter?.summarizedState?.takeIf { it.isNotBlank() }?.let { put("screenAfterSummary", it) }
        }
    }

    private fun canRetry(state: AgentLoopState): Boolean {
        return state.retryCount < state.maxRetries && state.currentStepNumber < state.maxSteps
    }

    private fun stuckDetectorTriggered(steps: List<TaskStep>, currentScreen: ScreenState?): Boolean {
        return if (config.stopOnRepeatedScreen) {
            val detector = StuckDetector(config.maxRepeatedScreenCount)
            detector.isStuck(steps, currentScreen?.signature())
        } else {
            false
        }
    }
}

typealias TaskLoopCoordinator = AgentLoop
