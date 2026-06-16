package com.nova.luna.brain

import com.nova.luna.modelinstall.SimpleJson
import com.nova.luna.diagnostics.NativeProofStage
import java.io.File
import android.util.Log

/**
 * JNI bridge for llama.cpp.
 */
class LlamaCppJni : NativeLlamaRuntime {

    private var modelLoaded = false
    private var loadedModelPath: String? = null
    private var modelLoadCount = 0L
    private var generationCallCount = 0L
    private var modelReused = false
    private var lastLoadMs = 0L
    private var lastError: String? = null
    private var lastFailure: String? = null
    private var lastParsedResult: NativeLlamaResult? = null

    override fun loadModel(modelFile: File): Boolean {
        if (!libraryLoaded) {
            lastError = "NATIVE_LIBRARY_NOT_LOADED"
            lastFailure = "NATIVE_LIBRARY_NOT_LOADED: Native library 'llama-jni' not loaded."
            lastParsedResult = null
            return false
        }

        if (modelFile.path.isBlank()) {
            lastError = "MODEL_PATH_MISSING"
            lastFailure = "MODEL_PATH_MISSING: Model path is blank."
            lastParsedResult = null
            return false
        }

        if (!modelFile.exists()) {
            lastError = "MODEL_FILE_NOT_FOUND"
            lastFailure = "MODEL_FILE_NOT_FOUND: Model file not found: ${modelFile.path}"
            lastParsedResult = null
            return false
        }

        if (isLoaded() && loadedModelPath == modelFile.absolutePath) {
            modelReused = true
            lastError = null
            lastFailure = null
            return true
        }

        if (isLoaded() && loadedModelPath != null) {
            runCatching { nativeReleaseModel() }
        }

        return try {
            val startTime = System.currentTimeMillis()
            val result = nativeLoadModel(modelFile.absolutePath)
            lastLoadMs = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
            modelLoaded = result
            loadedModelPath = if (result) modelFile.absolutePath else null
            if (result) {
                modelLoadCount += 1
                modelReused = modelLoadCount > 1
            } else {
                modelReused = false
            }
            lastParsedResult = null
            if (result) {
                lastError = null
                lastFailure = null
            } else {
                lastError = "MODEL_LOAD_FAILED"
                lastFailure = "MODEL_LOAD_FAILED: Native model load failed."
            }
            result
        } catch (e: Throwable) {
            lastError = "NATIVE_JNI_ERROR"
            lastFailure = "NATIVE_JNI_ERROR: Native error: ${e.message}"
            lastParsedResult = null
            logE(TAG, "Error loading model", e)
            false
        }
    }

    fun runProofStage(
        stage: NativeProofStage,
        modelFile: File,
        prompt: String,
        timeoutMs: Long,
        maxTokens: Int = 1
    ): NativeLlamaResult {
        if (!libraryLoaded) {
            return NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Native library 'llama-jni' not loaded.",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "NATIVE_LIBRARY_NOT_LOADED",
                message = "Native library 'llama-jni' not loaded.",
                modelLoaded = false,
                finishReason = "error"
            ).also {
                lastParsedResult = it
                lastError = it.lastError
                lastFailure = it.lastFailure
            }
        }

        if (modelFile.path.isBlank()) {
            return NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Model path was blank.",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "MODEL_PATH_MISSING",
                message = "Model path was blank.",
                modelLoaded = false,
                finishReason = "error"
            ).also {
                lastParsedResult = it
                lastError = it.lastError
                lastFailure = it.lastFailure
            }
        }

        val rawJson = try {
            nativeRunProofStage(
                stage.name,
                modelFile.absolutePath,
                prompt.trim(),
                maxTokens.coerceAtLeast(1),
                timeoutMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        } catch (e: Throwable) {
            logE(TAG, "Critical JNI abort/crash during proof stage ${stage.name}", e)
            null
        }

        if (rawJson == null) {
            return NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Native proof stage returned null or aborted.",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "NATIVE_JNI_CALL_FAILED",
                message = "Native proof stage returned null or aborted.",
                modelLoaded = false,
                finishReason = "error"
            ).also {
                lastParsedResult = it
                lastError = it.lastError
                lastFailure = it.lastFailure
            }
        }

        return try {
            parseNativeJson(rawJson).also { parsed ->
                lastParsedResult = parsed
                lastError = parsed.lastError ?: parsed.errorMessage
                lastFailure = parsed.lastFailure ?: parsed.lastError ?: parsed.errorMessage
            }
        } catch (e: Throwable) {
            logE(TAG, "Failed to parse native proof JSON: $rawJson", e)
            NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Malformed native proof JSON: ${e.message}",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "NATIVE_JSON_PARSE_FAILED",
                message = "Malformed native proof JSON: ${e.message}",
                modelLoaded = false,
                finishReason = "error"
            ).also {
                lastParsedResult = it
                lastError = it.lastError
                lastFailure = it.lastFailure
            }
        }
    }

    override fun generate(prompt: String, timeoutMs: Long): NativeLlamaResult {
        return generate(prompt, timeoutMs, DEFAULT_MAX_TOKENS)
    }

    fun generate(prompt: String, timeoutMs: Long, maxTokens: Int): NativeLlamaResult {
        generationCallCount += 1
        modelReused = modelLoadCount > 0 && generationCallCount > 1 && modelLoaded

        if (!libraryLoaded) {
            val result = NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Native library 'llama-jni' not loaded.",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "NATIVE_LIBRARY_NOT_LOADED",
                message = "Native library 'llama-jni' not loaded.",
                modelLoaded = false,
                modelLoadCount = modelLoadCount.toInt(),
                generationCallCount = generationCallCount.toInt(),
                loadMs = lastLoadMs,
                modelLoadMs = lastLoadMs,
                finishReason = "error"
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            return result
        }

        if (!isLoaded()) {
            val result = NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Model not loaded.",
                backendType = "native",
                backend = "native",
                errorCode = "MODEL_NOT_LOADED",
                message = "Model not loaded.",
                modelLoaded = false,
                modelLoadCount = modelLoadCount.toInt(),
                generationCallCount = generationCallCount.toInt(),
                loadMs = lastLoadMs,
                modelLoadMs = lastLoadMs,
                finishReason = "error"
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            return result
        }

        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            val result = NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Prompt was empty.",
                backendType = "native",
                backend = "native",
                modelDetected = true,
                tokenizerLoaded = true,
                realTokenIds = false,
                realInference = false,
                nativeGenerationAvailable = false,
                tokensGenerated = 0,
                promptTokens = 0,
                decodedText = null,
                errorCode = "EMPTY_PROMPT",
                message = "Prompt was empty.",
                modelLoaded = true,
                modelReused = modelReused,
                modelLoadCount = modelLoadCount.toInt(),
                generationCallCount = generationCallCount.toInt(),
                loadMs = lastLoadMs,
                modelLoadMs = lastLoadMs,
                finishReason = "error"
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            return result
        }

        val rawJson = try {
            nativeGenerate(
                trimmedPrompt,
                maxTokens,
                DEFAULT_TEMPERATURE,
                DEFAULT_TOP_K,
                DEFAULT_TOP_P,
                timeoutMs.coerceAtLeast(0).toInt()
            )
        } catch (e: Throwable) {
            logE(TAG, "Critical JNI abort/crash during generate", e)
            null
        }

        if (rawJson == null) {
            val result = NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Native JNI call returned null or aborted.",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "NATIVE_JNI_CALL_FAILED",
                message = "Native JNI call returned null or aborted.",
                modelLoaded = true,
                modelReused = modelReused,
                modelLoadCount = modelLoadCount.toInt(),
                generationCallCount = generationCallCount.toInt(),
                loadMs = lastLoadMs,
                modelLoadMs = lastLoadMs,
                finishReason = "error"
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            return result
        }

        return try {
            val parsed = parseNativeJson(rawJson)
            lastParsedResult = parsed
            lastError = parsed.lastError ?: parsed.errorMessage
            lastFailure = parsed.lastFailure ?: parsed.lastError ?: parsed.errorMessage
            parsed
        } catch (e: Throwable) {
            logE(TAG, "Failed to parse native JSON: $rawJson", e)
            val result = NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Malformed native JSON: ${e.message}",
                backendType = "failed_native",
                backend = "failed_native",
                errorCode = "NATIVE_JSON_PARSE_FAILED",
                message = "Malformed native JSON: ${e.message}",
                modelLoaded = true,
                modelReused = modelReused,
                modelLoadCount = modelLoadCount.toInt(),
                generationCallCount = generationCallCount.toInt(),
                loadMs = lastLoadMs,
                modelLoadMs = lastLoadMs,
                finishReason = "error"
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            result
        }
    }

    internal fun parseNativeJson(json: String): NativeLlamaResult {
        val root = runCatching { SimpleJson.parseObject(json) }
            .getOrElse { throwable ->
                throw IllegalArgumentException("Malformed native JSON: ${throwable.message}", throwable)
            }

        fun normalize(value: String?): String? {
            val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return if (trimmed.equals("none", ignoreCase = true)) null else trimmed
        }

        fun stringAny(vararg keys: String): String? {
            for (key in keys) {
                when (val value = root[key]) {
                    null -> continue
                    is String -> return value
                    else -> error("Expected string value for '$key'")
                }
            }
            return null
        }

        fun boolAny(vararg keys: String): Boolean {
            for (key in keys) {
                when (val value = root[key]) {
                    null -> continue
                    is Boolean -> return value
                    is String -> return value.toBooleanStrictOrNull()
                        ?: error("Expected boolean value for '$key'")
                    else -> error("Expected boolean value for '$key'")
                }
            }
            return false
        }

        fun intAny(vararg keys: String): Int {
            for (key in keys) {
                when (val value = root[key]) {
                    null -> continue
                    is Number -> return value.toInt()
                    is String -> return value.toIntOrNull()
                        ?: error("Expected numeric value for '$key'")
                    else -> error("Expected numeric value for '$key'")
                }
            }
            return 0
        }

        fun longAny(vararg keys: String): Long {
            for (key in keys) {
                when (val value = root[key]) {
                    null -> continue
                    is Number -> return value.toLong()
                    is String -> return value.toLongOrNull()
                        ?: error("Expected numeric value for '$key'")
                    else -> error("Expected numeric value for '$key'")
                }
            }
            return 0L
        }

        fun doubleAny(vararg keys: String): Double {
            for (key in keys) {
                when (val value = root[key]) {
                    null -> continue
                    is Number -> return value.toDouble()
                    is String -> return value.toDoubleOrNull()
                        ?: error("Expected numeric value for '$key'")
                    else -> error("Expected numeric value for '$key'")
                }
            }
            return 0.0
        }

        fun intListAny(vararg keys: String): List<Int> {
            for (key in keys) {
                when (val value = root[key]) {
                    null -> continue
                    is List<*> -> return value.map { item ->
                        when (item) {
                            null -> error("Expected numeric value inside array '$key'")
                            is Number -> item.toInt()
                            is String -> item.toIntOrNull()
                                ?: error("Expected numeric value inside array '$key'")
                            else -> error("Expected numeric value inside array '$key'")
                        }
                    }
                    is String -> {
                        val parsed = runCatching { SimpleJson.parseArray(value) }
                            .getOrElse { error("Expected array value for '$key'") }
                        return parsed.map { item ->
                            when (item) {
                                null -> error("Expected numeric value inside array '$key'")
                                is Number -> item.toInt()
                                is String -> item.toIntOrNull()
                                    ?: error("Expected numeric value inside array '$key'")
                                else -> error("Expected numeric value inside array '$key'")
                            }
                        }
                    }
                    else -> error("Expected array value for '$key'")
                }
            }
            return emptyList()
        }

        val success = boolAny("success", "ok")
        val backend = stringAny("backend", "backendType")?.takeIf { it.isNotBlank() } ?: "native"
        val decodedText = normalize(stringAny("decoded_text", "decodedText"))
        val text = normalize(stringAny("text")) ?: decodedText
        val errorCodeField = normalize(stringAny("error", "errorCode"))
        val messageField = normalize(stringAny("message"))
        val lastErrorField = normalize(stringAny("last_error", "lastError"))
        val lastFailureField = normalize(stringAny("last_failure", "lastFailure"))
        val modelDetected = boolAny("model_detected", "modelDetected")
        val realTokenIds = boolAny("real_token_ids", "realTokenIds")
        val realInference = boolAny("real_inference", "realInference")
        val nativeGenerationAvailable = boolAny("native_generation_available", "nativeGenerationAvailable")
        val simulation = boolAny("simulation", "simulationActive")
        val jsonParseAttempted = boolAny("json_parse_attempted", "jsonParseAttempted")
        val jsonParseSuccess = boolAny("json_parse_success", "jsonParseSuccess")
        val promptTokenIdsSample = intListAny("prompt_token_ids_sample", "promptTokenIdsSample")
        val generatedTokenIdsSample = intListAny("generated_token_ids_sample", "generatedTokenIdsSample")
        val promptText = normalize(stringAny("prompt_text", "promptText"))
        val parsedIntent = normalize(stringAny("parsed_intent", "parsedIntent"))
        val parsedRiskLevel = normalize(stringAny("parsed_risk_level", "parsedRiskLevel"))
        val parsedActionType = normalize(stringAny("parsed_action_type", "parsedActionType"))
        val finishReason = normalize(stringAny("finish_reason", "finishReason"))
        val realForwardPass = boolAny("real_forward_pass", "realForwardPass")
        val nativeForwardPassCount = intAny("native_forward_pass_count", "nativeForwardPassCount", "total_decode_calls", "totalDecodeCalls")
        val logitsComputed = boolAny("logits_computed", "logitsComputed", "logits_available", "logitsAvailable")
        val logitsFinite = boolAny("logits_finite", "logitsFinite")
        val logitsPreview = normalize(stringAny("logits_preview", "logitsPreview"))
        val sampledFromModelLogits = boolAny("sampled_from_model_logits", "sampledFromModelLogits")
        val usableOutput = boolAny("usable_output", "usableOutput")
        val nativeEngineStatus = normalize(stringAny("native_engine_status", "nativeEngineStatus")) ?: "unknown"
        val usableBrainStatus = normalize(stringAny("usable_brain_status", "usableBrainStatus")) ?: "unknown"
        val chatTemplateApplied = boolAny("chat_template_applied", "chatTemplateApplied")
        val chatTemplateSource = normalize(stringAny("chat_template_source", "chatTemplateSource"))
        val proofStage = normalize(stringAny("proof_stage", "proofStage"))
        val proofStageReached = normalize(
            stringAny("proof_stage_reached", "proofStageReached", "stage_reached", "stageReached")
        )
        val stopReason = normalize(stringAny("stop_reason", "stopReason"))
        val repetitionDetected = boolAny("repetition_detected", "repetitionDetected")
        val nativeError = normalize(stringAny("native_error", "nativeError"))
        val confirmationRequired = boolAny("confirmationRequired", "confirmation_required")
        val loadMs = longAny("load_ms", "loadMs", "modelLoadMs", "model_load_ms")
        val modelLoadMs = longAny("modelLoadMs", "model_load_ms", "load_ms", "loadMs")
        val generationMs = longAny("generation_ms", "generationMs")
        val errorMessage = messageField
            ?: lastFailureField
            ?: errorCodeField
            ?: lastErrorField
            ?: if (success) null else "Native reported error"
        val parsedMessage = messageField
            ?: lastFailureField
            ?: errorCodeField
            ?: errorMessage
        val tokenIdsPreview = normalize(stringAny("token_ids_preview", "tokenIdsPreview"))
            ?: promptTokenIdsSample.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]")

        return NativeLlamaResult(
            text = text,
            success = success,
            errorMessage = errorMessage,
            latencyMillis = longAny("latencyMillis"),
            tokensGenerated = intAny("tokens_generated", "tokensGenerated"),
            promptTokens = intAny("prompt_tokens_count", "prompt_tokens", "promptTokens"),
            modelLoadMs = modelLoadMs,
            loadMs = loadMs,
            promptEvalMs = longAny("promptEvalMs", "prompt_eval_ms"),
            generationMs = generationMs,
            contextSize = intAny("contextSize", "context_size"),
            batchSize = intAny("batch_size", "batchSize"),
            threadsUsed = intAny("threadsUsed", "threads_used", "thread_count", "threadCount"),
            backendType = backend,
            backend = backend,
            modelArch = normalize(stringAny("modelArch", "model_arch")),
            vocabSize = intAny("vocab_size", "vocabSize"),
            tensorsLoaded = intAny("tensorsLoaded", "tensors_loaded"),
            modelDetected = modelDetected,
            realTokenIds = realTokenIds,
            realInference = realInference,
            nativeGenerationAvailable = nativeGenerationAvailable,
            metadataParsed = boolAny("metadataParsed"),
            tokenizerLoaded = boolAny("tokenizer_loaded", "tokenizerLoaded"),
            tensorsWeightLoaded = boolAny("tensorsWeightLoaded"),
            ggmlGraphCompute = boolAny("ggmlGraphCompute"),
            logitsGenerated = boolAny("logitsGenerated"),
            samplingActive = boolAny("samplingActive"),
            deterministicResponse = boolAny("deterministicResponse"),
            memoryEstimateBytes = longAny("memoryEstimateBytes", "memory_estimate_bytes"),
            tokensPerSecond = doubleAny("tokensPerSecond", "tokens_per_second"),
            nativeBridgeStable = boolAny("nativeBridgeStable"),
            jsonReturnBridge = boolAny("jsonReturnBridge"),
            crashFree = boolAny("crashFree"),
            tokenizerType = normalize(stringAny("tokenizer_type", "tokenizerType")),
            bosTokenId = intAny("bosTokenId"),
            eosTokenId = intAny("eosTokenId"),
            specialTokensCount = intAny("specialTokensCount"),
            tokenizationSuccess = boolAny("tokenization_ok", "tokenizationSuccess"),
            tokenizerLoadMs = longAny("tokenizerLoadMs", "tokenizer_load_ms"),
            ggmlGraphBuilt = boolAny("ggmlGraphBuilt"),
            graphNodesCount = intAny("graphNodesCount"),
            memoryMappedMb = intAny("memoryMappedMb", "memory_mapped_mb"),
            logitsFromModelWeights = boolAny("logitsFromModelWeights"),
            decodedTokens = boolAny("decodedTokens"),
            sampledTokenIdsCount = intAny("sampledTokenIdsCount", "sampled_token_ids_count"),
            simulation = simulation,
            decodedText = decodedText,
            errorCode = errorCodeField,
            message = parsedMessage,
            simulationActive = simulation,
            upstreamSamplerLinked = boolAny("upstreamSamplerLinked"),
            tokenIdsPreview = tokenIdsPreview,
            tokenizationOk = boolAny("tokenization_ok", "tokenizationSuccess"),
            lastError = errorCodeField ?: lastErrorField ?: errorMessage,
            lastFailure = parsedMessage ?: lastErrorField ?: errorCodeField ?: errorMessage,
            modelLoaded = boolAny("model_loaded", "modelLoaded"),
            modelReused = boolAny("model_reused", "modelReused"),
            modelLoadCount = intAny("model_load_count", "modelLoadCount"),
            generationCallCount = intAny("generation_call_count", "generationCallCount"),
            promptText = promptText,
            promptTokenIdsSample = promptTokenIdsSample,
            generatedTokenIdsSample = generatedTokenIdsSample,
            jsonParseAttempted = jsonParseAttempted,
            jsonParseSuccess = jsonParseSuccess,
            realForwardPass = realForwardPass,
            nativeForwardPassCount = nativeForwardPassCount,
            logitsComputed = logitsComputed,
            logitsFinite = logitsFinite,
            logitsPreview = logitsPreview,
            sampledFromModelLogits = sampledFromModelLogits,
            parsedIntent = parsedIntent,
            parsedRiskLevel = parsedRiskLevel,
            parsedActionType = parsedActionType,
            finishReason = finishReason
                ?: stopReason
                ?: if (jsonParseSuccess) "json_complete" else null,
            usableOutput = usableOutput,
            nativeEngineStatus = nativeEngineStatus,
            usableBrainStatus = usableBrainStatus,
            chatTemplateApplied = chatTemplateApplied,
            chatTemplateSource = chatTemplateSource,
            proofStage = proofStage,
            proofStageReached = proofStageReached,
            stopReason = stopReason,
            repetitionDetected = repetitionDetected,
            nativeError = nativeError,
            confirmationRequired = confirmationRequired
        )
    }

    override fun isLoaded(): Boolean {
        if (!modelLoaded) return false
        return if (libraryLoaded) {
            runCatching { nativeIsModelLoaded() }.getOrDefault(false)
        } else {
            true
        }
    }

    override fun unload() {
        if (modelLoaded && libraryLoaded) {
            try {
                nativeReleaseModel()
            } catch (e: Throwable) {
                logE(TAG, "Error unloading model", e)
            }
        }
        modelLoaded = false
        loadedModelPath = null
        modelReused = false
        lastParsedResult = null
    }

    override fun diagnostics(): String {
        val result = lastParsedResult
        return buildString {
            append("library_loaded=$libraryLoaded, ")
            append("model_loaded=$modelLoaded, ")
            append("native_model_loaded=${if (libraryLoaded) runCatching { nativeIsModelLoaded() }.getOrDefault(false) else modelLoaded}, ")
            append("loaded_model_path=${loadedModelPath ?: "none"}, ")
            append("model_load_count=$modelLoadCount, ")
            append("generation_call_count=$generationCallCount, ")
            append("model_reused=$modelReused, ")
            append("load_ms=$lastLoadMs, ")
            append("last_error=${lastError ?: "none"}, ")
            append("last_failure=${lastFailure ?: lastError ?: "none"}")
            if (result != null) {
                append(", backend=${result.backend}, ")
                append("model_detected=${result.modelDetected}, ")
                append("tokenizer_loaded=${result.tokenizerLoaded}, ")
                append("vocab_size=${result.vocabSize}, ")
                append("batch_size=${result.batchSize}, ")
                append("tokenization_ok=${result.tokenizationOk}, ")
                append("prompt_tokens=${result.promptTokens}, ")
                append("real_token_ids=${result.realTokenIds}, ")
                append("native_generation_available=${result.nativeGenerationAvailable}, ")
                append("simulation=${result.simulation}, ")
                append("decoded_text=${result.decodedText ?: "none"}, ")
                append("error=${result.errorCode ?: result.lastError ?: "none"}, ")
                append("message=${result.message ?: result.lastFailure ?: "none"}, ")
                append("token_ids_preview=${result.tokenIdsPreview ?: "none"}, ")
                append("prompt_token_ids_sample=${if (result.promptTokenIdsSample.isNotEmpty()) result.promptTokenIdsSample.joinToString(prefix = "[", postfix = "]") else "[]"}, ")
                append("generated_token_ids_sample=${if (result.generatedTokenIdsSample.isNotEmpty()) result.generatedTokenIdsSample.joinToString(prefix = "[", postfix = "]") else "[]"}, ")
                append("json_parse_attempted=${result.jsonParseAttempted}, ")
                append("json_parse_success=${result.jsonParseSuccess}, ")
                append("real_inference=${result.realInference}, ")
                append("tokens_generated=${result.tokensGenerated}, ")
                append("logits_finite=${result.logitsFinite}, ")
                append("logits_preview=${result.logitsPreview ?: "none"}, ")
                append("load_ms=${result.loadMs}, ")
                append("generation_ms=${result.generationMs}, ")
                append("model_loaded=${result.modelLoaded}, ")
                append("model_reused=${result.modelReused}, ")
                append("model_load_count=${result.modelLoadCount}, ")
                append("generation_call_count=${result.generationCallCount}, ")
                append("proof_stage=${result.proofStage ?: "none"}, ")
                append("proof_stage_reached=${result.proofStageReached ?: "none"}, ")
                append("finish_reason=${result.finishReason ?: "none"}, ")
                append("parsed_intent=${result.parsedIntent ?: "none"}, ")
                append("parsed_risk_level=${result.parsedRiskLevel ?: "none"}")
            }
        }
    }

    private fun logW(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] W: $msg")
        }
    }

    private fun logE(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] E: $msg ${throwable?.message ?: ""}")
        }
    }

    companion object {
        private const val TAG = "LlamaCppJni"
        private const val DEFAULT_MAX_TOKENS = 6
        private const val DEFAULT_TEMPERATURE = 0.0f
        private const val DEFAULT_TOP_K = 1
        private const val DEFAULT_TOP_P = 1.0f
        private var libraryLoaded = false

        init {
            try {
                System.loadLibrary("llama-jni")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // Safe to ignore in unit tests or when lib is missing
            } catch (e: Throwable) {
                // Safe to ignore in unit tests
            }
        }

        @JvmStatic
        private external fun nativeLoadModel(modelPath: String): Boolean

        @JvmStatic
        private external fun nativeGenerate(
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            topK: Int,
            topP: Float,
            timeoutMs: Int
        ): String?

        @JvmStatic
        private external fun nativeRunProofStage(
            stage: String,
            modelPath: String,
            prompt: String,
            maxTokens: Int,
            timeoutMs: Int
        ): String?

        @JvmStatic
        private external fun nativeReleaseModel()

        @JvmStatic
        private external fun nativeUnloadModel()

        @JvmStatic
        private external fun nativeIsModelLoaded(): Boolean
    }
}
