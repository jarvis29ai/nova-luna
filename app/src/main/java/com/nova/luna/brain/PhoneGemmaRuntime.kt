package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.BrainRuntimeStatus
import java.io.File

data class PhoneGemmaRuntimeReadiness(
    val modelPathConfigured: Boolean,
    val modelFileExists: Boolean,
    val runtimeAvailable: Boolean,
    val modelLoaded: Boolean,
    val fallbackActive: Boolean,
    val reason: String,
    val selectedBrainRole: BrainModelRole
)

interface PhoneGemmaRuntimeBackend {
    val backendName: String

    fun isRuntimeAvailable(): Boolean

    fun generate(prompt: String, config: GemmaPhoneConfig): String
}

class UnavailablePhoneGemmaRuntimeBackend : PhoneGemmaRuntimeBackend {
    override val backendName: String = "UnavailablePhoneGemmaRuntimeBackend"

    override fun isRuntimeAvailable(): Boolean = false

    override fun generate(prompt: String, config: GemmaPhoneConfig): String {
        throw IllegalStateException("No phone Gemma inference backend is wired yet.")
    }
}

class PhoneGemmaRuntime(
    private val config: GemmaPhoneConfig = GemmaPhoneConfig.fromBuildConfig(),
    private val backend: PhoneGemmaRuntimeBackend = UnavailablePhoneGemmaRuntimeBackend(),
    private val modelPathExists: (String) -> Boolean = { path -> path.isNotBlank() && File(path).exists() },
    private val promptBuilder: (BrainRequest, BrainRouteDecision, GemmaPhoneConfig) -> String = { request, routeDecision, runtimeConfig ->
        buildString {
            append(BrainSystemPrompt.build(request))
            append("\n\nGemma phone runtime notes:\n")
            append("- selectedRole: ").append(routeDecision.selectedRole.wireValue).append('\n')
            append("- routeReason: ").append(routeDecision.reason).append('\n')
            append("- gemmaEnabled: ").append(runtimeConfig.gemmaEnabled).append('\n')
            append("- gemmaRoleEnabled: ").append(runtimeConfig.gemmaRoleEnabled).append('\n')
            append("- modelPath: ").append(runtimeConfig.gemmaModelAssetPath.ifBlank { "<not configured>" }).append('\n')
            append("- maxTokens: ").append(runtimeConfig.gemmaMaxTokens).append('\n')
            append("- temperature: ").append(runtimeConfig.gemmaTemperature).append('\n')
            append("- topK: ").append(runtimeConfig.gemmaTopK).append('\n')
            append("- contextWindow: ").append(runtimeConfig.gemmaContextWindow)
        }
    }
) {
    private val runtimeConfig = config.sanitized()
    private val localRuntime = PhoneLocalLlmRuntime(
        config = runtimeConfig.toPhoneLocalLlmConfig(),
        engine = object : PhoneLocalLlmEngine {
            override val engineName: String = backend.backendName

            override fun available(): Boolean = backend.isRuntimeAvailable()

            override fun readinessStatus(): PhoneLocalLlmStatus {
                return if (backend.isRuntimeAvailable()) {
                    PhoneLocalLlmStatus.READY
                } else {
                    PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE
                }
            }

            override fun modelId(): PhoneLocalLlmModelId? = PhoneLocalLlmModelId.GEMMA_3N

            override fun modelDisplayName(): String? = PhoneLocalLlmModelId.GEMMA_3N.displayName

            override fun maxInputTokens(): Int = runtimeConfig.gemmaMaxTokens

            override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
                if (!backend.isRuntimeAvailable()) {
                    return PhoneLocalLlmGenerationResult.unavailable(
                        status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                        reason = "No phone Gemma inference backend is wired yet.",
                        modelId = PhoneLocalLlmModelId.GEMMA_3N,
                        modelDisplayName = PhoneLocalLlmModelId.GEMMA_3N.displayName
                    )
                }

                return runCatching {
                    val response = backend.generate(prompt, runtimeConfig).trim()
                    if (response.isBlank()) {
                        PhoneLocalLlmGenerationResult.unavailable(
                            status = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                            reason = "Gemma backend returned an empty response.",
                            modelId = PhoneLocalLlmModelId.GEMMA_3N,
                            modelDisplayName = PhoneLocalLlmModelId.GEMMA_3N.displayName
                        )
                    } else {
                        PhoneLocalLlmGenerationResult(
                            status = PhoneLocalLlmStatus.READY,
                            text = response,
                            reason = "Gemma backend returned a response.",
                            modelId = PhoneLocalLlmModelId.GEMMA_3N,
                            modelDisplayName = PhoneLocalLlmModelId.GEMMA_3N.displayName,
                            jsonOnly = true
                        )
                    }
                }.getOrElse {
                    PhoneLocalLlmGenerationResult.unavailable(
                        status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                        reason = "Gemma backend failed: ${it.message.orEmpty()}",
                        modelId = PhoneLocalLlmModelId.GEMMA_3N,
                        modelDisplayName = PhoneLocalLlmModelId.GEMMA_3N.displayName
                    )
                }
            }

            override fun cancel(): Boolean = false

            override fun unload(): Boolean = true

            override fun diagnostics(): String = "backend=${backend.backendName}, available=${backend.isRuntimeAvailable()}"
        },
        assetLocator = ModelAssetLocator(modelPathExists),
        readinessChecker = ModelReadinessChecker(ModelAssetLocator(modelPathExists)),
        promptBuilder = PhoneLocalLlmPromptBuilder(),
        outputParser = PhoneLocalLlmOutputParser(BrainActionJsonCodec(), BrainActionValidator()),
        codec = BrainActionJsonCodec()
    )

    fun readinessStatus(
        selectedBrainRole: BrainModelRole = BrainModelRole.GEMMA_REASONING,
        fallbackActive: Boolean = false
    ): PhoneGemmaRuntimeReadiness {
        val modelPathConfigured = runtimeConfig.modelPathConfigured
        val modelFileExists = modelPathConfigured && modelPathExists(runtimeConfig.gemmaModelAssetPath)
        val backendAvailable = backend.isRuntimeAvailable()
        val runtimeAvailable = runtimeConfig.runtimeFeatureEnabled && backendAvailable
        val modelLoaded = runtimeAvailable && modelPathConfigured && modelFileExists
        val reason = when {
            !runtimeConfig.gemmaEnabled -> "Gemma phone runtime is disabled."
            !runtimeConfig.gemmaRoleEnabled -> "Gemma reasoning role is disabled."
            !modelPathConfigured -> "Gemma model asset path is not configured."
            !modelFileExists -> "Gemma model asset does not exist at the configured path."
            !backendAvailable -> "No phone Gemma inference backend is wired yet."
            !modelLoaded -> "Gemma phone runtime is not loaded."
            else -> "Gemma phone runtime is ready."
        }

        return PhoneGemmaRuntimeReadiness(
            modelPathConfigured = modelPathConfigured,
            modelFileExists = modelFileExists,
            runtimeAvailable = runtimeAvailable,
            modelLoaded = modelLoaded,
            fallbackActive = fallbackActive || !modelLoaded,
            reason = reason,
            selectedBrainRole = selectedBrainRole
        )
    }

    fun isReady(): Boolean {
        return readinessStatus().modelLoaded
    }

    fun localReadinessStatus(): PhoneLocalLlmReadiness = localRuntime.readinessStatus()

    fun modelId(): PhoneLocalLlmModelId? = localRuntime.modelId()

    fun modelDisplayName(): String? = localRuntime.modelDisplayName()

    fun maxInputTokens(): Int = localRuntime.maxInputTokens()

    fun buildPrompt(request: BrainRequest, routeDecision: BrainRouteDecision): String {
        return localRuntime.buildPrompt(request, routeDecision)
    }

    fun buildSafeTextPrompt(request: BrainRequest, routeDecision: BrainRouteDecision): String {
        return localRuntime.buildSafeTextPrompt(request, routeDecision)
    }

    fun generateJson(prompt: String): PhoneLocalLlmGenerationResult {
        return localRuntime.generateJson(prompt)
    }

    fun generateBrainAction(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        val result = localRuntime.generateBrainAction(request, routeDecision)
        if (result.available) {
            return result
        }

        val legacyReason = when {
            !runtimeConfig.gemmaEnabled -> "Gemma phone runtime is disabled."
            !runtimeConfig.gemmaRoleEnabled -> "Gemma reasoning role is disabled."
            !runtimeConfig.modelPathConfigured -> "Gemma model asset path is not configured."
            !modelPathExists(runtimeConfig.gemmaModelAssetPath) -> "Gemma model asset does not exist at the configured path."
            !backend.isRuntimeAvailable() -> "No phone Gemma inference backend is wired yet."
            else -> result.reason
        }

        return result.copy(reason = legacyReason)
    }

    fun cancel(): Boolean = localRuntime.cancel()

    fun diagnostics(): String = localRuntime.diagnostics()

    private fun buildLegacyPrompt(request: BrainRequest, routeDecision: BrainRouteDecision): String {
        return promptBuilder(request, routeDecision, runtimeConfig)
    }

    fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): String? {
        val readiness = readinessStatus(selectedBrainRole = routeDecision.selectedRole)
        if (!readiness.modelLoaded) {
            return null
        }

        return runCatching {
            backend.generate(buildLegacyPrompt(request, routeDecision), runtimeConfig).trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun describeStatus(
        selectedBrainRole: BrainModelRole = BrainModelRole.GEMMA_REASONING,
        fallbackActive: Boolean = false
    ): String {
        val readiness = readinessStatus(selectedBrainRole = selectedBrainRole, fallbackActive = fallbackActive)
        return buildString {
            append("selectedBrainRole=").append(readiness.selectedBrainRole.wireValue)
            append(", modelPathConfigured=").append(readiness.modelPathConfigured)
            append(", modelFileExists=").append(readiness.modelFileExists)
            append(", runtimeAvailable=").append(readiness.runtimeAvailable)
            append(", modelLoaded=").append(readiness.modelLoaded)
            append(", fallbackActive=").append(readiness.fallbackActive)
            append(", reason=").append(readiness.reason)
        }
    }
}
