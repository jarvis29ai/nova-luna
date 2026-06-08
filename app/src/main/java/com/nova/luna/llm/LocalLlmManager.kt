package com.nova.luna.llm

import android.content.Context
import com.nova.luna.brain.PhoneLocalLlmRuntime

open class LocalLlmManager(
    private val context: Context,
    private val config: LocalLlmConfig = LocalLlmConfig(),
    private val promptBuilder: LocalLlmPromptBuilder = LocalLlmPromptBuilder(),
    private val outputParser: LocalLlmOutputParser = LocalLlmOutputParser(),
    private val readinessChecker: LocalLlmReadinessChecker = LocalLlmReadinessChecker(context)
) {
    private val phoneRuntime = PhoneLocalLlmRuntime() // Existing stub/engine bridge

    open fun process(request: LocalLlmRequest): LocalLlmResult {
        // 1. Select Model and Check Readiness
        val modelConfig = config.models.find { it.modelId == request.modelId }
            ?: return failure(request, LocalLlmStatus.NOT_CONFIGURED, "Model config not found")
            
        val status = readinessChecker.check(modelConfig)
        if (status != LocalLlmStatus.READY) {
            return failure(request, status, "Model not ready: $status")
        }

        // 2. Build Prompt
        val prompt = promptBuilder.build(request)

        // 3. Generate (via existing phoneRuntime bridge)
        val startedAt = System.currentTimeMillis()
        val generationResult = phoneRuntime.generate(prompt)
        val latency = System.currentTimeMillis() - startedAt

        if (!generationResult.success) {
            return failure(request, LocalLlmStatus.FAILED, "Generation failed: ${generationResult.reason}")
        }

        // 4. Parse Output
        val parseResult = outputParser.parse(generationResult.text ?: "", request)
        
        return parseResult.copy(latencyMs = latency)
    }

    private fun failure(req: LocalLlmRequest, status: LocalLlmStatus, reason: String) = LocalLlmResult(
        status = status,
        modelId = req.modelId,
        modelDisplayName = req.modelId.name,
        technicalReason = reason
    )
}
