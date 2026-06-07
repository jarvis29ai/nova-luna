package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class PhoneLocalLlmProvider(
    private val runtime: PhoneLocalLlmRuntime = PhoneLocalLlmRuntime(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : PhoneBrainProvider {
    override val available: Boolean
        get() = runtime.available()

    override fun analyze(request: BrainRequest): String {
        return diagnose(request).extractedJson
            ?: throw IllegalStateException("Phone-local LLM did not return strict BrainAction JSON.")
    }

    override fun diagnose(request: BrainRequest): BrainProviderTrace {
        val readiness = runtime.readinessStatus()
        val routeDecision = BrainRouteDecision(
            selectedRole = BrainModelRole.GEMMA_REASONING,
            reason = "Flexible local reasoning request.",
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf(
                "Phone-local LLM may only produce safe JSON."
            )
        )

        if (!readiness.available) {
            return BrainProviderTrace(
                providerName = this::class.java.simpleName,
                rawResponse = null,
                extractedJson = null,
                parsedAction = null,
                error = readiness.reason
            )
        }

        val prompt = runtime.buildPrompt(request, routeDecision)
        val generation = runtime.generateJson(prompt)
        val parseResult = PhoneLocalLlmOutputParser(codec, BrainActionValidator()).parse(generation.text.orEmpty())

        return BrainProviderTrace(
            providerName = this::class.java.simpleName,
            rawResponse = generation.text,
            extractedJson = parseResult.extractedJson,
            parsedAction = parseResult.candidateAction,
            error = when {
                !generation.success -> generation.reason
                !parseResult.accepted -> parseResult.reason
                else -> null
            }
        )
    }
}
