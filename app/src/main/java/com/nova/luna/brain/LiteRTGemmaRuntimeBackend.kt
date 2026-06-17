package com.nova.luna.brain

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import android.util.Log

class LiteRTGemmaRuntimeBackend(private val context: Context) : PhoneGemmaRuntimeBackend {
    override val backendName: String = "LiteRTGemmaRuntimeBackend"

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    override fun isRuntimeAvailable(): Boolean {
        // Basic check: we assume it's available if we can reach this code
        // Real-world: could check device RAM or GPU support if needed.
        return true
    }

    override fun generate(prompt: String, config: GemmaPhoneConfig): String {
        val modelPath = config.gemmaModelAssetPath
        if (modelPath.isBlank() || !File(modelPath).exists()) {
            Log.e("LiteRTGemma", "Model file not found: $modelPath")
            return "Error: Gemma model file not found."
        }

        try {
            synchronized(this) {
                if (llmInference == null || loadedModelPath != modelPath) {
                    Log.i("LiteRTGemma", "Loading model from $modelPath")
                    llmInference?.close()
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(config.gemmaMaxTokens)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    loadedModelPath = modelPath
                    Log.i("LiteRTGemma", "Model loaded successfully.")
                }
            }

            return llmInference?.generateResponse(prompt) ?: "Error: Failed to initialize LiteRT Gemma inference."
        } catch (e: Exception) {
            Log.e("LiteRTGemma", "Inference failed", e)
            return "Error: Gemma inference failed: ${e.message}"
        }
    }
}
