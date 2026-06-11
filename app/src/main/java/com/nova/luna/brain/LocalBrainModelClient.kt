package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelCatalogEntry
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class LocalBrainModelClient(
    override val role: BrainModelRole,
    private val roleReadinessProvider: BrainRoleReadinessProvider = NoOpBrainRoleReadinessProvider,
    private val engine: PhoneLocalLlmEngine = UnavailablePhoneLocalLlmEngine(),
    private val promptBuilder: LocalModelPromptBuilder = LocalModelPromptBuilder(),
    private val candidateParser: LocalCandidateJsonParser = LocalCandidateJsonParser(),
    private val candidateValidator: LocalCandidateValidator = LocalCandidateValidator(),
    private val catalog: BrainModelCatalog = BrainModelCatalog
) : PhoneBrainModel {
    override val available: Boolean
        get() = roleReadinessProvider.isReady(role) && engine.available() && catalog.entryForRole(role) != null

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
                jsonParsed = false
            )
        }

        val entry = catalog.entryForRole(role)
            ?: return BrainModelResult.unavailable(
                role = role,
                reason = "Unknown local brain role: ${role.wireValue}.",
                safetyNotes = routeDecision.safetyNotes,
                localModelId = localModelId(),
                localModelStatus = PhoneLocalLlmStatus.UNAVAILABLE,
                promptBuilt = false,
                jsonParsed = false
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
                jsonParsed = false
            )
        }

        if (!engine.available()) {
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
                jsonParsed = false
            )
        }

        val prompt = buildPrompt(request, routeDecision)
        val generation = runCatching {
            engine.generate(prompt, DEFAULT_TIMEOUT_MS)
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
                jsonParsed = false
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
                latencyMillis = generation.latencyMillis
            )
        }

        val parseResult = candidateParser.parse(generation.text.orEmpty())
        if (!parseResult.accepted || parseResult.candidateAction == null) {
            return BrainModelResult.unavailable(
                role = role,
                reason = parseResult.reason,
                rawResponse = generation.text,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Downloaded local model output was rejected by the strict candidate parser."
                ),
                localModelId = generation.modelId ?: localModelId(),
                localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
                localModelStatus = parseResult.status.toPhoneLocalLlmStatus(),
                promptBuilt = true,
                jsonParsed = false,
                latencyMillis = generation.latencyMillis
            )
        }

        val validationResult = candidateValidator.validate(
            candidateAction = parseResult.candidateAction,
            rawJson = parseResult.extractedJson
        )
        if (!validationResult.accepted) {
            return BrainModelResult.unavailable(
                role = role,
                reason = validationResult.reason,
                rawResponse = generation.text,
                candidateAction = validationResult.candidateAction,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Downloaded local model output was rejected by the candidate validator."
                ),
                localModelId = generation.modelId ?: localModelId(),
                localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
                localModelStatus = PhoneLocalLlmStatus.VALIDATION_REJECTED,
                promptBuilt = true,
                jsonParsed = true,
                latencyMillis = generation.latencyMillis
            )
        }

        return BrainModelResult.available(
            role = role,
            candidateAction = validationResult.candidateAction,
            rawResponse = generation.text,
            reason = "Downloaded local model produced a structured candidate.",
            safetyNotes = routeDecision.safetyNotes + listOf(
                "Downloaded local model output passed strict candidate parsing and validation.",
                "SafetyGate still decides whether the candidate may execute."
            ),
            localModelId = generation.modelId ?: localModelId(),
            localModelDisplayName = generation.modelDisplayName ?: entry.displayName,
            localModelStatus = generation.status,
            promptBuilt = true,
            jsonParsed = true,
            latencyMillis = generation.latencyMillis
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
