package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole

/**
 * A proxy engine that dynamically resolves the underlying PhoneLocalLlmEngine 
 * for a specific role using ModelRuntimeLoader. This allows the system to 
 * transition from unavailable to ready when model files become available 
 * in private storage without re-instantiating the BrainService.
 */
class DynamicModelRuntime(
    private val role: BrainModelRole,
    private val loader: ModelRuntimeLoader
) : PhoneLocalLlmEngine {
    
    private val delegate: PhoneLocalLlmEngine
        get() = loader.loadForRole(role)

    override val engineName: String 
        get() = delegate.engineName

    override fun available(): Boolean = delegate.available()

    override fun readinessStatus(): PhoneLocalLlmStatus = delegate.readinessStatus()

    override fun modelId(): PhoneLocalLlmModelId? = delegate.modelId()

    override fun modelDisplayName(): String? = delegate.modelDisplayName()

    override fun maxInputTokens(): Int = delegate.maxInputTokens()

    override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        return delegate.generate(prompt, timeoutMs)
    }

    override fun generateJson(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        return delegate.generateJson(prompt, timeoutMs)
    }

    override fun cancel(): Boolean = delegate.cancel()

    override fun diagnostics(): String = delegate.diagnostics()
}
