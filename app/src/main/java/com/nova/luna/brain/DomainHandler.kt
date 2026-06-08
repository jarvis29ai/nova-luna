package com.nova.luna.brain

import com.nova.luna.model.CommandIntent

data class DomainMatch(
    val domain: UnifiedDomain,
    val confidence: Float,
    val matchedSignals: List<String> = emptyList(),
    val reason: String? = null,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null
)

interface DomainHandler {
    val domain: UnifiedDomain
    val modelName: String
    
    fun canHandle(command: String, context: AssistantContext? = null): DomainMatch
    fun parse(command: String, context: AssistantContext? = null): CommandIntent
}

data class AssistantContext(
    val activeDomain: UnifiedDomain? = null,
    val lastCommand: String? = null,
    val lastRouteDecision: UnifiedRouteDecision? = null,
    val currentAppPackage: String? = null,
    val currentScreenSummary: String? = null,
    val memoryContext: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
