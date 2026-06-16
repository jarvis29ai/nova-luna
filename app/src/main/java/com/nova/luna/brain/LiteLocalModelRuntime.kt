package com.nova.luna.brain

import java.io.File

/**
 * A local model runtime for the Lite model pack (qwen2.5-0.5b GGUF).
 * 
 * This runtime now routes through the native llama bridge, surfaces real generation
 * when it is usable, and keeps proof/diagnostic fields honest when it is not.
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
        "LiteLocalModelRuntime (Native - Gated)"
    }

    private var nativeLoaded = false
    private var loadedModelPath: String? = null
    private var modelLoadCount = 0L
    private var generationCallCount = 0L
    private var modelReused = false
    private var lastErrorReason: String? = null
    private var lastFailureReason: String? = null
    
    private var modelLoadMs = 0L
    private var promptEvalMs = 0L
    private var generationMs = 0L
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
    private var promptTokenIdsSample: String? = null
    private var generatedTokenIdsSample: String? = null
    private var jsonParseAttempted = false
    private var jsonParseSuccess = false
    private var realForwardPass = false
    private var nativeForwardPassCount = 0
    private var logitsComputed = false
    private var sampledFromModelLogits = false
    private var usableOutput = false
    private var nativeEngineStatus = "unknown"
    private var usableBrainStatus = "unknown"
    private var chatTemplateApplied = false
    private var chatTemplateSource: String? = null
    private var stopReason: String? = null
    private var repetitionDetected = false
    private var nativeError: String? = null
    private var parsedIntent: String? = null
    private var parsedRiskLevel: String? = null
    private var parsedActionType: String? = null
    private var confirmationRequired = false
    private var finishReason: String? = null

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
        if (modelFile.path.isBlank()) {
            return PhoneLocalLlmGenerationResult.unavailable(
                status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                reason = "MODEL_PATH_MISSING: Lite model path is blank.",
                modelId = modelId,
                modelDisplayName = modelDisplayName()
            )
        }

        if (!available()) {
            return PhoneLocalLlmGenerationResult.unavailable(
                status = PhoneLocalLlmStatus.MODEL_ASSET_MISSING,
                reason = "MODEL_FILE_NOT_FOUND: Lite model file not found at ${modelFile.path}",
                modelId = modelId,
                modelDisplayName = modelDisplayName()
            )
        }

        return try {
            generateNative(prompt, timeoutMs)
        } catch (e: Throwable) {
            resetNativeResultState()
            nativeLoaded = false
            loadedModelPath = null
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
            val loadDuration = (System.currentTimeMillis() - startLoad).coerceAtLeast(0L)
            modelLoadMs = loadDuration
            if (nativeLoaded) {
                modelLoadCount = if (modelLoadCount > 0L) modelLoadCount + 1L else 1L
                loadedModelPath = modelFile.absolutePath
                modelReused = false
            }
            
            if (!nativeLoaded) {
                resetNativeResultState()
                modelLoadMs = loadDuration
                lastFailureReason = "Native model load failed."
                lastErrorReason = "MODEL_LOAD_FAILED"
                loadedModelPath = null
                backendType = "native"
                return generateUnavailable(
                    status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                    reason = "Native model load failed.",
                    modelId = modelId,
                    modelDisplayName = modelDisplayName()
                )
            }
        }

        generationCallCount += 1
        modelReused = modelLoadCount > 0 && generationCallCount > 1 && nativeLoaded

        val result = nativeRuntime.generate(prompt, timeoutMs)
        updateDiagnosticsFromNativeResult(result)

        val outputText = result.decodedText?.trim()?.takeIf { it.isNotBlank() }
            ?: result.text?.trim()?.takeIf { it.isNotBlank() }

        if (result.success && result.hasUsableBrainOutput && outputText != null) {
            lastFailureReason = normalizeNativeNote(result.message ?: result.lastFailure ?: result.errorMessage)
                ?: "Real native generation succeeded."
            lastErrorReason = null
            return PhoneLocalLlmGenerationResult(
                status = PhoneLocalLlmStatus.READY,
                text = outputText,
                reason = result.message ?: "Real native generation succeeded.",
                modelId = modelId,
                modelDisplayName = modelDisplayName(),
                latencyMillis = result.latencyMillis.takeIf { it > 0 } ?: (result.loadMs + result.promptEvalMs + result.generationMs).takeIf { it > 0 },
                jsonOnly = result.jsonParseSuccess || outputText.trim().startsWith("{")
            )
        }

        val reason = result.message
            ?: result.lastFailure
            ?: result.errorMessage
            ?: result.errorCode
            ?: "Native generation did not produce usable text."

        lastFailureReason = normalizeNativeNote(reason) ?: "Native generation did not produce usable text."
        lastErrorReason = normalizeNativeNote(result.errorCode ?: result.lastError ?: result.errorMessage)
            ?: mapNativeErrorToStatus(result).wireValue.uppercase()

        return PhoneLocalLlmGenerationResult(
            status = mapNativeErrorToStatus(result),
            text = outputText,
            reason = reason,
            modelId = modelId,
            modelDisplayName = modelDisplayName(),
            latencyMillis = result.latencyMillis.takeIf { it > 0 } ?: (result.loadMs + result.promptEvalMs + result.generationMs).takeIf { it > 0 },
            jsonOnly = result.jsonParseSuccess || outputText?.trim()?.startsWith("{") == true
        )
    }

    private fun updateDiagnosticsFromNativeResult(result: NativeLlamaResult) {
        val decodedOutput = result.decodedText?.trim()?.takeIf { it.isNotBlank() }
            ?: result.text?.trim()?.takeIf { it.isNotBlank() }
        tokensGenerated = result.tokensGenerated
        contextSize = result.contextSize
        threadsUsed = result.threadsUsed
        modelArch = result.modelArch
        tensorsLoaded = result.tensorsLoaded
        modelDetected = result.modelDetected
        metadataParsed = result.metadataParsed
        tensorsWeightLoaded = result.tensorsWeightLoaded
        ggmlGraphCompute = result.ggmlGraphCompute
        logitsGenerated = result.logitsGenerated
        samplingActive = result.samplingActive
        deterministicResponse = result.deterministicResponse
        memoryEstimateBytes = result.memoryEstimateBytes
        promptEvalMs = result.promptEvalMs
        generationMs = result.generationMs
        tokensPerSecond = result.tokensPerSecond
        nativeBridgeStable = result.nativeBridgeStable
        jsonReturnBridge = result.jsonReturnBridge
        crashFree = result.crashFree
        tokenizerType = result.tokenizerType
        bosTokenId = result.bosTokenId
        eosTokenId = result.eosTokenId
        specialTokensCount = result.specialTokensCount
        if (result.tokenizerLoadMs > 0L) {
            tokenizerLoadMs = result.tokenizerLoadMs
        }
        ggmlGraphBuilt = result.ggmlGraphBuilt
        graphNodesCount = result.graphNodesCount
        memoryMappedMb = result.memoryMappedMb
        logitsFromModelWeights = result.logitsFromModelWeights
        decodedTokens = result.decodedTokens || decodedOutput != null
        sampledTokenIdsCount = result.generatedTokenIdsSample.size.takeIf { it > 0 } ?: result.sampledTokenIdsCount
        simulationActive = result.simulation
        decodedText = decodedOutput
        upstreamSamplerLinked = result.upstreamSamplerLinked
        backendType = result.backend.takeIf { it.isNotBlank() } ?: "native"
        realInference = result.realInference
        nativeGenerationAvailable = result.nativeGenerationAvailable
        realTokenIds = result.realTokenIds
        tokenizerLoaded = result.tokenizerLoaded && result.vocabSize > 0
        vocabSize = result.vocabSize
        tokenizationSuccess = result.tokenizationOk
        promptTokens = result.promptTokens
        tokenIdsPreview = result.tokenIdsPreview ?: result.promptTokenIdsSample.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]")
        promptTokenIdsSample = if (result.promptTokenIdsSample.isNotEmpty()) result.promptTokenIdsSample.joinToString(prefix = "[", postfix = "]") else result.tokenIdsPreview
        generatedTokenIdsSample = if (result.generatedTokenIdsSample.isNotEmpty()) result.generatedTokenIdsSample.joinToString(prefix = "[", postfix = "]") else null
        tokenizerType = result.tokenizerType
        bosTokenId = result.bosTokenId
        eosTokenId = result.eosTokenId
        specialTokensCount = result.specialTokensCount
        tokenizerLoadMs = result.tokenizerLoadMs
        val reportedModelLoadMs = result.loadMs.takeIf { it > 0 } ?: result.modelLoadMs
        if (reportedModelLoadMs > 0L) {
            modelLoadMs = reportedModelLoadMs
        }
        modelLoadCount = maxOf(modelLoadCount, result.modelLoadCount.toLong())
        generationCallCount = maxOf(generationCallCount, result.generationCallCount.toLong())
        modelReused = result.modelReused || modelReused
        jsonParseAttempted = result.jsonParseAttempted
        jsonParseSuccess = result.jsonParseSuccess
        realForwardPass = result.realForwardPass
        nativeForwardPassCount = result.nativeForwardPassCount
        logitsComputed = result.logitsComputed
        sampledFromModelLogits = result.sampledFromModelLogits
        usableOutput = result.usableOutput
        nativeEngineStatus = result.nativeEngineStatus
        usableBrainStatus = result.usableBrainStatus
        chatTemplateApplied = result.chatTemplateApplied
        chatTemplateSource = result.chatTemplateSource
        stopReason = result.stopReason
        repetitionDetected = result.repetitionDetected
        nativeError = result.nativeError
        parsedIntent = result.parsedIntent
        parsedRiskLevel = result.parsedRiskLevel
        parsedActionType = result.parsedActionType
        confirmationRequired = result.confirmationRequired
        finishReason = result.finishReason
        lastErrorReason = normalizeNativeNote(result.errorCode ?: result.lastError ?: result.errorMessage)
        lastFailureReason = normalizeNativeNote(result.message ?: result.lastFailure ?: result.errorMessage)
    }

    private fun mapNativeErrorToStatus(result: NativeLlamaResult): PhoneLocalLlmStatus {
        val errorCode = (result.errorCode ?: result.lastError ?: result.errorMessage)
            ?.trim()
            ?.uppercase()

        return when (errorCode) {
            "PROMPT_TOO_LONG", "PROMPT_TOO_LARGE" -> PhoneLocalLlmStatus.PROMPT_TOO_LARGE
            "DECODE_FAILED", "GENERATED_EOS_BEFORE_TEXT", "GENERATED_TOKEN_DECODED_EMPTY", "GENERATED_TOKENS_DECODED_EMPTY", "JSON_PARSE_FAILED", "NATIVE_OUTPUT_TOKENIZATION_FAILED" -> PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED
            "REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED" -> PhoneLocalLlmStatus.REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED
            "MODEL_RUNTIME_NOT_AVAILABLE",
            "MODEL_PATH_MISSING",
            "MODEL_FILE_NOT_FOUND",
            "MODEL_NOT_LOADED",
            "MODEL_LOAD_FAILED",
            "TOKENIZER_NOT_LOADED",
            "PROMPT_TOKENIZATION_FAILED",
            "EMPTY_PROMPT",
            "CONTEXT_CREATE_FAILED",
            "PROMPT_EVAL_FAILED",
            "TOKEN_SAMPLING_FAILED",
            "NATIVE_OOM_OR_ALLOCATION_FAILED",
            "GENERATION_TIMEOUT",
            "NATIVE_JNI_CALL_FAILED",
            "NATIVE_JNI_ERROR",
            "NATIVE_JSON_PARSE_FAILED",
            "NATIVE_LIBRARY_NOT_LOADED" -> PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE
            else -> {
                if (result.nativeGenerationAvailable || result.realInference || result.tokensGenerated > 0) {
                    PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED
                } else {
                    PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE
                }
            }
        }
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
        modelLoadMs = 0L
        promptEvalMs = 0L
        generationMs = 0L
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
        promptTokenIdsSample = null
        generatedTokenIdsSample = null
        jsonParseAttempted = false
        jsonParseSuccess = false
        usableOutput = false
        nativeEngineStatus = "unknown"
        usableBrainStatus = "unknown"
        chatTemplateApplied = false
        chatTemplateSource = null
        stopReason = null
        repetitionDetected = false
        nativeError = null
        parsedIntent = null
        parsedRiskLevel = null
        parsedActionType = null
        confirmationRequired = false
        finishReason = null
        realInference = false
        modelReused = false
        backendType = "native"
        lastErrorReason = null
        lastFailureReason = null
    }

    override fun cancel(): Boolean {
        return true
    }

    override fun unload(): Boolean {
        if (nativeLoaded) {
            try {
                nativeRuntime.unload()
            } catch (e: Throwable) {
                // Ignore cleanup errors
            }
            nativeLoaded = false
            loadedModelPath = null
            modelReused = false
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
            append("prompt_token_ids_sample=${promptTokenIdsSample ?: "none"}, ")
            append("generated_token_ids_sample=${generatedTokenIdsSample ?: "none"}, ")
            append("json_parse_attempted=$jsonParseAttempted, ")
            append("json_parse_success=$jsonParseSuccess, ")
            append("real_forward_pass=$realForwardPass, ")
            append("native_forward_pass_count=$nativeForwardPassCount, ")
            append("logits_computed=$logitsComputed, ")
            append("sampled_from_model_logits=$sampledFromModelLogits, ")
            append("usable_output=$usableOutput, ")
            append("native_engine_status=$nativeEngineStatus, ")
            append("usable_brain_status=$usableBrainStatus, ")
            append("chat_template_applied=$chatTemplateApplied, ")
            append("chat_template_source=${chatTemplateSource ?: "none"}, ")
            append("stop_reason=${stopReason ?: "none"}, ")
            append("repetition_detected=$repetitionDetected, ")
            append("native_error=${nativeError ?: "none"}, ")
            append("model_load_count=$modelLoadCount, ")
            append("generation_call_count=$generationCallCount, ")
            append("model_reused=$modelReused, ")
            append("load_ms=$modelLoadMs, ")
            append("generation_ms=$generationMs, ")
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
            append("parsed_intent=${parsedIntent ?: "none"}, ")
            append("parsed_risk_level=${parsedRiskLevel ?: "none"}, ")
            append("parsed_action_type=${parsedActionType ?: "none"}, ")
            append("confirmation_required=$confirmationRequired, ")
            append("finish_reason=${finishReason ?: "none"}, ")
            append("native_diagnostics=[$nativeDiag]")
        }
    }
}
