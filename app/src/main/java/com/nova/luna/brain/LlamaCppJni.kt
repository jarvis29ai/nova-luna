package com.nova.luna.brain

import java.io.File
import android.util.Log

/**
 * JNI bridge for llama.cpp.
 */
class LlamaCppJni : NativeLlamaRuntime {

    private var modelLoaded = false
    private var lastError: String? = null
    private var lastFailure: String? = null
    private var lastParsedResult: NativeLlamaResult? = null

    override fun loadModel(modelFile: File): Boolean {
        if (!libraryLoaded) {
            lastError = "Native library 'llama-jni' not loaded."
            lastFailure = lastError
            lastParsedResult = null
            return false
        }

        if (!modelFile.exists()) {
            lastError = "Model file not found: ${modelFile.path}"
            lastFailure = lastError
            lastParsedResult = null
            return false
        }

        return try {
            val result = nativeLoadModel(modelFile.absolutePath)
            modelLoaded = result
            lastParsedResult = null
            if (result) {
                lastError = null
                lastFailure = null
            } else {
                lastError = "Native model load failed."
                lastFailure = lastError
            }
            result
        } catch (e: Throwable) {
            lastError = "Native error: ${e.message}"
            lastFailure = lastError
            lastParsedResult = null
            logE(TAG, "Error loading model", e)
            false
        }
    }

    override fun generate(prompt: String, timeoutMs: Long): NativeLlamaResult {
        if (!modelLoaded) {
            val result = NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Model not loaded.",
                backendType = "failed_native",
                backend = "failed_native",
                lastError = "Model not loaded.",
                lastFailure = "Model not loaded."
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            return result
        }

        val rawJson = try {
            nativeGenerate(prompt, timeoutMs.toInt())
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
                lastError = "Native JNI call returned null or aborted.",
                lastFailure = "Native JNI call returned null or aborted."
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
                lastError = "Malformed native JSON: ${e.message}",
                lastFailure = "Malformed native JSON: ${e.message}"
            )
            lastParsedResult = result
            lastError = result.lastError
            lastFailure = result.lastFailure
            result
        }
    }

    internal fun parseNativeJson(json: String): NativeLlamaResult {
        fun normalize(value: String?): String? {
            val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return if (trimmed.equals("none", ignoreCase = true)) null else trimmed
        }

        fun decodeJsonString(value: String): String {
            val builder = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char != '\\') {
                    builder.append(char)
                    index += 1
                    continue
                }

                if (index + 1 >= value.length) {
                    builder.append('\\')
                    break
                }

                when (val next = value[index + 1]) {
                    '\\' -> builder.append('\\')
                    '"' -> builder.append('"')
                    '/' -> builder.append('/')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000C')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> {
                        if (index + 5 >= value.length) {
                            builder.append("\\u")
                            index += 2
                            continue
                        }
                        val hex = value.substring(index + 2, index + 6)
                        if (hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            builder.append(hex.toInt(16).toChar())
                            index += 6
                            continue
                        }
                        builder.append("\\u")
                    }

                    else -> builder.append(next)
                }
                index += 2
            }
            return builder.toString()
        }

        fun getStringAny(vararg keys: String): String? {
            for (key in keys) {
                val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                val match = pattern.find(json) ?: continue
                return decodeJsonString(match.groupValues[1])
            }
            return null
        }

        fun getBoolAny(vararg keys: String): Boolean {
            for (key in keys) {
                val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
                val match = pattern.find(json) ?: continue
                return match.groupValues[1].equals("true", ignoreCase = true)
            }
            return false
        }

        fun getIntAny(vararg keys: String): Int {
            for (key in keys) {
                val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
                val match = pattern.find(json) ?: continue
                return match.groupValues[1].toIntOrNull() ?: 0
            }
            return 0
        }

        fun getLongAny(vararg keys: String): Long {
            for (key in keys) {
                val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
                val match = pattern.find(json) ?: continue
                return match.groupValues[1].toLongOrNull() ?: 0L
            }
            return 0L
        }

        fun getDoubleAny(vararg keys: String): Double {
            for (key in keys) {
                val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
                val match = pattern.find(json) ?: continue
                return match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
            return 0.0
        }

        val success = getBoolAny("success", "ok")
        val backend = getStringAny("backend", "backendType") ?: "native"
        val decodedText = getStringAny("decoded_text", "decodedText")
        val text = getStringAny("text") ?: decodedText
        val errorCodeField = normalize(getStringAny("error", "errorCode"))
        val messageField = normalize(getStringAny("message"))
        val lastErrorField = normalize(getStringAny("last_error", "lastError"))
        val lastFailureField = normalize(getStringAny("last_failure", "lastFailure"))
        val modelDetected = getBoolAny("model_detected", "modelDetected")
        val realTokenIds = getBoolAny("real_token_ids", "realTokenIds")
        val nativeGenerationAvailable = getBoolAny("native_generation_available", "nativeGenerationAvailable")
        val simulation = getBoolAny("simulation", "simulationActive")
        val errorMessage = messageField
            ?: lastFailureField
            ?: errorCodeField
            ?: lastErrorField
            ?: if (success) null else "Native reported error"
        val parsedMessage = messageField
            ?: lastFailureField
            ?: errorCodeField
            ?: errorMessage

        return NativeLlamaResult(
            text = text,
            success = success,
            errorMessage = errorMessage,
            latencyMillis = getLongAny("latencyMillis"),
            tokensGenerated = getIntAny("tokensGenerated"),
            promptTokens = getIntAny("prompt_tokens", "promptTokens"),
            modelLoadMs = getLongAny("modelLoadMs"),
            promptEvalMs = getLongAny("promptEvalMs"),
            contextSize = getIntAny("contextSize"),
            threadsUsed = getIntAny("threadsUsed"),
            backendType = backend,
            backend = backend,
            modelArch = getStringAny("modelArch"),
            vocabSize = getIntAny("vocab_size", "vocabSize"),
            tensorsLoaded = getIntAny("tensorsLoaded"),
            modelDetected = modelDetected,
            realTokenIds = realTokenIds,
            realInference = getBoolAny("real_inference", "realInference"),
            nativeGenerationAvailable = nativeGenerationAvailable,
            metadataParsed = getBoolAny("metadataParsed"),
            tokenizerLoaded = getBoolAny("tokenizer_loaded", "tokenizerLoaded"),
            tensorsWeightLoaded = getBoolAny("tensorsWeightLoaded"),
            ggmlGraphCompute = getBoolAny("ggmlGraphCompute"),
            logitsGenerated = getBoolAny("logitsGenerated"),
            samplingActive = getBoolAny("samplingActive"),
            deterministicResponse = getBoolAny("deterministicResponse"),
            memoryEstimateBytes = getLongAny("memoryEstimateBytes"),
            tokensPerSecond = getDoubleAny("tokensPerSecond"),
            nativeBridgeStable = getBoolAny("nativeBridgeStable"),
            jsonReturnBridge = getBoolAny("jsonReturnBridge"),
            crashFree = getBoolAny("crashFree"),
            tokenizerType = getStringAny("tokenizer_type", "tokenizerType"),
            bosTokenId = getIntAny("bosTokenId"),
            eosTokenId = getIntAny("eosTokenId"),
            specialTokensCount = getIntAny("specialTokensCount"),
            tokenizationSuccess = getBoolAny("tokenization_ok", "tokenizationSuccess"),
            tokenizerLoadMs = getLongAny("tokenizerLoadMs"),
            ggmlGraphBuilt = getBoolAny("ggmlGraphBuilt"),
            graphNodesCount = getIntAny("graphNodesCount"),
            memoryMappedMb = getIntAny("memoryMappedMb"),
            logitsFromModelWeights = getBoolAny("logitsFromModelWeights"),
            decodedTokens = getBoolAny("decodedTokens"),
            sampledTokenIdsCount = getIntAny("sampledTokenIdsCount"),
            simulation = simulation,
            decodedText = decodedText,
            errorCode = errorCodeField,
            message = parsedMessage,
            simulationActive = simulation,
            upstreamSamplerLinked = getBoolAny("upstreamSamplerLinked"),
            tokenIdsPreview = normalize(getStringAny("token_ids_preview", "tokenIdsPreview")),
            tokenizationOk = getBoolAny("tokenization_ok", "tokenizationSuccess"),
            lastError = errorCodeField ?: lastErrorField ?: errorMessage,
            lastFailure = parsedMessage ?: lastErrorField ?: errorCodeField ?: errorMessage
        )
    }

    override fun isLoaded(): Boolean = modelLoaded

    override fun unload() {
        if (modelLoaded && libraryLoaded) {
            try {
                nativeUnloadModel()
            } catch (e: Throwable) {
                logE(TAG, "Error unloading model", e)
            }
        }
        modelLoaded = false
        lastParsedResult = null
    }

    override fun diagnostics(): String {
        val result = lastParsedResult
        return buildString {
            append("library_loaded=$libraryLoaded, ")
            append("model_loaded=$modelLoaded, ")
            append("last_error=${lastError ?: "none"}, ")
            append("last_failure=${lastFailure ?: lastError ?: "none"}")
            if (result != null) {
                append(", backend=${result.backend}, ")
                append("model_detected=${result.modelDetected}, ")
                append("tokenizer_loaded=${result.tokenizerLoaded}, ")
                append("vocab_size=${result.vocabSize}, ")
                append("tokenization_ok=${result.tokenizationOk}, ")
                append("prompt_tokens=${result.promptTokens}, ")
                append("real_token_ids=${result.realTokenIds}, ")
                append("native_generation_available=${result.nativeGenerationAvailable}, ")
                append("simulation=${result.simulation}, ")
                append("decoded_text=${result.decodedText ?: "none"}, ")
                append("error=${result.errorCode ?: result.lastError ?: "none"}, ")
                append("message=${result.message ?: result.lastFailure ?: "none"}, ")
                append("token_ids_preview=${result.tokenIdsPreview ?: "none"}, ")
                append("real_inference=${result.realInference}")
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
        private external fun nativeGenerate(prompt: String, timeoutMs: Int): String?

        @JvmStatic
        private external fun nativeUnloadModel()
    }
}
