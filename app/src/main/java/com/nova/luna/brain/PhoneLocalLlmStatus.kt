package com.nova.luna.brain

enum class PhoneLocalLlmStatus(val wireValue: String) {
    READY("ready"),
    DISABLED("disabled"),
    MODEL_DISABLED("model_disabled"),
    MODEL_ASSET_MISSING("model_asset_missing"),
    MODEL_RUNTIME_NOT_AVAILABLE("model_runtime_not_available"),
    RUNTIME_UNAVAILABLE("runtime_unavailable"),
    PROMPT_TOO_LARGE("prompt_too_large"),
    OUTPUT_PARSE_FAILED("output_parse_failed"),
    VALIDATION_REJECTED("validation_rejected"),
    SAFETY_REJECTED("safety_rejected"),
    CANCELLED("cancelled"),
    UNAVAILABLE("unavailable");

    companion object {
        fun fromWireValue(value: String?): PhoneLocalLlmStatus? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
