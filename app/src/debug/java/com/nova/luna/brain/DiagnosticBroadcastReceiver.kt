package com.nova.luna.brain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.safety.SafetyGate

/**
 * A debug-only receiver that allows ADB-triggered runtime diagnostics.
 * 
 * adb shell "am broadcast -p com.nova.luna.debug -a com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME --es command 'open camera'"
 */
class DiagnosticBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DIAGNOSE_RUNTIME) return

        val requestText = DiagnosticRequestResolver.resolve(
            command = intent.getStringExtra("command"),
            request = intent.getStringExtra("request")
        )
        val mode = intent.getStringExtra("mode") ?: "normal"
        
        Log.i(TAG, "Received diagnostic request: $requestText, mode: $mode")

        val pendingResult = goAsync()

        Thread {
            try {
                performPhase18NativeGgufDiagnostics(context, requestText, mode)
            } catch (e: Throwable) {
                Log.e(TAG, "Diagnostics failed", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun performPhase18NativeGgufDiagnostics(
        context: Context,
        requestText: String,
        mode: String
    ) {
        val storage = PrivateAppModelStorage.from(context)
        val config = BrainRuntimeConfig.fromBuildConfig()
        val preflight = LiteNativeProbePlanner.plan(storage)
        val nativeRuntime = LlamaCppJni()
        val engine = LiteLocalModelRuntime(
            modelFile = preflight.modelFile,
            modelId = PhoneLocalLlmModelId.QWEN_0_5B,
            realInferenceEnabled = true,
            nativeRuntime = nativeRuntime
        )

        val startTime = System.currentTimeMillis()
        val result = if (preflight.shouldRunNativeProbe) {
            engine.generate(requestText, 5000)
        } else {
            null
        }
        val duration = System.currentTimeMillis() - startTime

        val jsonAttempt = if (result != null && preflight.shouldRunNativeProbe) {
            val structuredRuntime = PhoneLocalLlmRuntime(
                config = PhoneLocalLlmConfig(
                    enabled = true,
                    maxInputTokens = 2048,
                    maxPromptChars = 4096,
                    timeoutMs = 5000,
                    models = listOf(
                        PhoneLocalLlmModelConfig(
                            id = PhoneLocalLlmModelId.QWEN_0_5B,
                            enabled = true,
                            assetPath = preflight.modelFile.absolutePath,
                            quantizedFileName = preflight.modelFile.name,
                            minimumRamMb = PhoneLocalLlmModelId.QWEN_0_5B.minimumRamMbHint,
                            maxInputTokens = 2048,
                            maxPromptChars = 4096,
                            timeoutMs = 5000,
                            priority = PhoneLocalLlmModelId.QWEN_0_5B.priority
                        )
                    )
                ),
                engine = engine
            )
            val routeDecision = BrainRouteDecision(
                selectedRole = BrainModelRole.ACTION_JSON,
                reason = "Phase 19 JSON/action attempt for open camera.",
                requiresInternet = false,
                requiresScreenContext = false,
                fallbackAllowed = true,
                safetyNotes = listOf(
                    "Phase 19 diagnostic JSON attempt only.",
                    "Do not execute phone actions directly."
                )
            )
            structuredRuntime.generateBrainAction(BrainRequest(rawText = requestText), routeDecision)
        } else {
            null
        }

        val action = result?.text?.let { BrainActionJsonCodec().decode(it) }
        val safetyGate = SafetyGate()
        val safetyDecision = action?.let { safetyGate.evaluate(it) }
        val runtimeAvailable = result?.success == true && nativeRuntime.isLoaded()
        val generationStatus = when {
            !preflight.modelEnabled -> PhoneLocalLlmStatus.MODEL_DISABLED
            !preflight.modelExists -> PhoneLocalLlmStatus.MODEL_ASSET_MISSING
            result != null -> result.status
            else -> PhoneLocalLlmStatus.UNAVAILABLE
        }
        val generationReason = result?.reason ?: preflight.reason

        Log.i(TAG, "--- NOVA/LUNA PHASE 18 NATIVE GGUF DIAGNOSTICS ---")
        Log.i(TAG, "Input: $requestText")
        Log.i(TAG, "Mode: $mode (native GGUF probe)")
        Log.i(TAG, "Model Role: ${BrainModelRole.LITE_FALLBACK.wireValue}")
        Log.i(TAG, "Model Path Checked: ${preflight.modelFile.absolutePath}")
        Log.i(TAG, "Model Enabled: ${preflight.modelEnabled}")
        Log.i(TAG, "Model Exists: ${preflight.modelExists}")
        Log.i(TAG, "Model Loaded: ${nativeRuntime.isLoaded()}")
        Log.i(TAG, "Runtime Available: $runtimeAvailable")
        Log.i(TAG, "LITE_REAL_INFERENCE_ENABLED: ${config.liteRealInferenceEnabled}")
        Log.i(TAG, "Probe Reason: ${preflight.reason}")
        Log.i(TAG, "Generation Status: $generationStatus")
        Log.i(TAG, "Generation Reason: $generationReason")
        Log.i(TAG, "Raw Native Response: ${result?.text}")
        Log.i(TAG, "Duration: ${duration}ms")
        Log.i(TAG, "Latency (Engine): ${result?.latencyMillis ?: 0L}ms")
        Log.i(TAG, "Engine Diagnostics: ${engine.diagnostics()}")
        Log.i(TAG, "Native Diagnostics: ${nativeRuntime.diagnostics()}")

        if (jsonAttempt != null) {
            Log.i(TAG, "JSON Attempt Raw Response: ${jsonAttempt.rawResponse}")
            Log.i(TAG, "JSON Parse Attempted: true")
            Log.i(TAG, "JSON Parse Success: ${jsonAttempt.jsonParsed && jsonAttempt.candidateAction != null && jsonAttempt.available}")
            Log.i(TAG, "Parsed Intent: ${jsonAttempt.candidateAction?.intent ?: "none"}")
            Log.i(TAG, "Parsed Risk Level: ${jsonAttempt.candidateAction?.riskLevel?.wireValue ?: "none"}")
            Log.i(TAG, "JSON Attempt Reason: ${jsonAttempt.reason}")
        }
        
        if (action != null) {
            Log.i(TAG, "Parsed Intent: ${action.intent}")
            Log.i(TAG, "Safety Decision: Allowed=${safetyDecision?.allowed}, Level=${safetyDecision?.level}")
            Log.i(TAG, "Safety Message: ${safetyDecision?.message}")
        } else {
            Log.i(TAG, "Safety Decision: not evaluated because no BrainAction was produced.")
            Log.i(TAG, "Safety Message: $generationReason")
        }
        Log.i(TAG, "------------------------------------------------")
    }

    companion object {
        private const val TAG = "NovaLunaDiagnostic"
        const val ACTION_DIAGNOSE_RUNTIME = "com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME"
    }
}
