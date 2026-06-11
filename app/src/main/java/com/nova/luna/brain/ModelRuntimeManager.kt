package com.nova.luna.brain

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole

class ModelRuntimeManager(
    private val loader: ModelRuntimeLoader,
    private val ramGuard: ModelRamGuard,
    private val catalog: BrainModelCatalog = BrainModelCatalog
) {
    private var currentLoadedRole: BrainModelRole? = null
    private var currentEngine: PhoneLocalLlmEngine? = null

    private var modelSwitchCount = 0L
    private var unloadCount = 0L
    private var loadCount = 0L
    private var fallbackCount = 0L
    private var lastRamGuardDecision: RamGuardDecision? = null
    private var lastLoadError: String? = null

    @Synchronized
    fun getEngineForRole(role: BrainModelRole): Pair<PhoneLocalLlmEngine, ModelRuntimeSessionTrace> {
        val requestedRole = role
        var selectedRole = role
        var switched = false
        var unloadedPrevious = false
        var reusedLoadedModel = false
        var fallbackUsed = false
        var fallbackReason: String? = null

        // 1. Check RAM Guard
        val decision = ramGuard.decide(requestedRole, currentLoadedRole)
        lastRamGuardDecision = decision

        when (decision) {
            RamGuardDecision.ALLOW -> {
                // Proceed with normal loading
            }
            RamGuardDecision.ALLOW_WITH_UNLOAD -> {
                if (currentLoadedRole != null && currentLoadedRole != requestedRole) {
                    unloadCurrentModel()
                    unloadedPrevious = true
                }
            }
            RamGuardDecision.BLOCK_USE_LITE_FALLBACK -> {
                if (requestedRole != BrainModelRole.LITE_FALLBACK) {
                    selectedRole = BrainModelRole.LITE_FALLBACK
                    fallbackUsed = true
                    fallbackReason = "RAM_GUARD_BLOCK_USE_LITE"
                    fallbackCount++
                }
            }
            RamGuardDecision.BLOCK_NO_SAFE_MODEL -> {
                return UnavailablePhoneLocalLlmEngine() to createTrace(
                    requestedRole = requestedRole,
                    selectedRole = null,
                    switched = false,
                    unloadedPrevious = false,
                    reusedLoadedModel = false,
                    fallbackUsed = true,
                    fallbackReason = "RAM_GUARD_BLOCK_NO_SAFE_MODEL"
                )
            }
        }

        // 2. Reuse or Load
        if (currentLoadedRole == selectedRole && currentEngine != null) {
            reusedLoadedModel = true
        } else {
            if (currentLoadedRole != null) {
                unloadCurrentModel()
                unloadedPrevious = true
            }

            val engine = loader.loadForRole(selectedRole)
            if (engine.available()) {
                currentEngine = engine
                currentLoadedRole = selectedRole
                loadCount++
                switched = true
                modelSwitchCount++
            } else {
                // If selected role (even fallback) is not available, try LITE_FALLBACK as last resort if not already tried
                if (selectedRole != BrainModelRole.LITE_FALLBACK) {
                    selectedRole = BrainModelRole.LITE_FALLBACK
                    fallbackUsed = true
                    fallbackReason = "SELECTED_MODEL_UNAVAILABLE"
                    fallbackCount++
                    
                    val fallbackEngine = loader.loadForRole(selectedRole)
                    if (fallbackEngine.available()) {
                        currentEngine = fallbackEngine
                        currentLoadedRole = selectedRole
                        loadCount++
                        switched = true
                        modelSwitchCount++
                    } else {
                        return UnavailablePhoneLocalLlmEngine() to createTrace(
                            requestedRole = requestedRole,
                            selectedRole = null,
                            switched = false,
                            unloadedPrevious = unloadedPrevious,
                            reusedLoadedModel = false,
                            fallbackUsed = true,
                            fallbackReason = "ALL_MODELS_UNAVAILABLE"
                        )
                    }
                } else {
                    return UnavailablePhoneLocalLlmEngine() to createTrace(
                        requestedRole = requestedRole,
                        selectedRole = null,
                        switched = false,
                        unloadedPrevious = unloadedPrevious,
                        reusedLoadedModel = false,
                        fallbackUsed = true,
                        fallbackReason = "LITE_MODEL_UNAVAILABLE"
                    )
                }
            }
        }

        return (currentEngine ?: UnavailablePhoneLocalLlmEngine()) to createTrace(
            requestedRole = requestedRole,
            selectedRole = selectedRole,
            switched = switched,
            unloadedPrevious = unloadedPrevious,
            reusedLoadedModel = reusedLoadedModel,
            fallbackUsed = fallbackUsed,
            fallbackReason = fallbackReason
        )
    }

    @Synchronized
    fun unloadCurrentModel() {
        currentEngine?.unload()
        currentEngine = null
        currentLoadedRole = null
        unloadCount++
    }

    fun getCurrentLoadedRole(): BrainModelRole? = currentLoadedRole

    private fun createTrace(
        requestedRole: BrainModelRole,
        selectedRole: BrainModelRole?,
        switched: Boolean,
        unloadedPrevious: Boolean,
        reusedLoadedModel: Boolean,
        fallbackUsed: Boolean,
        fallbackReason: String?
    ): ModelRuntimeSessionTrace {
        return ModelRuntimeSessionTrace(
            requestedRole = requestedRole,
            selectedRole = selectedRole,
            switched = switched,
            unloadedPrevious = unloadedPrevious,
            reusedLoadedModel = reusedLoadedModel,
            fallbackUsed = fallbackUsed,
            fallbackReason = fallbackReason,
            ramGuardDecision = lastRamGuardDecision?.name ?: "UNKNOWN",
            modelSwitchCount = modelSwitchCount,
            unloadCount = unloadCount,
            loadCount = loadCount,
            fallbackCount = fallbackCount,
            lastLoadError = lastLoadError
        )
    }

    fun diagnostics(): String {
        return buildString {
            append("currentLoadedRole=${currentLoadedRole?.wireValue ?: "none"}, ")
            append("modelSwitchCount=$modelSwitchCount, ")
            append("unloadCount=$unloadCount, ")
            append("loadCount=$loadCount, ")
            append("fallbackCount=$fallbackCount, ")
            append("lastRamGuardDecision=${lastRamGuardDecision?.name ?: "none"}")
        }
    }
}

data class ModelRuntimeSessionTrace(
    val requestedRole: BrainModelRole,
    val selectedRole: BrainModelRole?,
    val switched: Boolean,
    val unloadedPrevious: Boolean,
    val reusedLoadedModel: Boolean,
    val fallbackUsed: Boolean,
    val fallbackReason: String?,
    val ramGuardDecision: String,
    val modelSwitchCount: Long,
    val unloadCount: Long,
    val loadCount: Long,
    val fallbackCount: Long,
    val lastLoadError: String?
)
