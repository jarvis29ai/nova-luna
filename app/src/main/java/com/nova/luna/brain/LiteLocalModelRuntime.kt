package com.nova.luna.brain

import java.io.File

/**
 * A local model runtime for the Lite model pack (qwen2.5-0.5b GGUF).
 * 
 * In this Phase 18 integration, it honestly reports the tokenizer depth via proof flags.
 */
class LiteLocalModelRuntime(
    private val modelFile: File,
    private val modelId: PhoneLocalLlmModelId = PhoneLocalLlmModelId.QWEN_0_5B,
    private val realInferenceEnabled: Boolean = false,
    private val nativeRuntime: NativeLlamaRuntime = LlamaCppJni()
) : PhoneLocalLlmEngine {

    override val engineName: String = if (realInferenceEnabled) {
        "LiteLocalModelRuntime (Native)"
    } else {
        "LiteLocalModelRuntime (Tokenizer Proof)"
    }

    private var nativeLoaded = false
    private var lastErrorReason: String? = null
    private var lastFailureReason: String? = null
    
    private var modelLoadMs = 0L
    private var promptEvalMs = 0L
    private var tokensGenerated = 0
    private var promptTokens = 0
    private var tokensPerSecond = 0.0
    private var contextSize = 0
    private var threadsUsed = 0
    private var backendType = "native"
    private var realInference = false
    private var modelArch: String? = null
    private var vocabSize = 0
    private var tensorsLoaded = 0
    private var metadataParsed = false
    private var tokenizerLoaded = false
    private var modelDetected = false
    private var realTokenIds = false
    private var nativeGenerationAvailable = false
    private var tensorsWeightLoaded = false
    private var ggmlGraphCompute = false
    private var logitsGenerated = false
    private var samplingActive = false
    private var deterministicResponse = true
    private var memoryEstimateBytes = 0L
    private var nativeBridgeStable = true
    private var jsonReturnBridge = true
    private var crashFree = true
    private var tokenizerType: String? = null
    private var bosTokenId = -1
    private var eosTokenId = -1
    private var specialTokensCount = 0
    private var tokenizationSuccess = false
    private var tokenizerLoadMs = 0L
    private var ggmlGraphBuilt = false
    private var graphNodesCount = 0
    private var memoryMappedMb = 0
    private var logitsFromModelWeights = false
    private var decodedTokens = false
    private var sampledTokenIdsCount = 0
    private var simulationActive = false
    private var decodedText: String? = null
    private var upstreamSamplerLinked = false
    private var tokenIdsPreview: String? = null

    override fun available(): Boolean {
        return modelFile.exists() && modelFile.isFile
    }

    override fun readinessStatus(): PhoneLocalLlmStatus {
        return if (available()) PhoneLocalLlmStatus.READY else PhoneLocalLlmStatus.MODEL_ASSET_MISSING
    }

    override fun modelId(): PhoneLocalLlmModelId = modelId

    override fun modelDisplayName(): String = "Qwen 2.5 0.5B (Lite)"

    override fun maxInputTokens(): Int = 2048

    override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        if (!available()) {
            return PhoneLocalLlmGenerationResult.unavailable(
                status = PhoneLocalLlmStatus.MODEL_ASSET_MISSING,
                reason = "Lite model file not found at ${modelFile.path}",
                modelId = modelId,
                modelDisplayName = modelDisplayName()
            )
        }

        return try {
            generateNative(prompt, timeoutMs)
        } catch (e: Throwable) {
            resetNativeResultState()
            nativeLoaded = false
            lastFailureReason = "Native crash: ${e.message ?: "Unknown error"}"
            lastErrorReason = lastFailureReason
            realInference = false
            nativeGenerationAvailable = false
            backendType = "failed_native"
            generateUnavailable(
                status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                reason = "Native runtime crashed. ${lastFailureReason ?: "Unknown error"}",
                modelId = modelId,
                modelDisplayName = modelDisplayName()
            )
        }
    }

    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()

    private fun generateNative(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        if (!nativeLoaded) {
            val startLoad = System.currentTimeMillis()
            nativeLoaded = nativeRuntime.loadModel(modelFile)
            modelLoadMs = System.currentTimeMillis() - startLoad
            
            if (!nativeLoaded) {
                resetNativeResultState()
                lastFailureReason = "Native model load failed."
                lastErrorReason = lastFailureReason
                backendType = "failed_native"
                return generateUnavailable(
                    status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                    reason = "Native model load failed.",
                    modelId = modelId,
                    modelDisplayName = modelDisplayName()
                )
            }
        }

        val result = nativeRuntime.generate(prompt, timeoutMs)
        updateDiagnosticsFromNativeResult(result)

        val reason = buildString {
            append(
                result.message
                    ?: result.errorMessage
                    ?: result.lastFailure
                    ?: result.lastError
                    ?: "Tokenizer is available, but real native generation is not implemented yet."
            )
            if (result.nativeGenerationAvailable || result.realInference || result.success) {
                append(" Real native generation remains disabled in Phase 19.1.")
            }
        }

        lastFailureReason = normalizeNativeNote(result.message ?: result.lastFailure ?: result.errorMessage)
            ?: "Tokenizer is available, but real native generation is not implemented yet."
        lastErrorReason = normalizeNativeNote(result.errorCode ?: result.lastError ?: result.errorMessage)
            ?: "REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED"

        return generateUnavailable(
            status = if (result.backend == "failed_native") {
                PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE
            } else {
                PhoneLocalLlmStatus.REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED
            },
            reason = reason,
            modelId = modelId,
            modelDisplayName = modelDisplayName()
        ).copy(
            latencyMillis = result.latencyMillis
        )
    }

    private fun updateDiagnosticsFromNativeResult(result: NativeLlamaResult) {
        val tokenizerProofAvailable = result.modelDetected || result.tokenizerLoaded || result.backend != "failed_native"
        val promptTokenProof = result.tokenizationOk && result.promptTokens > 0
        tokensGenerated = 0
        contextSize = result.contextSize
        threadsUsed = result.threadsUsed
        modelArch = result.modelArch
        tensorsLoaded = result.tensorsLoaded
        modelDetected = tokenizerProofAvailable
        metadataParsed = result.metadataParsed
        tensorsWeightLoaded = result.tensorsWeightLoaded && tokenizerProofAvailable
        ggmlGraphCompute = result.ggmlGraphCompute && tokenizerProofAvailable
        logitsGenerated = false
        samplingActive = false
        deterministicResponse = true
        memoryEstimateBytes = result.memoryEstimateBytes
        promptEvalMs = result.promptEvalMs
        tokensPerSecond = 0.0
        nativeBridgeStable = result.nativeBridgeStable
        jsonReturnBridge = result.jsonReturnBridge
        crashFree = result.crashFree
        tokenizerType = result.tokenizerType
        bosTokenId = result.bosTokenId
        eosTokenId = result.eosTokenId
        specialTokensCount = result.specialTokensCount
        tokenizerLoadMs = result.tokenizerLoadMs
        ggmlGraphBuilt = result.ggmlGraphBuilt && tokenizerProofAvailable
        graphNodesCount = result.graphNodesCount
        memoryMappedMb = result.memoryMappedMb
        logitsFromModelWeights = false
        decodedTokens = false
        sampledTokenIdsCount = 0
        simulationActive = false
        decodedText = null
        upstreamSamplerLinked = result.upstreamSamplerLinked
        backendType = if (result.backend == "failed_native") {
            "failed_native"
        } else {
            "native"
        }
        realInference = false
        nativeGenerationAvailable = false
        realTokenIds = result.realTokenIds || promptTokenProof
        tokenizerLoaded = tokenizerProofAvailable && result.tokenizerLoaded && result.vocabSize > 0
        vocabSize = if (tokenizerLoaded) result.vocabSize else 0
        tokenizationSuccess = tokenizerLoaded && result.tokenizationOk
        promptTokens = if (tokenizationSuccess) result.promptTokens else 0
        tokenIdsPreview = if (tokenizationSuccess) result.tokenIdsPreview else null
        tokenizerType = if (tokenizerLoaded) result.tokenizerType else null
        bosTokenId = if (tokenizerLoaded) result.bosTokenId else -1
        eosTokenId = if (tokenizerLoaded) result.eosTokenId else -1
        specialTokensCount = if (tokenizerLoaded) result.specialTokensCount else 0
        tokenizerLoadMs = if (tokenizerLoaded) result.tokenizerLoadMs else 0L
        lastErrorReason = normalizeNativeNote(result.errorCode ?: result.lastError ?: result.errorMessage)
            ?: normalizeNativeNote(result.lastError)
            ?: normalizeNativeNote(result.errorMessage)
        lastFailureReason = normalizeNativeNote(result.message ?: result.lastFailure)
            ?: normalizeNativeNote(result.lastFailure)
            ?: normalizeNativeNote(result.errorMessage)
    }

    private fun normalizeNativeNote(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (trimmed.equals("none", ignoreCase = true)) null else trimmed
    }

    private fun generateUnavailable(
        status: PhoneLocalLlmStatus,
        reason: String,
        modelId: PhoneLocalLlmModelId,
        modelDisplayName: String
    ): PhoneLocalLlmGenerationResult {
        return PhoneLocalLlmGenerationResult.unavailable(
            status = status,
            reason = reason,
            modelId = modelId,
            modelDisplayName = modelDisplayName
        )
    }

    private fun resetNativeResultState() {
        tokensGenerated = 0
        promptTokens = 0
        tokensPerSecond = 0.0
        contextSize = 0
        threadsUsed = 0
        modelArch = null
        vocabSize = 0
        tensorsLoaded = 0
        modelDetected = false
        realTokenIds = false
        nativeGenerationAvailable = false
        metadataParsed = false
        tokenizerLoaded = false
        tensorsWeightLoaded = false
        ggmlGraphCompute = false
        logitsGenerated = false
        samplingActive = false
        deterministicResponse = true
        memoryEstimateBytes = 0L
        nativeBridgeStable = true
        jsonReturnBridge = true
        crashFree = true
        tokenizerType = null
        bosTokenId = -1
        eosTokenId = -1
        specialTokensCount = 0
        tokenizationSuccess = false
        tokenizerLoadMs = 0L
        ggmlGraphBuilt = false
        graphNodesCount = 0
        memoryMappedMb = 0
        logitsFromModelWeights = false
        decodedTokens = false
        sampledTokenIdsCount = 0
        simulationActive = false
        decodedText = null
        upstreamSamplerLinked = false
        tokenIdsPreview = null
        realInference = false
    }

    override fun cancel(): Boolean {
        if (nativeLoaded) {
            try {
                nativeRuntime.unload()
            } catch (e: Throwable) {
                // Ignore cleanup errors
            }
            nativeLoaded = false
        }
        return true
    }

    override fun diagnostics(): String {
        val nativeDiag = try { nativeRuntime.diagnostics() } catch (e: Throwable) { "native diagnostics error: ${e.message}" }
        val failure = lastFailureReason ?: "none"
        val error = lastErrorReason ?: "none"
        val memoryMb = memoryEstimateBytes / (1024 * 1024)
        val tpsStr = "%.2f".format(java.util.Locale.US, tokensPerSecond)
        return buildString {
            append("engine=$engineName, ")
            append("path=${modelFile.absolutePath}, ")
            append("ready=${available()}, ")
            append("real_inference=$realInference, ")
            append("model_detected=$modelDetected, ")
            append("real_token_ids=$realTokenIds, ")
            append("native_generation_available=$nativeGenerationAvailable, ")
            append("simulation=$simulationActive, ")
            append("simulation_active=$simulationActive, ")
            append("backend=$backendType, ")
            append("tokenizer_loaded=$tokenizerLoaded, ")
            append("vocab_size=$vocabSize, ")
            append("tokenization_ok=$tokenizationSuccess, ")
            append("prompt_tokens=$promptTokens, ")
            append("decoded_text=${decodedText ?: "none"}, ")
            append("token_ids_preview=${tokenIdsPreview ?: "none"}, ")
            append("error=$error, ")
            append("message=$failure, ")
            append("last_error=$error, ")
            append("last_failure=$failure, ")
            append("bridge_stable=$nativeBridgeStable, ")
            append("json_bridge=$jsonReturnBridge, ")
            append("crash_free=$crashFree, ")
            append("metadata_parsed=$metadataParsed, ")
            append("tokenizer_type=${tokenizerType ?: "none"}, ")
            append("bos_id=$bosTokenId, ")
            append("eos_id=$eosTokenId, ")
            append("special_tokens=$specialTokensCount, ")
            append("tensors_loaded=$tensorsWeightLoaded, ")
            append("graph_built=$ggmlGraphBuilt, ")
            append("graph_compute=$ggmlGraphCompute, ")
            append("nodes=$graphNodesCount, ")
            append("mmap_mb=$memoryMappedMb, ")
            append("logits=$logitsGenerated, ")
            append("logits_weights=$logitsFromModelWeights, ")
            append("sampling=$samplingActive, ")
            append("upstream_sampler=$upstreamSamplerLinked, ")
            append("sampled_ids=$sampledTokenIdsCount, ")
            append("decoded_tokens=$decodedTokens, ")
            append("deterministic=$deterministicResponse, ")
            append("memory_estimate=${memoryMb}MB, ")
            append("model_arch=${modelArch ?: "none"}, ")
            append("tensors_count=$tensorsLoaded, ")
            append("native_loaded=$nativeLoaded, ")
            append("model_load_ms=$modelLoadMs, ")
            append("tokenizer_load_ms=$tokenizerLoadMs, ")
            append("prompt_eval_ms=$promptEvalMs, ")
            append("tokens_generated=$tokensGenerated, ")
            append("tps=$tpsStr, ")
            append("context_size=$contextSize, ")
            append("threads=$threadsUsed, ")
            append("native_diagnostics=[$nativeDiag]")
        }
    }
}
