package com.nova.luna.brain

data class PhoneLocalLlmGenerationResult(
    val status: PhoneLocalLlmStatus,
    val text: String? = null,
    val reason: String,
    val modelId: PhoneLocalLlmModelId? = null,
    val modelDisplayName: String? = null,
    val latencyMillis: Long? = null,
    val jsonOnly: Boolean = false
) {
    val success: Boolean
        get() = status == PhoneLocalLlmStatus.READY && !text.isNullOrBlank()

    companion object {
        fun unavailable(
            status: PhoneLocalLlmStatus,
            reason: String,
            modelId: PhoneLocalLlmModelId? = null,
            modelDisplayName: String? = null
        ): PhoneLocalLlmGenerationResult {
            return PhoneLocalLlmGenerationResult(
                status = status,
                text = null,
                reason = reason,
                modelId = modelId,
                modelDisplayName = modelDisplayName
            )
        }
    }
}

interface PhoneLocalLlmEngine {
    val engineName: String

    fun available(): Boolean

    fun readinessStatus(): PhoneLocalLlmStatus

    fun modelId(): PhoneLocalLlmModelId?

    fun modelDisplayName(): String?

    fun maxInputTokens(): Int

    fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult

    fun generateJson(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        return generate(prompt, timeoutMs)
    }

    fun cancel(): Boolean

    fun diagnostics(): String
}

class UnavailablePhoneLocalLlmEngine : PhoneLocalLlmEngine {
    override val engineName: String = "UnavailablePhoneLocalLlmEngine"

    override fun available(): Boolean = false

    override fun readinessStatus(): PhoneLocalLlmStatus = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE

    override fun modelId(): PhoneLocalLlmModelId? = null

    override fun modelDisplayName(): String? = null

    override fun maxInputTokens(): Int = 0

    override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
        return PhoneLocalLlmGenerationResult.unavailable(
            status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
            reason = "No phone-local inference engine is wired yet."
        )
    }

    override fun cancel(): Boolean = false

    override fun diagnostics(): String = "engine=$engineName, status=${PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE.wireValue}"
}
