package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision

class PhoneLocalLlmRuntime(
    private val config: PhoneLocalLlmConfig = PhoneLocalLlmConfig.fromBuildConfig(),
    private val engine: PhoneLocalLlmEngine = UnavailablePhoneLocalLlmEngine(),
    private val assetLocator: ModelAssetLocator = ModelAssetLocator(),
    private val readinessChecker: ModelReadinessChecker = ModelReadinessChecker(assetLocator),
    private val promptBuilder: PhoneLocalLlmPromptBuilder = PhoneLocalLlmPromptBuilder(),
    private val outputParser: PhoneLocalLlmOutputParser = PhoneLocalLlmOutputParser(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) {
    private val runtimeConfig = config.sanitized()

    fun readinessStatus(): PhoneLocalLlmReadiness {
        return readinessChecker.readinessStatus(
            config = runtimeConfig,
            engineAvailable = engine.available()
        )
    }

    fun available(): Boolean = readinessStatus().available

    fun modelId(): PhoneLocalLlmModelId? = readinessStatus().selectedModelId

    fun modelDisplayName(): String? = readinessStatus().selectedModelDisplayName

    fun maxInputTokens(): Int = readinessStatus().maxInputTokens

    fun maxPromptChars(): Int = readinessStatus().maxPromptChars

    fun buildPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision
    ): String {
        val readiness = readinessStatus()
        val selectedModel = readiness.selectedModel ?: runtimeConfig.models.firstOrNull()
            ?: PhoneLocalLlmConfig.defaultModelStack().first()
        return promptBuilder.buildBrainActionPrompt(
            request = request,
            routeDecision = routeDecision,
            model = selectedModel,
            readiness = readiness
        )
    }

    fun buildSafeTextPrompt(
        request: BrainRequest,
        routeDecision: BrainRouteDecision
    ): String {
        val readiness = readinessStatus()
        val selectedModel = readiness.selectedModel ?: runtimeConfig.models.firstOrNull()
            ?: PhoneLocalLlmConfig.defaultModelStack().first()
        return promptBuilder.buildSafeTextPrompt(
            request = request,
            routeDecision = routeDecision,
            model = selectedModel,
            readiness = readiness
        )
    }

    fun generate(prompt: String): PhoneLocalLlmGenerationResult {
        val readiness = readinessStatus()
        if (!readiness.available) {
            return PhoneLocalLlmGenerationResult.unavailable(
                status = readiness.status,
                reason = readiness.reason,
                modelId = readiness.selectedModelId,
                modelDisplayName = readiness.selectedModelDisplayName
            )
        }

        val selectedModel = readiness.selectedModel ?: return PhoneLocalLlmGenerationResult.unavailable(
            status = PhoneLocalLlmStatus.MODEL_DISABLED,
            reason = "No phone-local model is selected."
        )

        if (prompt.length > readiness.maxPromptChars) {
            return PhoneLocalLlmGenerationResult.unavailable(
                status = PhoneLocalLlmStatus.PROMPT_TOO_LARGE,
                reason = "Prompt exceeded the safe local length limit.",
                modelId = selectedModel.id,
                modelDisplayName = selectedModel.id.displayName
            )
        }

        val startedAt = System.nanoTime()
        val result = runCatching {
            engine.generateJson(prompt, readiness.timeoutMs)
        }.getOrElse {
            return PhoneLocalLlmGenerationResult.unavailable(
                status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                reason = "Local LLM engine failed: ${it.message.orEmpty()}",
                modelId = selectedModel.id,
                modelDisplayName = selectedModel.id.displayName
            )
        }

        if (!result.success) {
            return result.copy(
                modelId = result.modelId ?: selectedModel.id,
                modelDisplayName = result.modelDisplayName ?: selectedModel.id.displayName,
                latencyMillis = result.latencyMillis ?: elapsedMillis(startedAt)
            )
        }

        return result.copy(
            modelId = result.modelId ?: selectedModel.id,
            modelDisplayName = result.modelDisplayName ?: selectedModel.id.displayName,
            latencyMillis = result.latencyMillis ?: elapsedMillis(startedAt)
        )
    }

    fun generateJson(prompt: String): PhoneLocalLlmGenerationResult {
        return generate(prompt)
    }

    fun cancel(): Boolean = engine.cancel()

    fun diagnostics(): String {
        val readiness = readinessStatus()
        return buildString {
            append("status=").append(readiness.status.wireValue)
            append(", runtimeAvailable=").append(readiness.runtimeAvailable)
            append(", assetMissing=").append(readiness.assetMissing)
            append(", reason=").append(readiness.reason)
            append(", availableModelIds=").append(readiness.availableModelIds.joinToString(",") { it.wireValue })
            append(", engine=").append(engine.diagnostics())
        }
    }

    fun generateBrainAction(
        request: BrainRequest,
        routeDecision: BrainRouteDecision
    ): BrainModelResult {
        val readiness = readinessStatus()
        if (!readiness.available) {
            return BrainModelResult.unavailable(
                role = BrainModelRole.GEMMA_REASONING,
                reason = readiness.reason,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Local phone LLM is unavailable.",
                    "BrainService will keep deterministic fallback behavior."
                ),
                localModelId = readiness.selectedModelId,
                localModelDisplayName = readiness.selectedModelDisplayName,
                localModelStatus = readiness.status,
                promptBuilt = false,
                jsonParsed = false
            )
        }

        val prompt = buildPrompt(request, routeDecision)
        val generation = generateJson(prompt)
        if (!generation.success) {
            return BrainModelResult.unavailable(
                role = BrainModelRole.GEMMA_REASONING,
                reason = generation.reason,
                rawResponse = generation.text,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Local phone LLM produced no usable output."
                ),
                localModelId = generation.modelId ?: readiness.selectedModelId,
                localModelDisplayName = generation.modelDisplayName ?: readiness.selectedModelDisplayName,
                localModelStatus = generation.status,
                promptBuilt = true,
                jsonParsed = false,
                latencyMillis = generation.latencyMillis
            )
        }

        val parseResult = outputParser.parse(generation.text.orEmpty())
        if (!parseResult.accepted || parseResult.candidateAction == null) {
            return BrainModelResult.unavailable(
                role = BrainModelRole.GEMMA_REASONING,
                reason = parseResult.reason,
                rawResponse = generation.text,
                candidateAction = parseResult.candidateAction,
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "Local phone LLM output was rejected by the strict output parser."
                ),
                localModelId = generation.modelId ?: readiness.selectedModelId,
                localModelDisplayName = generation.modelDisplayName ?: readiness.selectedModelDisplayName,
                localModelStatus = parseResult.status,
                promptBuilt = true,
                jsonParsed = false,
                latencyMillis = generation.latencyMillis
            )
        }

        val candidateAction = parseResult.candidateAction
        return BrainModelResult.available(
            role = BrainModelRole.GEMMA_REASONING,
            candidateAction = candidateAction,
            rawResponse = generation.text,
            reason = "Phone-local LLM produced a structured candidate.",
            safetyNotes = routeDecision.safetyNotes + listOf(
                "Local phone LLM output passed strict parsing."
            ),
            localModelId = generation.modelId ?: readiness.selectedModelId,
            localModelDisplayName = generation.modelDisplayName ?: readiness.selectedModelDisplayName,
            localModelStatus = parseResult.status,
            promptBuilt = true,
            jsonParsed = true,
            latencyMillis = generation.latencyMillis
        )
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
    }
}
