package com.nova.luna.diagnostics

enum class NativeProofStatus {
    READY,
    MODEL_MISSING,
    PARTIAL,
    ERROR
}

data class NativeTokenizerProof(
    val status: NativeProofStatus,
    val modelFound: Boolean,
    val modelPath: String?,
    val tokenizerLoaded: Boolean,
    val vocabSize: Int,
    val sampleText: String,
    val sampleTokenIds: List<Int>,
    val tokenizerError: String? = null,
    val instructionsForUser: String? = null
) {
    fun asMap(): Map<String, Any?> {
        return linkedMapOf(
            "status" to status.name,
            "model_found" to modelFound,
            "model_path" to modelPath,
            "tokenizer_loaded" to tokenizerLoaded,
            "vocab_size" to vocabSize,
            "sample_text" to sampleText,
            "sample_token_ids" to sampleTokenIds,
            "tokenizer_error" to tokenizerError,
            "instructions_for_user" to instructionsForUser
        )
    }
}

data class NativeInferenceProof(
    val status: NativeProofStatus,
    val modelFound: Boolean,
    val modelPath: String?,
    val realInference: Boolean,
    val generatedTokenCount: Int,
    val decodedText: String?,
    val outputSource: String,
    val simulation: Boolean,
    val inferenceError: String? = null,
    val instructionsForUser: String? = null,
    val promptText: String? = null,
    val promptTokenIds: List<Int> = emptyList(),
    val generatedTokenIds: List<Int> = emptyList()
) {
    fun asMap(): Map<String, Any?> {
        return linkedMapOf(
            "status" to status.name,
            "model_found" to modelFound,
            "model_path" to modelPath,
            "real_inference" to realInference,
            "generated_token_count" to generatedTokenCount,
            "decoded_text" to decodedText,
            "output_source" to outputSource,
            "simulation" to simulation,
            "inference_error" to inferenceError,
            "instructions_for_user" to instructionsForUser,
            "prompt_text" to promptText,
            "prompt_token_ids" to promptTokenIds,
            "generated_token_ids" to generatedTokenIds
        )
    }
}
