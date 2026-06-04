package com.nova.luna.brain

class UnavailablePhoneBrainProvider : PhoneBrainProvider {
    override val available: Boolean = false

    override fun analyze(request: BrainRequest): String {
        throw IllegalStateException("No phone-local brain model is integrated yet.")
    }

    override fun diagnose(request: BrainRequest): BrainProviderTrace {
        return BrainProviderTrace(
            providerName = this::class.java.simpleName,
            rawResponse = null,
            extractedJson = null,
            parsedAction = null,
            error = "phone_model_unavailable"
        )
    }
}
