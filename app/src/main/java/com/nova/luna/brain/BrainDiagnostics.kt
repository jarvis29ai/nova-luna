package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.BrainRuntimeStatus
import com.nova.luna.model.InternetPermissionDecision
import com.nova.luna.model.SafetyDecision

data class BrainProviderTrace(
    val providerName: String,
    val rawResponse: String?,
    val extractedJson: String?,
    val parsedAction: BrainAction?,
    val error: String? = null
)

data class BrainDiagnostics(
    val userInput: String,
    val activeCabSession: Boolean,
    val selectedProvider: String,
    val selectedRole: BrainModelRole? = null,
    val routeDecision: BrainRouteDecision? = null,
    val rawModelResponse: String?,
    val extractedBrainActionJson: String?,
    val parsedBrainAction: BrainAction?,
    val modelAvailable: Boolean? = null,
    val validatorResult: Boolean,
    val fallbackUsed: Boolean,
    val finalProvider: String,
    val finalBrainAction: BrainAction,
    val finalSafetyDecision: SafetyDecision,
    val runtimeStatus: BrainRuntimeStatus? = null,
    val internetPermissionDecision: InternetPermissionDecision? = null,
    val onlineTrace: OnlineAiTrace? = null
)

interface BrainProviderDiagnostics {
    fun diagnose(request: BrainRequest): BrainProviderTrace
}
