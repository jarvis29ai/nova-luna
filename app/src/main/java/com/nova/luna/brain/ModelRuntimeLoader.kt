package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.modelinstall.LocalRuntimeReadinessChecker

/**
 * Resolves READY model files from ModelInstallStorage and provides the appropriate PhoneLocalLlmEngine.
 */
class ModelRuntimeLoader(
    private val storage: PrivateAppModelStorage,
    private val readinessChecker: LocalRuntimeReadinessChecker,
    private val liteRealInferenceEnabled: Boolean = false
) {
    /**
     * Resolves the engine for a given role.
     * If the model is not ready, it returns an UnavailablePhoneLocalLlmEngine.
     */
    fun loadForRole(role: BrainModelRole): PhoneLocalLlmEngine {
        val packId = packIdForRole(role) ?: return UnavailablePhoneLocalLlmEngine()

        // Check if the pack is installed and ready in private storage
        if (!readinessChecker.installReady(packId)) {
            return UnavailablePhoneLocalLlmEngine()
        }

        val modelId = modelIdForRole(role) ?: return UnavailablePhoneLocalLlmEngine()
        val modelFile = storage.packFile(packId, modelId.defaultQuantizedFileName)

        return when (packId) {
            ModelPackId.LITE -> LiteLocalModelRuntime(
                modelFile = modelFile,
                modelId = modelId,
                realInferenceEnabled = liteRealInferenceEnabled
            )
            // For Phase 8, we only implement Lite runtime. 
            // CORE and FULL can use placeholders or Lite fallback if appropriate, 
            // but the requirement says "Start with Lite fallback runtime path only".
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
