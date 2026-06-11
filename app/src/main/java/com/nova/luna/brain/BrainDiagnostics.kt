package com.nova.luna.brain

import com.nova.luna.agent.AgentLoopStopReason
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.BrainRuntimeStatus
import com.nova.luna.model.InternetPermissionDecision
import com.nova.luna.model.SafetyDecision
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.LocalUserPreferences
import com.nova.luna.memory.PendingConfirmationType
import com.nova.luna.memory.RecoveryState
import com.nova.luna.modelinstall.ModelInstallDiagnostics

data class BrainProviderTrace(
    val providerName: String,
    val rawResponse: String?,
    val extractedJson: String?,
    val parsedAction: BrainAction?,
    val error: String? = null
)

data class BrainRouterTrace(
    val brain_router_used: Boolean,
    val selected_model_role: BrainModelRole? = null,
    val mock_fallback_used: Boolean,
    val fallback_reason: String? = null,
    val real_model_invoked: Boolean,
    val real_inference: Boolean,
    val native_generation_available: Boolean,
    val json_parse_attempted: Boolean,
    val json_parse_success: Boolean
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
    val routerTrace: BrainRouterTrace? = null,
    val runtimeStatus: BrainRuntimeStatus? = null,
    val internetPermissionDecision: InternetPermissionDecision? = null,
    val onlineTrace: OnlineAiTrace? = null,
    val activeSessionType: BrainSessionType? = null,
    val pendingConfirmationId: String? = null,
    val pendingConfirmationType: PendingConfirmationType? = null,
    val recoveryState: RecoveryState? = null,
    val memorySessionCount: Int = 0,
    val memoryPendingConfirmationCount: Int = 0,
    val preferences: LocalUserPreferences? = null,
    val agentLoopCandidate: Boolean = false,
    val agentLoopStarted: Boolean = false,
    val agentLoopId: String? = null,
    val agentLoopStepCount: Int = 0,
    val agentLoopStopReason: AgentLoopStopReason? = null,
    val agentLoopRecoveryUsed: Boolean = false,
    val agentLoopAskedUser: Boolean = false,
    val agentLoopVerificationMessage: String? = null,
    val modelInstallDiagnostics: ModelInstallDiagnostics? = null,
    val sessionTrace: ModelRuntimeSessionTrace? = null,
    val phase23_command_understanding: Boolean = true,
    val brain_action_created: Boolean = false,
    val action_intent: String? = null,
    val action_type: String? = null,
    val risk_level: String? = null,
    val requires_confirmation: Boolean? = null,
    val action_confidence: Double? = null,
    val action_source: String? = null,
    val action_params: Map<String, String>? = null,
    val action_assistant_reply: String? = null
)

interface BrainProviderDiagnostics {
    fun diagnose(request: BrainRequest): BrainProviderTrace
}
