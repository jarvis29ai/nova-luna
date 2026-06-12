package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelCatalogEntry
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.util.AssistantTextNormalizer

class LocalBrainModelClient(
    override val role: BrainModelRole,
    private val roleReadinessProvider: BrainRoleReadinessProvider = NoOpBrainRoleReadinessProvider,
    private val engine: PhoneLocalLlmEngine = UnavailablePhoneLocalLlmEngine(),
    private val manager: ModelRuntimeManager? = null,
    private val promptBuilder: LocalModelPromptBuilder = LocalModelPromptBuilder(),
    private val actionParser: BrainActionParser = BrainActionParser(),
    private val validator: BrainActionValidator = BrainActionValidator(),
    private val catalog: BrainModelCatalog = BrainModelCatalog
) : PhoneBrainModel {
    override val available: Boolean
        get() = roleReadinessProvider.isReady(role) && (manager?.getEngineForRole(role)?.first?.available() ?: engine.available()) && catalog.entryForRole(role) != null

    fun buildPrompt(request: BrainRequest, routeDecision: BrainRouteDecision): String {
        val entry = catalog.entryForRole(role)
            ?: error("Unknown local brain role: $role")
        return promptBuilder.build(request, routeDecision, entry)
    }

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        if (routeDecision.selectedRole != role) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "Route decision selected ${routeDecision.selectedRole.wireValue}, but this client handles ${role.wireValue}.",
                safetyNotes = routeDecision.safetyNotes,
                localModelId = localModelId(),
                localModelDisplayName = catalog.entryForRole(role)?.displayName,
                localModelStatus = PhoneLocalLlmStatus.UNAVAILABLE,
                promptBuilt = false,
                jsonParsed = false,
                realInference = false,
                nativeGenerationAvailable = false,
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )
        }

        val entry = catalog.entryForRole(role)
            ?: return BrainModelResult.unavailable(
                role = role,
                reason = "Unknown local brain role: ${role.wireValue}.",
                safetyNotes = routeDecision.safetyNotes,
                localModelId = localModelId(),
                localModelDisplayName = catalog.entryForRole(role)?.displayName,
                localModelStatus = PhoneLocalLlmStatus.UNAVAILABLE,
                promptBuilt = false,
                jsonParsed = false,
                realInference = false,
                nativeGenerationAvailable = false,
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )

        if (!roleReadinessProvider.isReady(role)) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "AI brain is not installed yet for ${entry.displayName}.",
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Download or import the ${entry.displayName} model into private app storage before using it."
                ),
                localModelId = localModelId(),
                localModelDisplayName = entry.displayName,
                localModelStatus = PhoneLocalLlmStatus.UNAVAILABLE,
                promptBuilt = false,
                jsonParsed = false,
                realInference = false,
                nativeGenerationAvailable = false,
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )
        }

        val (effectiveEngine, sessionTrace) = if (manager != null) {
            manager.getEngineForRole(role)
        } else {
            engine to null
        }

        if (!effectiveEngine.available()) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "Local candidate engine is unavailable for ${entry.displayName}.",
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "The downloaded model is ready, but the local inference engine is unavailable."
                ),
                localModelId = localModelId(),
                localModelDisplayName = entry.displayName,
                localModelStatus = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                promptBuilt = false,
                jsonParsed = false,
                realInference = false,
                nativeGenerationAvailable = false,
                jsonParseAttempted = false,
                jsonParseSuccess = false,
                sessionTrace = sessionTrace
            )
        }

        val prompt = buildPrompt(request, routeDecision)
        val generation = runCatching {
            effectiveEngine.generate(prompt, DEFAULT_TIMEOUT_MS)
        }.getOrElse { throwable ->
            return BrainModelResult.unavailable(
                role = role,
                reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Local candidate generation failed for ${entry.displayName}.",
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "The local candidate engine threw an exception."
                ),
                localModelId = localModelId(),
                localModelDisplayName = entry.displayName,
                localModelStatus = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                promptBuilt = true,
                jsonParsed = false,
                realInference = true,
                nativeGenerationAvailable = true,
                jsonParseAttempted = false,
                jsonParseSuccess = false,
                sessionTrace = sessionTrace
            )
        }

        if (!generation.success) {
            return BrainModelResult.unavailable(
                role = role,
                reason = generation.reason,
                rawResponse = generation.text,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Downloaded local model produced no usable output."
                ),
                localModelId = generation.modelId ?: localModelId(),
                localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
                localModelStatus = generation.status,
                promptBuilt = true,
                jsonParsed = false,
                realInference = true,
                nativeGenerationAvailable = true,
                jsonParseAttempted = true,
                jsonParseSuccess = false,
                latencyMillis = generation.latencyMillis,
                sessionTrace = sessionTrace
            )
        }

        val parsedAction = actionParser.parse(
            rawCommand = request.rawText,
            normalizedCommand = AssistantTextNormalizer.normalize(request.rawText),
            modelOutput = generation.text.orEmpty()
        )

        if (parsedAction.source == com.nova.luna.model.BrainActionSource.ERROR) {
            return BrainModelResult.unavailable(
                role = role,
                reason = parsedAction.reason,
                rawResponse = generation.text,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Downloaded local model output was rejected by the Phase 23 parser."
                ),
                localModelId = generation.modelId ?: localModelId(),
                localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
                localModelStatus = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                promptBuilt = true,
                jsonParsed = false,
                realInference = true,
                nativeGenerationAvailable = true,
                jsonParseAttempted = true,
                jsonParseSuccess = false,
                latencyMillis = generation.latencyMillis,
                sessionTrace = sessionTrace
            )
        }

        if (!validator.isAcceptable(parsedAction)) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "BrainActionValidator rejected the local candidate.",
                rawResponse = generation.text,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Downloaded local model output was rejected by the validator."
                ),
                localModelId = generation.modelId ?: localModelId(),
                localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
                localModelStatus = PhoneLocalLlmStatus.VALIDATION_REJECTED,
                promptBuilt = true,
                jsonParsed = true,
                realInference = true,
                nativeGenerationAvailable = true,
                jsonParseAttempted = true,
                jsonParseSuccess = true,
                latencyMillis = generation.latencyMillis,
                sessionTrace = sessionTrace
            )
        }

        return BrainModelResult.available(
            role = role,
            candidateAction = parsedAction,
            rawResponse = generation.text,
            reason = "Downloaded local model produced a structured candidate.",
            safetyNotes = routeDecision.safetyNotes + listOf(
                "Downloaded local model output passed Phase 23 structured parsing.",
                "SafetyGate still decides whether the candidate may execute."
            ),
            localModelId = generation.modelId ?: localModelId(),
            localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
            localModelStatus = generation.status,
            promptBuilt = true,
            jsonParsed = true,
            realInference = true,
            nativeGenerationAvailable = true,
            jsonParseAttempted = true,
            jsonParseSuccess = true,
            latencyMillis = generation.latencyMillis,
            sessionTrace = sessionTrace
        )
    }

    private fun localModelId(): PhoneLocalLlmModelId? {
        return when (role) {
            BrainModelRole.CORE_BRAIN -> PhoneLocalLlmModelId.GEMMA_3N
            BrainModelRole.MULTILINGUAL_BACKUP -> PhoneLocalLlmModelId.QWEN_1_5B
            BrainModelRole.LITE_FALLBACK -> PhoneLocalLlmModelId.QWEN_0_5B
            BrainModelRole.GEMMA_REASONING -> PhoneLocalLlmModelId.GEMMA_3N
            else -> null
        }
    }

    private fun LocalCandidateParseStatus.toPhoneLocalLlmStatus(): PhoneLocalLlmStatus {
        return when (this) {
            LocalCandidateParseStatus.READY -> PhoneLocalLlmStatus.READY
            LocalCandidateParseStatus.INVALID_JSON -> PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED
            LocalCandidateParseStatus.UNSUPPORTED_SCHEMA -> PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED
            LocalCandidateParseStatus.EMPTY_OUTPUT -> PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 5_000L
    }
}
