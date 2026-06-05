package com.nova.luna.model

data class BrainAction(
    val intent: String,
    val reply: String,
    val actionType: BrainActionType,
    val riskLevel: BrainRiskLevel,
    val requiresConfirmation: Boolean,
    val finalActionAllowed: Boolean,
    val params: Map<String, String> = emptyMap(),
    val nextQuestion: String? = null
)
