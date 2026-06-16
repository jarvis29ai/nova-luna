package com.nova.luna.brain

import java.io.File

/**
 * Interface for native llama.cpp GGUF inference.
 */
interface NativeLlamaRuntime {
    fun loadModel(modelFile: File): Boolean
    fun generate(prompt: String, timeoutMs: Long): NativeLlamaResult
    fun isLoaded(): Boolean
    fun unload()
    fun diagnostics(): String
}

data class NativeLlamaResult(
    val text: String?,
    val success: Boolean,
    val errorMessage: String? = null,
    val latencyMillis: Long = 0,
    val tokensGenerated: Int = 0,
    val promptTokens: Int = 0,
    val modelLoadMs: Long = 0,
    val loadMs: Long = 0,
    val promptEvalMs: Long = 0,
    val generationMs: Long = 0,
    val contextSize: Int = 0,
    val batchSize: Int = 0,
    val threadsUsed: Int = 0,
    val backendType: String = "native",
    val backend: String = backendType,
    val modelArch: String? = null,
    val vocabSize: Int = 0,
    val tensorsLoaded: Int = 0,
    val modelDetected: Boolean = false,
    val realTokenIds: Boolean = false,
    val realInference: Boolean = false,
    val nativeGenerationAvailable: Boolean = false,
    val metadataParsed: Boolean = false,
    val tokenizerLoaded: Boolean = false,
    val tensorsWeightLoaded: Boolean = false,
    val ggmlGraphCompute: Boolean = false,
    val logitsGenerated: Boolean = false,
    val samplingActive: Boolean = false,
    val deterministicResponse: Boolean = true,
    val memoryEstimateBytes: Long = 0,
    val tokensPerSecond: Double = 0.0,
    val nativeBridgeStable: Boolean = true,
    val jsonReturnBridge: Boolean = true,
    val crashFree: Boolean = true,
    val tokenizerType: String? = null,
    val bosTokenId: Int = -1,
    val eosTokenId: Int = -1,
    val specialTokensCount: Int = 0,
    val tokenizationSuccess: Boolean = false,
    val tokenizerLoadMs: Long = 0,
    val ggmlGraphBuilt: Boolean = false,
    val graphNodesCount: Int = 0,
    val memoryMappedMb: Int = 0,
    val logitsFromModelWeights: Boolean = false,
    val decodedTokens: Boolean = false,
    val sampledTokenIdsCount: Int = 0,
    val simulation: Boolean = false,
    val decodedText: String? = null,
    val errorCode: String? = null,
    val message: String? = errorMessage,
    val simulationActive: Boolean = simulation,
    val upstreamSamplerLinked: Boolean = false,
    val tokenIdsPreview: String? = null,
    val tokenizationOk: Boolean = tokenizationSuccess,
    val lastError: String? = errorCode ?: errorMessage,
    val lastFailure: String? = message ?: errorCode ?: errorMessage,
    val modelLoaded: Boolean = false,
    val modelReused: Boolean = false,
    val modelLoadCount: Int = 0,
    val generationCallCount: Int = 0,
    val promptText: String? = null,
    val promptTokenIdsSample: List<Int> = emptyList(),
    val generatedTokenIdsSample: List<Int> = emptyList(),
    val jsonParseAttempted: Boolean = false,
    val jsonParseSuccess: Boolean = false,
    val realForwardPass: Boolean = false,
    val nativeForwardPassCount: Int = 0,
    val logitsComputed: Boolean = false,
    val logitsFinite: Boolean = false,
    val logitsPreview: String? = null,
    val sampledFromModelLogits: Boolean = false,
    val usableOutput: Boolean = false,
    val nativeEngineStatus: String = "unknown",
    val usableBrainStatus: String = "unknown",
    val chatTemplateApplied: Boolean = false,
    val chatTemplateSource: String? = null,
    val proofStage: String? = null,
    val proofStageReached: String? = null,
    val stopReason: String? = null,
    val repetitionDetected: Boolean = false,
    val nativeError: String? = null,
    val parsedIntent: String? = null,
    val parsedRiskLevel: String? = null,
    val parsedActionType: String? = null,
    val confirmationRequired: Boolean = false,
    val finishReason: String? = null
) {
    val decodedTextLength: Int
        get() = decodedText?.length ?: 0

    val hasUsableGeneratedText: Boolean
        get() = backend.equals("native", ignoreCase = true) &&
            modelDetected &&
            tokenizerLoaded &&
            realTokenIds &&
            realInference &&
            realForwardPass &&
            nativeForwardPassCount > 0 &&
            logitsComputed &&
            sampledFromModelLogits &&
            !simulation &&
            nativeGenerationAvailable &&
            tokensGenerated > 0 &&
            !(decodedText ?: text).isNullOrBlank()

    val hasUsableBrainOutput: Boolean
        get() = hasUsableGeneratedText &&
            jsonParseSuccess &&
            usableOutput &&
            !parsedIntent.isNullOrBlank() &&
            !parsedActionType.isNullOrBlank() &&
            !parsedRiskLevel.isNullOrBlank() &&
            !repetitionDetected &&
            nativeError.isNullOrBlank()
}
