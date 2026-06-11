package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole

/**
 * A proxy engine that dynamically resolves the underlying PhoneLocalLlmEngine 
 * for a specific role using ModelRuntimeManager. This allows the system to 
 * transition from unavailable to ready when model files become available 
 * in private storage without re-instantiating the BrainService.
 */
class DynamicModelRuntime(
    private val role: BrainModelRole,
    private val manager: ModelRuntimeManager
) : PhoneLocalLlmEngine {
    
    private val engine: PhoneLocalLlmEngine
        get() = manager.getEngineForRole(role).first

    override val engineName: String 
        get() = engine.engineName

    override fun available(): Boolean = engine.available()

    override fun readinessStatus(): PhoneLocalLlmStatus = engine.readinessStatus()

    override fun modelId(): PhoneLocalLlmModelId? = engine.modelId()

    override fun modelDisplayName(): String? = engine.modelDisplayName()

    override fun maxInputTokens(): Int = engine.maxInputTokens()

    override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        return engine.generate(prompt, timeoutMs)
    }

    override fun generateJson(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        return engine.generateJson(prompt, timeoutMs)
    }

    override fun cancel(): Boolean = engine.cancel()

    override fun unload(): Boolean = engine.unload()

    override fun diagnostics(): String = engine.diagnostics()
}
