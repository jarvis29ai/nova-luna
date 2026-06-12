package com.nova.luna.brain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

        val requestText = DiagnosticRequestResolver.resolve(
            command = intent.getStringExtra("command"),
            request = intent.getStringExtra("request"),
            fallback = DEFAULT_TOKENIZER_SAMPLE
        )
        val mode = intent.getStringExtra("mode") ?: "all"

        Log.i(TAG, "Received diagnostic request: $requestText, mode: $mode")

        val pendingResult = goAsync()

        Thread {
            try {
                performPhase18And19Diagnostics(context, requestText, mode, intent)
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
        intent: Intent
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
        val normalizedMode = mode.trim().lowercase()
        val runTokenizer = normalizedMode in setOf("all", "tokenizer", "phase18")
        val runInference = normalizedMode in setOf("all", "inference", "phase19")
        val inferencePrompt = intent.getStringExtra("inference_prompt")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_INFERENCE_PROMPT

        val tokenizerProof = if (runTokenizer) {
            proofRunner.runTokenizerProof(
                modelId = DEFAULT_MODEL_ID,
                sampleText = requestText
            )
        } else {
            null
        }

        val inferenceProof = if (runInference) {
            proofRunner.runInferenceProof(
                modelId = DEFAULT_MODEL_ID,
                promptText = inferencePrompt
            )
        } else {
            null
        }

        Log.i(TAG, "--- NOVA/LUNA PHASE 18/19 MODEL PROOF ---")
        Log.i(TAG, "Input: $requestText")
        Log.i(TAG, "Mode: $mode")
        Log.i(TAG, "Model Role: $DEFAULT_MODEL_ID")

        tokenizerProof?.let { proof ->
            Log.i(TAG, "PHASE_18_TOKENIZER_PROOF | ${formatMap(proof.asMap())}")
        } ?: Log.i(TAG, "PHASE_18_TOKENIZER_PROOF | skipped=true")

        inferenceProof?.let { proof ->
            Log.i(TAG, "PHASE_19_INFERENCE_PROOF | ${formatMap(proof.asMap())}")

            val decodedText = proof.decodedText
            if (!decodedText.isNullOrBlank()) {
                val codec = BrainActionJsonCodec()
                val parsed = runCatching { codec.decode(decodedText) }.getOrNull()
                if (parsed != null) {
                    val safetyGate = SafetyGate()
                    val safetyDecision = safetyGate.evaluate(action = parsed, originalUserText = inferencePrompt)
                    Log.i(TAG, "PHASE_19_PARSED_ACTION | intent=${parsed.intent}, action_type=${parsed.actionType}, risk_level=${parsed.riskLevel.wireValue}, requires_confirmation=${parsed.requiresConfirmation}, safety_allowed=${safetyDecision.allowed}")
                } else {
                    Log.i(TAG, "PHASE_19_PARSED_ACTION | json_parse_success=false")
                }
            }
        } ?: Log.i(TAG, "PHASE_19_INFERENCE_PROOF | skipped=true")

        Log.i(TAG, "Model Install Storage: ${storage.describe(com.nova.luna.modelinstall.ModelPackId.LITE)}")
        Log.i(TAG, "Runtime State Store: ${runtimeStateStore.find(com.nova.luna.modelinstall.ModelPackId.LITE)?.modelRootPath ?: "none"}")
        Log.i(TAG, "------------------------------------------------")
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
        private const val DEFAULT_MODEL_ID = "lite"
        private const val DEFAULT_TOKENIZER_SAMPLE = "open camera"
        private const val DEFAULT_INFERENCE_PROMPT = """Return JSON only: {"actionType":"OPEN_CAMERA","riskLevel":"LOW"}"""
    }
}
