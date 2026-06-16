package com.nova.luna.brain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import com.nova.luna.diagnostics.NativeModelProofRunner
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallVerifier
import com.nova.luna.modelinstall.ModelPathResolver
import com.nova.luna.modelinstall.ModelRuntimeStateStore
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.safety.SafetyGate

/**
 * Debug-only receiver for ADB-triggered model proof diagnostics.
 *
 * Examples:
 * adb shell am broadcast -p com.nova.luna -a com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME --es command "open camera" --es mode tokenizer
 * adb shell am broadcast -p com.nova.luna -a com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME --es command "open camera" --es mode inference
 */
class DiagnosticBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DIAGNOSE_RUNTIME) return

        val requestedModelId = resolveModelId(
            intent.getStringExtra(EXTRA_MODEL_ID)
                ?: intent.getStringExtra(EXTRA_PACK_ID)
                ?: intent.getStringExtra(EXTRA_MODEL)
                ?: intent.getStringExtra(EXTRA_ROLE)
        )
        val debugOverridePath = intent.getStringExtra(EXTRA_MODEL_PATH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val requestText = DiagnosticRequestResolver.resolve(
            command = intent.getStringExtra("command"),
            request = intent.getStringExtra("request"),
            fallback = DEFAULT_TOKENIZER_SAMPLE
        )
        val mode = intent.getStringExtra("mode") ?: "all"

        Log.i(
            TAG,
            "Received diagnostic request: $requestText, mode: $mode, modelId=${requestedModelId.wireValue}, modelPath=${debugOverridePath ?: "<default>"}"
        )
        appendTranscript(
            context = context,
            lines = listOf(
                "received requestText=$requestText",
                "mode=$mode",
                "modelId=${requestedModelId.wireValue}",
                "modelPath=${debugOverridePath ?: "<default>"}"
            )
        )

        val pendingResult = goAsync()

        Thread {
            try {
                performPhase18And19Diagnostics(
                    context = context,
                    requestText = requestText,
                    mode = mode,
                    intent = intent,
                    modelId = requestedModelId,
                    debugOverridePath = debugOverridePath
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Diagnostics failed", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun performPhase18And19Diagnostics(
        context: Context,
        requestText: String,
        mode: String,
        intent: Intent,
        modelId: com.nova.luna.modelinstall.ModelPackId,
        debugOverridePath: String?
    ) {
        val storage = PrivateAppModelStorage.from(context)
        val runtimeStateStore = ModelRuntimeStateStore(storage)
        val modelInstallService = ModelInstallService(
            pathResolver = ModelPathResolver(context, storage, runtimeStateStore),
            verifier = ModelInstallVerifier(),
            runtimeStateStore = runtimeStateStore,
            storage = storage
        )
        val proofRunner = NativeModelProofRunner(storage, modelInstallService)
        val installState = modelInstallService.getInstallState(modelId.wireValue, debugOverridePath)
        val normalizedMode = mode.trim().lowercase()
        val runTokenizer = normalizedMode in setOf("all", "tokenizer", "phase18")
        val runInference = normalizedMode in setOf("all", "inference", "phase19")
        val inferencePrompt = intent.getStringExtra("inference_prompt")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_INFERENCE_PROMPT

        val tokenizerProof = if (runTokenizer) {
            proofRunner.runTokenizerProof(
                modelId = modelId.wireValue,
                sampleText = requestText,
                debugOverridePath = debugOverridePath
            )
        } else {
            null
        }

        val inferenceProof = if (runInference) {
            proofRunner.runInferenceProof(
                modelId = modelId.wireValue,
                promptText = inferencePrompt,
                debugOverridePath = debugOverridePath
            )
        } else {
            null
        }

        Log.i(TAG, "--- NOVA/LUNA PHASE 18/19 MODEL PROOF ---")
        Log.i(TAG, "Input: $requestText")
        Log.i(TAG, "Mode: $mode")
        Log.i(TAG, "Requested Model: ${modelId.wireValue} (${modelId.displayName})")
        Log.i(TAG, "Debug Override Path: ${debugOverridePath ?: "<default>"}")
        Log.i(TAG, "MODEL_INSTALL_STATE | ${formatMap(buildMap {
            put("model_found", installState.exists)
            put("model_path", installState.resolvedPath ?: installState.modelId)
            put("model_sha256", installState.sha256Actual ?: installState.sha256Expected)
            put("role", installState.role)
            put("status", if (installState.ready) "READY" else installState.reason)
            put("ready", installState.ready)
        })}")
        appendTranscript(
            context = context,
            lines = listOf(
                "install_state=${formatMap(buildMap {
                    put("model_found", installState.exists)
                    put("model_path", installState.resolvedPath ?: installState.modelId)
                    put("model_sha256", installState.sha256Actual ?: installState.sha256Expected)
                    put("role", installState.role)
                    put("status", if (installState.ready) "READY" else installState.reason)
                    put("ready", installState.ready)
                })}"
            )
        )

        tokenizerProof?.let { proof ->
            Log.i(TAG, "PHASE_18_TOKENIZER_PROOF | ${formatMap(proof.asMap())}")
            appendTranscript(context, listOf("tokenizer_proof=${formatMap(proof.asMap())}"))
        } ?: Log.i(TAG, "PHASE_18_TOKENIZER_PROOF | skipped=true")

        inferenceProof?.let { proof ->
            Log.i(TAG, "PHASE_19_INFERENCE_PROOF | ${formatMap(proof.asMap())}")
            appendTranscript(context, listOf("inference_proof=${formatMap(proof.asMap())}"))

            val decodedText = proof.decodedText
            if (!decodedText.isNullOrBlank()) {
                val codec = BrainActionJsonCodec()
                val parsed = runCatching { codec.decode(decodedText) }.getOrNull()
                if (parsed != null) {
                    val safetyGate = SafetyGate()
                    val safetyDecision = safetyGate.evaluate(action = parsed, originalUserText = inferencePrompt)
                    Log.i(
                        TAG,
                        "PHASE_19_PARSED_ACTION | intent=${parsed.intent}, action_type=${parsed.actionType}, risk_level=${parsed.riskLevel.wireValue}, requires_confirmation=${parsed.requiresConfirmation}, safety_allowed=${safetyDecision.allowed}, json_parse_success=true, parsed_action_type=${parsed.actionType}, parsed_risk_level=${parsed.riskLevel.wireValue}"
                    )
                    appendTranscript(
                        context,
                        listOf(
                            "parsed_action=intent=${parsed.intent},action_type=${parsed.actionType},risk_level=${parsed.riskLevel.wireValue},requires_confirmation=${parsed.requiresConfirmation},safety_allowed=${safetyDecision.allowed}"
                        )
                    )
                } else {
                    Log.i(TAG, "PHASE_19_PARSED_ACTION | json_parse_success=false")
                    appendTranscript(context, listOf("parsed_action=json_parse_success=false"))
                }
            }
        } ?: Log.i(TAG, "PHASE_19_INFERENCE_PROOF | skipped=true")

        Log.i(TAG, "Model Install Storage: ${storage.describe(modelId)}")
        Log.i(TAG, "Runtime State Store: ${runtimeStateStore.find(modelId)?.modelRootPath ?: "none"}")
        Log.i(TAG, "------------------------------------------------")
        appendTranscript(
            context,
            listOf(
                "storage=${storage.describe(modelId)}",
                "runtime_state=${runtimeStateStore.find(modelId)?.modelRootPath ?: "none"}",
                "complete=true"
            )
        )
    }

    private fun formatMap(map: Map<String, Any?>): String {
        return map.entries.joinToString(separator = ", ") { (key, value) ->
            "$key=${formatValue(value)}"
        }
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"$value\""
            is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { item ->
                formatValue(item)
            }
            else -> value.toString()
        }
    }

    companion object {
        private const val TAG = "NovaLunaDiagnostic"
        const val ACTION_DIAGNOSE_RUNTIME = "com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_PACK_ID = "pack_id"
        private const val EXTRA_MODEL = "model"
        private const val EXTRA_ROLE = "role"
        private const val EXTRA_MODEL_PATH = "model_path"
        private const val DEFAULT_TOKENIZER_SAMPLE = "open camera"
        private const val DEFAULT_INFERENCE_PROMPT = "Open the camera"
    }

    private fun resolveModelId(rawValue: String?): com.nova.luna.modelinstall.ModelPackId {
        val normalized = rawValue?.trim().orEmpty()
        return when {
            normalized.equals("core", ignoreCase = true) ||
                normalized.contains("gemma", ignoreCase = true) -> com.nova.luna.modelinstall.ModelPackId.CORE
            normalized.equals("full", ignoreCase = true) ||
                normalized.contains("qwen", ignoreCase = true) ||
                normalized.contains("multilingual", ignoreCase = true) -> com.nova.luna.modelinstall.ModelPackId.FULL
            normalized.equals("lite", ignoreCase = true) ||
                normalized.contains("fallback", ignoreCase = true) ||
                normalized.contains("small", ignoreCase = true) -> com.nova.luna.modelinstall.ModelPackId.LITE
            else -> com.nova.luna.modelinstall.ModelPackId.fromWireValue(normalized) ?: com.nova.luna.modelinstall.ModelPackId.LITE
        }
    }

    private fun appendTranscript(context: Context, lines: List<String>) {
        runCatching {
            val dir = File(context.filesDir, "diagnostics")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "last_runtime_proof.txt")
            file.appendText(
                buildString {
                    appendLine("timestamp=${System.currentTimeMillis()}")
                    lines.forEach { line -> appendLine(line) }
                    appendLine("---")
                }
            )
        }.onFailure {
            Log.w(TAG, "Failed to write diagnostic transcript: ${it.message}")
        }
    }
}
