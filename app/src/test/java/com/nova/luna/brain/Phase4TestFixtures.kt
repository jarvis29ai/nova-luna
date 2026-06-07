package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import java.io.File

fun tempModelFilePath(prefix: String = "gemma-phone-model"): String {
    return File.createTempFile(prefix, ".bin").apply {
        deleteOnExit()
    }.absolutePath
}

fun gemmaPhoneConfig(
    gemmaEnabled: Boolean = true,
    gemmaModelAssetPath: String = tempModelFilePath(),
    gemmaMaxTokens: Int = 128,
    gemmaTemperature: Double = 0.2,
    gemmaTopK: Int = 40,
    gemmaContextWindow: Int = 8192,
    gemmaRoleEnabled: Boolean = true
): GemmaPhoneConfig {
    return GemmaPhoneConfig(
        gemmaEnabled = gemmaEnabled,
        gemmaModelAssetPath = gemmaModelAssetPath,
        gemmaMaxTokens = gemmaMaxTokens,
        gemmaTemperature = gemmaTemperature,
        gemmaTopK = gemmaTopK,
        gemmaContextWindow = gemmaContextWindow,
        gemmaRoleEnabled = gemmaRoleEnabled
    )
}

class StaticGemmaBackend(
    override val backendName: String = "StaticGemmaBackend",
    private val runtimeAvailable: Boolean = true,
    private val response: String = safeGemmaJson()
) : PhoneGemmaRuntimeBackend {
    override fun isRuntimeAvailable(): Boolean = runtimeAvailable

    override fun generate(prompt: String, config: GemmaPhoneConfig): String {
        check(runtimeAvailable) {
            "Gemma backend should not be called when it is unavailable."
        }

        return response
    }
}

class FakePhoneBrainProvider(
    override val available: Boolean = true,
    private val response: String = safeProviderJson()
) : PhoneBrainProvider {
    override fun analyze(request: BrainRequest): String = response

    override fun diagnose(request: BrainRequest): BrainProviderTrace {
        return BrainProviderTrace(
            providerName = this::class.java.simpleName,
            rawResponse = response,
            extractedJson = response,
            parsedAction = BrainAction(
                intent = "flexible_reasoning",
                reply = "Flexible reasoning placeholder.",
                actionType = BrainActionType.READ_ONLY,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf("rawText" to request.rawText)
            )
        )
    }
}

fun safeGemmaJson(): String {
    val codec = BrainActionJsonCodec()
    return codec.encode(
        BrainAction(
            intent = "explain",
            reply = "Gemma reasoning placeholder.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "placeholder")
        )
    )
}

fun dangerousGemmaJson(): String {
    return """
        {
          "intent": "cab_booking",
          "reply": "Ready to book and pay now.",
          "actionType": "prepare",
          "riskLevel": "confirmation_required",
          "requiresConfirmation": true,
          "finalActionAllowed": true,
          "params": {
            "rawText": "please help me book a cab"
          },
          "nextQuestion": "Confirm booking now?"
        }
    """.trimIndent()
}

fun safeProviderJson(): String {
    val codec = BrainActionJsonCodec()
    return codec.encode(
        BrainAction(
            intent = "flexible_reasoning",
            reply = "Flexible reasoning placeholder.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to "placeholder")
        )
    )
}
