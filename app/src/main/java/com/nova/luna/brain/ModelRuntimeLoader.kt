package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.modelinstall.ModelInstallService

/**
 * Resolves READY model files from ModelInstallStorage and provides the appropriate PhoneLocalLlmEngine.
 */
class ModelRuntimeLoader(
    private val storage: PrivateAppModelStorage,
    private val modelInstallService: ModelInstallService,
    private val liteRealInferenceEnabled: Boolean = false
) {
    /**
     * Resolves the engine for a given role.
     * If the model is not ready, it returns an UnavailablePhoneLocalLlmEngine.
     */
    fun loadForRole(role: BrainModelRole): PhoneLocalLlmEngine {
        val installModelId = when (role) {
            BrainModelRole.CORE_BRAIN -> "core"
            BrainModelRole.MULTILINGUAL_BACKUP -> "full"
            BrainModelRole.LITE_FALLBACK -> "lite"
            else -> null
        } ?: return UnavailablePhoneLocalLlmEngine()

        val verifiedPath = modelInstallService.getReadyModelPath(installModelId) ?: return UnavailablePhoneLocalLlmEngine()

        val modelFile = java.io.File(verifiedPath)
        val modelEnum = modelIdForRole(role) ?: return UnavailablePhoneLocalLlmEngine()

        return when (role) {
            BrainModelRole.LITE_FALLBACK -> LiteLocalModelRuntime(
                modelFile = modelFile,
                modelId = modelEnum,
                realInferenceEnabled = liteRealInferenceEnabled
            )
            // Phase 21: Native runtime should use verified model path.
            // Future phases will add CORE and MULTILINGUAL runtimes here.
            else -> UnavailablePhoneLocalLlmEngine()
        }
    }

    private fun packIdForRole(role: BrainModelRole): ModelPackId? {
        return when (role) {
            BrainModelRole.CORE_BRAIN -> ModelPackId.CORE
            BrainModelRole.MULTILINGUAL_BACKUP -> ModelPackId.FULL
            BrainModelRole.LITE_FALLBACK -> ModelPackId.LITE
            else -> null
        }
    }

    private fun modelIdForRole(role: BrainModelRole): PhoneLocalLlmModelId? {
        return when (role) {
            BrainModelRole.CORE_BRAIN -> PhoneLocalLlmModelId.GEMMA_3N
            BrainModelRole.MULTILINGUAL_BACKUP -> PhoneLocalLlmModelId.QWEN_1_5B
            BrainModelRole.LITE_FALLBACK -> PhoneLocalLlmModelId.QWEN_0_5B
            else -> null
        }
    }
}
