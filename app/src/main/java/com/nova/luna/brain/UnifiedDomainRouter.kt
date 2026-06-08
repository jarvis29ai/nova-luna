package com.nova.luna.brain

import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.util.AssistantTextNormalizer

class UnifiedDomainRouter(
    private val handlers: List<DomainHandler>
) {
    fun route(rawText: String, context: AssistantContext? = null): UnifiedRouteResult {
        val normalized = AssistantTextNormalizer.normalize(rawText)
        
        // 1. Ask each handler for its match confidence
        val matches = handlers.map { it.canHandle(normalized, context) }
        
        // 2. Find best match
        var bestMatch = matches.maxByOrNull { it.confidence }
            ?: DomainMatch(UnifiedDomain.UNKNOWN, 0.0f, reason = "No handlers matched")
            
        // 3. Session prioritize: If a domain is already active and the command is likely related
        if (context?.activeDomain != null && context.activeDomain != UnifiedDomain.UNKNOWN) {
            val activeDomainMatch = matches.find { it.domain == context.activeDomain }
            if (activeDomainMatch != null && activeDomainMatch.confidence >= 0.40f) {
                // Boost active domain confidence
                if (activeDomainMatch.confidence + 0.30f > bestMatch.confidence) {
                    bestMatch = activeDomainMatch.copy(confidence = (activeDomainMatch.confidence + 0.30f).coerceAtMost(1.0f))
                }
            }
        }

        val decision = UnifiedRouteDecision(
            selectedDomain = bestMatch.domain,
            selectedModelName = handlers.find { it.domain == bestMatch.domain }?.modelName,
            confidence = bestMatch.confidence,
            matchedSignals = bestMatch.matchedSignals,
            requiresClarification = bestMatch.needsClarification,
            clarificationQuestion = bestMatch.clarificationQuestion,
            sourceCommand = rawText,
            normalizedCommand = normalized,
            reason = bestMatch.reason
        )

        // 3. Handle confidence thresholds
        return when {
            decision.confidence >= 0.70f -> {
                val handler = handlers.find { it.domain == decision.selectedDomain }
                val commandIntent = handler?.parse(normalized, context)
                UnifiedRouteResult(
                    status = RouteStatus.ROUTED,
                    routeDecision = decision,
                    commandIntent = commandIntent
                )
            }
            decision.confidence >= 0.50f -> {
                UnifiedRouteResult(
                    status = RouteStatus.NEEDS_CLARIFICATION,
                    routeDecision = decision.copy(requiresClarification = true, clarificationQuestion = decision.clarificationQuestion ?: "Do you mean you want to use ${decision.selectedDomain.name}?"),
                    userMessage = decision.clarificationQuestion ?: "Do you mean you want to use ${decision.selectedDomain.name}?"
                )
            }
            else -> {
                UnifiedRouteResult(
                    status = RouteStatus.UNSUPPORTED,
                    routeDecision = decision.copy(selectedDomain = UnifiedDomain.UNKNOWN),
                    userMessage = "I am not sure how to help with that yet. Can you rephrase?"
                )
            }
        }
    }
}
