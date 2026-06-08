package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

enum class UnifiedDomain {
    MEDIA,
    MUSIC,
    FOOD,
    GROCERY,
    CAB,
    SHOPPING,
    COMMUNICATION,
    CONTENT,
    PHONE_CONTROL,
    SYSTEM_NAVIGATION,
    SETTINGS,
    UNKNOWN
}

enum class RouteStatus {
    ROUTED,
    NEEDS_CLARIFICATION,
    UNSUPPORTED,
    LOW_CONFIDENCE,
    BLOCKED,
    ERROR
}

data class UnifiedRouteDecision(
    val selectedDomain: UnifiedDomain,
    val selectedModelName: String? = null,
    val confidence: Float = 0.0f,
    val reason: String? = null,
    val matchedSignals: List<String> = emptyList(),
    val candidateIntentType: IntentType = IntentType.UNKNOWN,
    val requiresClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val riskLevel: BrainRiskLevel = BrainRiskLevel.SAFE,
    val sourceCommand: String,
    val normalizedCommand: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UnifiedRouteResult(
    val status: RouteStatus,
    val routeDecision: UnifiedRouteDecision,
    val commandIntent: CommandIntent? = null,
    val userMessage: String? = null,
    val technicalReason: String? = null
)
