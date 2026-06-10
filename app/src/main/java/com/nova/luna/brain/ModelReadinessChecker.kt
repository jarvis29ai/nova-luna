package com.nova.luna.brain

data class PhoneLocalLlmReadiness(
    val status: PhoneLocalLlmStatus,
    val selectedModel: PhoneLocalLlmModelConfig? = null,
    val runtimeAvailable: Boolean = false,
    val assetMissing: Boolean = false,
    val reason: String,
    val availableModelIds: List<PhoneLocalLlmModelId> = emptyList(),
    val timeoutMs: Long = 0L,
    val maxInputTokens: Int = 0,
    val maxPromptChars: Int = 0
) {
    val available: Boolean
        get() = status == PhoneLocalLlmStatus.READY

    val selectedModelId: PhoneLocalLlmModelId?
        get() = selectedModel?.id

    val selectedModelDisplayName: String?
        get() = selectedModel?.id?.displayName
}

class ModelReadinessChecker(
    private val assetLocator: ModelAssetLocator = ModelAssetLocator()
) {
    fun readinessStatus(
        config: PhoneLocalLlmConfig,
        engineAvailable: Boolean
    ): PhoneLocalLlmReadiness {
        val sanitized = config.sanitized()
        if (!sanitized.enabled) {
            return PhoneLocalLlmReadiness(
                status = PhoneLocalLlmStatus.DISABLED,
                selectedModel = sanitized.models.firstOrNull(),
                runtimeAvailable = false,
                assetMissing = true,
                reason = "Local phone LLM is disabled.",
                availableModelIds = sanitized.models.map { it.id },
                timeoutMs = sanitized.timeoutMs,
                maxInputTokens = sanitized.maxInputTokens,
                maxPromptChars = sanitized.maxPromptChars
            )
        }

        val enabledModels = sanitized.models
            .filter { it.enabled }
            .sortedBy { it.priority }

        if (enabledModels.isEmpty()) {
            return PhoneLocalLlmReadiness(
                status = PhoneLocalLlmStatus.MODEL_DISABLED,
                selectedModel = null,
                runtimeAvailable = false,
                assetMissing = true,
                reason = "No phone-local LLM models are enabled.",
                availableModelIds = emptyList(),
                timeoutMs = sanitized.timeoutMs,
                maxInputTokens = sanitized.maxInputTokens,
                maxPromptChars = sanitized.maxPromptChars
            )
        }

        val selectedModel = enabledModels.firstOrNull { assetLocator.exists(it) } ?: enabledModels.first()
        val selectedModelHasAsset = assetLocator.exists(selectedModel)
        val availableModelIds = enabledModels.filter { assetLocator.exists(it) }.map { it.id }

        return when {
            !selectedModelHasAsset -> PhoneLocalLlmReadiness(
                status = PhoneLocalLlmStatus.MODEL_ASSET_MISSING,
                selectedModel = selectedModel,
                runtimeAvailable = false,
                assetMissing = true,
                reason = buildString {
                    append("Phone-local LLM model asset is missing for ")
                    append(selectedModel.id.displayName)
                    append(".")
                },
                availableModelIds = availableModelIds,
                timeoutMs = sanitized.timeoutMs,
                maxInputTokens = sanitized.maxInputTokens,
                maxPromptChars = sanitized.maxPromptChars
            )

            !engineAvailable -> PhoneLocalLlmReadiness(
                status = PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE,
                selectedModel = selectedModel,
                runtimeAvailable = false,
                assetMissing = false,
                reason = buildString {
                    append("Phone-local LLM runtime is unavailable for ")
                    append(selectedModel.id.displayName)
                    append(".")
                },
                availableModelIds = availableModelIds,
                timeoutMs = sanitized.timeoutMs,
                maxInputTokens = sanitized.maxInputTokens,
                maxPromptChars = sanitized.maxPromptChars
            )

            else -> PhoneLocalLlmReadiness(
                status = PhoneLocalLlmStatus.READY,
                selectedModel = selectedModel,
                runtimeAvailable = true,
                assetMissing = false,
                reason = buildString {
                    append(selectedModel.id.displayName)
                    append(" is ready.")
                },
                availableModelIds = availableModelIds,
                timeoutMs = sanitized.timeoutMs,
                maxInputTokens = sanitized.maxInputTokens,
                maxPromptChars = sanitized.maxPromptChars
            )
        }
    }
}
