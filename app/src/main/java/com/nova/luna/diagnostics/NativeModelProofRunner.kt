package com.nova.luna.diagnostics

import com.nova.luna.brain.LlamaCppJni
import com.nova.luna.brain.NativeLlamaResult
import com.nova.luna.brain.NativeLlamaRuntime
import com.nova.luna.modelinstall.ModelInstallReason
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallSpecRegistry
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.PrivateAppModelStorage
import java.io.File

class NativeModelProofRunner(
    private val storage: PrivateAppModelStorage,
    private val modelInstallService: ModelInstallService,
    private val runtimeFactory: () -> NativeLlamaRuntime = { LlamaCppJni() },
    private val specRegistry: ModelInstallSpecRegistry = ModelInstallSpecRegistry()
) {
    fun runTokenizerProof(
        modelId: String = ModelPackId.LITE.wireValue,
        sampleText: String = DEFAULT_TOKENIZER_SAMPLE,
        debugOverridePath: String? = null
    ): NativeTokenizerProof {
        val state = modelInstallService.getInstallState(modelId, debugOverridePath)
        val expectedPath = expectedModelPath(state.modelId, state.expectedFileName)
        val modelRole = state.role
        val modelSha256 = state.sha256Actual ?: state.sha256Expected

        if (!state.exists) {
            return NativeTokenizerProof(
                status = NativeProofStatus.MODEL_MISSING,
                modelFound = false,
                modelPath = expectedPath.absolutePath,
                modelRole = modelRole,
                modelSha256 = modelSha256,
                tokenizerLoaded = false,
                vocabSize = 0,
                sampleText = sampleText,
                sampleTokenIds = emptyList(),
                tokenizerError = null,
                instructionsForUser = missingModelInstructions(expectedPath, state.reason),
                proofSource = "NONE",
                simulation = false
            )
        }

        if (!state.ready) {
            return NativeTokenizerProof(
                status = NativeProofStatus.PARTIAL,
                modelFound = true,
                modelPath = state.resolvedPath ?: expectedPath.absolutePath,
                modelRole = modelRole,
                modelSha256 = modelSha256,
                tokenizerLoaded = false,
                vocabSize = 0,
                sampleText = sampleText,
                sampleTokenIds = emptyList(),
                tokenizerError = state.reason,
                instructionsForUser = buildString {
                    append("Model is present but not ready: ")
                    append(state.reason)
                    append(". Verify the GGUF file at ")
                    append(expectedPath.absolutePath)
                    append(".")
                },
                proofSource = "NONE",
                simulation = false
            )
        }

        val runtime = runtimeFactory()
        val modelFile = File(state.resolvedPath ?: expectedPath.absolutePath)

        return try {
            if (!runtime.loadModel(modelFile)) {
                val error = runtime.diagnostics().ifBlank { ModelInstallReason.MODEL_FILE_MISSING }
                NativeTokenizerProof(
                    status = NativeProofStatus.ERROR,
                    modelFound = true,
                    modelPath = modelFile.absolutePath,
                    modelRole = modelRole,
                    modelSha256 = modelSha256,
                    tokenizerLoaded = false,
                    vocabSize = 0,
                    sampleText = sampleText,
                    sampleTokenIds = emptyList(),
                    tokenizerError = error,
                    instructionsForUser = buildTokenizerFailureInstructions(modelFile, error),
                    proofSource = "NONE",
                    simulation = false
                )
            } else {
                val nativeResult = runtime.generate(sampleText, DEFAULT_TIMEOUT_MS)
                val tokenizerLoaded = nativeResult.tokenizerLoaded && nativeResult.vocabSize > 0
                val sampleTokenIds = nativeResult.promptTokenIdsSample
                val status = when {
                    nativeResult.success && tokenizerLoaded && sampleTokenIds.isNotEmpty() -> NativeProofStatus.READY
                    nativeResult.success -> NativeProofStatus.PARTIAL
                    else -> NativeProofStatus.ERROR
                }
                NativeTokenizerProof(
                    status = status,
                    modelFound = true,
                    modelPath = modelFile.absolutePath,
                    modelRole = modelRole,
                    modelSha256 = modelSha256,
                    tokenizerLoaded = tokenizerLoaded,
                    vocabSize = nativeResult.vocabSize,
                    sampleText = sampleText,
                    sampleTokenIds = sampleTokenIds,
                    tokenizerError = nativeResult.lastError ?: nativeResult.errorCode,
                    instructionsForUser = if (status == NativeProofStatus.READY) null else buildTokenizerFailureInstructions(modelFile, nativeResult.lastError ?: nativeResult.errorCode ?: nativeResult.message ?: "UNKNOWN_ERROR"),
                    proofSource = if (status == NativeProofStatus.READY) "REAL_DEVICE_GGUF" else "NONE",
                    simulation = false
                )
            }
        } catch (throwable: Throwable) {
            NativeTokenizerProof(
                status = NativeProofStatus.ERROR,
                modelFound = true,
                modelPath = modelFile.absolutePath,
                modelRole = modelRole,
                modelSha256 = modelSha256,
                tokenizerLoaded = false,
                vocabSize = 0,
                sampleText = sampleText,
                sampleTokenIds = emptyList(),
                tokenizerError = throwable.message ?: throwable::class.java.simpleName,
                instructionsForUser = buildTokenizerFailureInstructions(modelFile, throwable.message ?: throwable::class.java.simpleName),
                proofSource = "NONE",
                simulation = false
            )
        } finally {
            runCatching { runtime.unload() }
        }
    }

    fun runInferenceProof(
        modelId: String = ModelPackId.LITE.wireValue,
        promptText: String = DEFAULT_INFERENCE_PROMPT,
        debugOverridePath: String? = null
    ): NativeInferenceProof {
        val state = modelInstallService.getInstallState(modelId, debugOverridePath)
        val expectedPath = expectedModelPath(state.modelId, state.expectedFileName)
        val modelRole = state.role
        val modelSha256 = state.sha256Actual ?: state.sha256Expected

        if (!state.exists) {
            return NativeInferenceProof(
                status = NativeProofStatus.MODEL_MISSING,
                modelFound = false,
                modelPath = expectedPath.absolutePath,
                modelRole = modelRole,
                modelSha256 = modelSha256,
                modelLoaded = false,
                realInference = false,
                generatedTokenCount = 0,
                decodedText = null,
                outputSource = "NONE",
                simulation = false,
                inferenceError = null,
                instructionsForUser = missingModelInstructions(expectedPath, state.reason),
                promptText = promptText,
                proofSource = "NONE"
            )
        }

        if (!state.ready) {
            return NativeInferenceProof(
                status = NativeProofStatus.PARTIAL,
                modelFound = true,
                modelPath = state.resolvedPath ?: expectedPath.absolutePath,
                modelRole = modelRole,
                modelSha256 = modelSha256,
                modelLoaded = false,
                realInference = false,
                generatedTokenCount = 0,
                decodedText = null,
                outputSource = "NATIVE_GGUF",
                simulation = false,
                inferenceError = state.reason,
                instructionsForUser = buildString {
                    append("Model is present but not ready: ")
                    append(state.reason)
                    append(". Verify the GGUF file at ")
                    append(expectedPath.absolutePath)
                    append(".")
                },
                promptText = promptText,
                proofSource = "NONE"
            )
        }

        val runtime = runtimeFactory()
        val modelFile = File(state.resolvedPath ?: expectedPath.absolutePath)

        return try {
            if (!runtime.loadModel(modelFile)) {
                val error = runtime.diagnostics().ifBlank { ModelInstallReason.MODEL_FILE_MISSING }
                NativeInferenceProof(
                    status = NativeProofStatus.ERROR,
                    modelFound = true,
                    modelPath = modelFile.absolutePath,
                    modelRole = modelRole,
                    modelSha256 = modelSha256,
                    modelLoaded = false,
                    realInference = false,
                    generatedTokenCount = 0,
                    decodedText = null,
                    outputSource = "NATIVE_GGUF",
                    simulation = false,
                    inferenceError = error,
                    instructionsForUser = buildInferenceFailureInstructions(modelFile, error),
                    promptText = promptText,
                    proofSource = "NONE"
                )
            } else {
                val nativeResult = runtime.generate(promptText, DEFAULT_TIMEOUT_MS)
                val decodedText = nativeResult.decodedText?.trim()?.takeIf { it.isNotBlank() }
                    ?: nativeResult.text?.trim()?.takeIf { it.isNotBlank() }
                val modelLoaded = nativeResult.modelLoaded || runtime.isLoaded()
                val realInference = modelLoaded &&
                    nativeResult.nativeGenerationAvailable &&
                    nativeResult.tokensGenerated > 0 &&
                    !decodedText.isNullOrBlank() &&
                    !nativeResult.simulation
                val generatedTokenIds = nativeResult.generatedTokenIdsSample
                val status = when {
                    realInference -> NativeProofStatus.READY
                    nativeResult.tokensGenerated > 0 || !decodedText.isNullOrBlank() -> NativeProofStatus.PARTIAL
                    else -> NativeProofStatus.ERROR
                }
                NativeInferenceProof(
                    status = status,
                    modelFound = true,
                    modelPath = modelFile.absolutePath,
                    modelRole = modelRole,
                    modelSha256 = modelSha256,
                    modelLoaded = modelLoaded,
                    realInference = realInference,
                    generatedTokenCount = nativeResult.tokensGenerated,
                    decodedText = decodedText,
                    outputSource = if (realInference) "NATIVE_GGUF" else "NATIVE_GGUF",
                    simulation = nativeResult.simulation,
                    inferenceError = if (realInference) null else nativeResult.lastError ?: nativeResult.errorCode,
                    instructionsForUser = if (status == NativeProofStatus.READY) null else buildInferenceFailureInstructions(modelFile, nativeResult.lastError ?: nativeResult.errorCode ?: nativeResult.message ?: "UNKNOWN_ERROR"),
                    promptText = nativeResult.promptText ?: promptText,
                    promptTokenIds = nativeResult.promptTokenIdsSample,
                    generatedTokenIds = generatedTokenIds,
                    proofSource = if (realInference) "REAL_DEVICE_GGUF" else "NONE"
                )
            }
        } catch (throwable: Throwable) {
            NativeInferenceProof(
                status = NativeProofStatus.ERROR,
                modelFound = true,
                modelPath = modelFile.absolutePath,
                modelRole = modelRole,
                modelSha256 = modelSha256,
                modelLoaded = false,
                realInference = false,
                generatedTokenCount = 0,
                decodedText = null,
                outputSource = "NATIVE_GGUF",
                simulation = false,
                inferenceError = throwable.message ?: throwable::class.java.simpleName,
                instructionsForUser = buildInferenceFailureInstructions(modelFile, throwable.message ?: throwable::class.java.simpleName),
                promptText = promptText,
                proofSource = "NONE"
            )
        } finally {
            runCatching { runtime.unload() }
        }
    }

    private fun expectedModelPath(modelId: String, expectedFileName: String): File {
        val packId = ModelPackId.fromWireValue(modelId) ?: ModelPackId.CORE
        val fileName = expectedFileName.ifBlank {
            specRegistry.getSpec(modelId)?.expectedFileName?.takeIf { it.isNotBlank() } ?: modelId
        }
        return File(storage.modelsDir(packId), fileName)
    }

    private fun missingModelInstructions(expectedPath: File, reason: String): String {
        return buildString {
            append("No installed GGUF model was found. ")
            append(reason)
            append(" Place the model at ")
            append(expectedPath.absolutePath)
            append(" or import it through the debug model import receiver.")
        }
    }

    private fun buildTokenizerFailureInstructions(modelFile: File, reason: String): String {
        return buildString {
            append("Tokenizer proof did not complete. ")
            append(reason)
            append(" Check the GGUF at ")
            append(modelFile.absolutePath)
            append(" and rerun the proof broadcast.")
        }
    }

    private fun buildInferenceFailureInstructions(modelFile: File, reason: String): String {
        return buildString {
            append("Inference proof did not complete. ")
            append(reason)
            append(" Check the GGUF at ")
            append(modelFile.absolutePath)
            append(" and rerun the proof broadcast.")
        }
    }

    companion object {
        private const val DEFAULT_TOKENIZER_SAMPLE = "open camera"
        private const val DEFAULT_INFERENCE_PROMPT = """Return JSON only: {"actionType":"OPEN_CAMERA","riskLevel":"LOW"}"""
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
