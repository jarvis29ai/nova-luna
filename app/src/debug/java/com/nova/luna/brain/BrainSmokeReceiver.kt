package com.nova.luna.brain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BrainSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_BRAIN_SMOKE) {
            return
        }

        val config = BrainRuntimeConfig.fromBuildConfig()

        BrainSmokeLogger.i(
            "smoke_receiver_received",
            mapOf(
                "action" to intent.action,
                "component" to intent.component?.flattenToString(),
                "brain_provider" to config.brainProvider,
                "llm_enabled" to config.llmEnabled,
                "ollama_base_url" to config.ollamaBaseUrl,
                "ollama_model" to config.ollamaModel
            )
        )

        val pendingResult = goAsync()

        Thread {
            try {
                runSmoke()
            } catch (throwable: Throwable) {
                BrainSmokeLogger.e(
                    "smoke_failed",
                    mapOf("error" to (throwable.message ?: throwable::class.java.simpleName)),
                    throwable
                )
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun runSmoke() {
        val service = BrainService()
        val phrases = BrainSmokePhraseCatalog.phrases()
        val config = BrainRuntimeConfig.fromBuildConfig()

        BrainSmokeLogger.i(
            "smoke_start",
            mapOf(
                "count" to phrases.size,
                "provider" to config.brainProvider
            )
        )

        phrases.forEachIndexed { index, sample ->
            val diagnostics = service.diagnose(sample.text)
            BrainSmokeLogger.i(
                "smoke_case_result",
                mapOf(
                    "index" to (index + 1).toString(),
                    "category" to sample.category,
                    "user_input" to diagnostics.userInput,
                    "provider_used" to diagnostics.finalProvider,
                    "selected_provider" to diagnostics.selectedProvider,
                    "raw_model_response" to diagnostics.rawModelResponse,
                    "extracted_brain_action_json" to diagnostics.extractedBrainActionJson,
                    "parsed_brain_action" to diagnostics.parsedBrainAction,
                    "validator_result" to diagnostics.validatorResult,
                    "fallback_used" to diagnostics.fallbackUsed,
                    "final_provider" to diagnostics.finalProvider,
                    "final_brain_action" to diagnostics.finalBrainAction,
                    "final_safety_decision" to diagnostics.finalSafetyDecision,
                    "runtime_status" to diagnostics.runtimeStatus,
                    "internet_permission_decision" to diagnostics.internetPermissionDecision
                )
            )
        }

        BrainSmokeLogger.i(
            "smoke_complete",
            mapOf("count" to phrases.size)
        )
    }

    companion object {
        const val ACTION_RUN_BRAIN_SMOKE = "com.nova.luna.debug.ACTION_RUN_BRAIN_SMOKE"
    }
}
