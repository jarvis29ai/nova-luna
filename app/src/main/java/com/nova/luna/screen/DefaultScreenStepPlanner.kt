package com.nova.luna.screen

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType

class DefaultScreenStepPlanner(
    private val elementFinder: ScreenElementFinder
) : ScreenStepPlanner {

    override fun planNextStep(action: BrainAction, snapshot: ScreenSnapshot): ScreenStepResult {
        if (snapshot.riskSignals.isNotEmpty()) {
            return ScreenStepResult(
                success = false,
                stepExecuted = ScreenStep(action = ScreenAction.HUMAN_REQUIRED, reason = "Risk signals detected: ${snapshot.riskSignals.joinToString()}", riskLevel = 10),
                reason = "Stopped due to risky screen.",
                requiresHuman = true,
                error = "RISKY_SCREEN"
            )
        }

        return when (snapshot.detectedScreenType) {
            ScreenType.PAYMENT_OR_CHECKOUT, ScreenType.LOGIN_OR_AUTH, ScreenType.OTP_SCREEN, ScreenType.CAPTCHA_SCREEN -> {
                ScreenStepResult(
                    success = false,
                    stepExecuted = ScreenStep(action = ScreenAction.HUMAN_REQUIRED, reason = "Human input required for sensitive screen.", riskLevel = 10),
                    reason = "Stopped at sensitive screen.",
                    requiresHuman = true,
                    error = "SENSITIVE_SCREEN"
                )
            }
            ScreenType.YOUTUBE_HOME, ScreenType.BROWSER_SEARCH, ScreenType.SEARCH_SCREEN -> handleSearchScreen(action, snapshot)
            ScreenType.YOUTUBE_SEARCH, ScreenType.RESULTS_SCREEN, ScreenType.CAB_FARE_RESULTS, ScreenType.FOOD_RESTAURANT_RESULTS -> {
                 ScreenStepResult(
                    success = true,
                    stepExecuted = ScreenStep(action = ScreenAction.NO_OP, reason = "Results are visible. Stopping execution."),
                    reason = "Results are visible."
                 )
            }
            ScreenType.MESSAGE_DRAFT -> handleDraftScreen(action, snapshot)
            else -> handleGenericAction(action, snapshot)
        }
    }

    private fun handleSearchScreen(action: BrainAction, snapshot: ScreenSnapshot): ScreenStepResult {
        val query = action.params["query"] ?: action.params["text"]
        if (query.isNullOrBlank()) {
             return ScreenStepResult(
                success = false,
                stepExecuted = ScreenStep(action = ScreenAction.NO_OP, reason = "No search query provided."),
                reason = "Missing query",
                error = "MISSING_QUERY"
            )
        }

        val match = elementFinder.findElement(snapshot, ElementQuery(
            targetType = ScreenElementType.SEARCH_FIELD,
            synonyms = listOf("search", "find", "lookup", "magnifier"),
            preferEditable = true,
            allowPartialMatch = true
        ))

        return if (match.element != null) {
            ScreenStepResult(
                success = true,
                stepExecuted = ScreenStep(
                    action = ScreenAction.TYPE_TEXT,
                    targetElement = match.element,
                    inputText = query,
                    reason = "Typing search query: $query"
                ),
                reason = "Found search field."
            )
        } else {
             ScreenStepResult(
                success = false,
                stepExecuted = ScreenStep(action = ScreenAction.NO_OP, reason = "Could not find search field."),
                reason = "Search field not found.",
                error = "ELEMENT_NOT_FOUND"
            )
        }
    }

    private fun handleDraftScreen(action: BrainAction, snapshot: ScreenSnapshot): ScreenStepResult {
        val body = action.params["body"] ?: action.params["message"]
        if (body.isNullOrBlank()) {
             return ScreenStepResult(
                success = true,
                stepExecuted = ScreenStep(action = ScreenAction.NO_OP, reason = "Draft is ready but no body to type."),
                reason = "Draft ready."
            )
        }
        
        val match = elementFinder.findElement(snapshot, ElementQuery(
            preferEditable = true,
            synonyms = listOf("message", "type", "text"),
            allowPartialMatch = true
        ))
        
        return if (match.element != null) {
            // We type the text but DO NOT press send.
            ScreenStepResult(
                success = true,
                stepExecuted = ScreenStep(
                    action = ScreenAction.TYPE_TEXT,
                    targetElement = match.element,
                    inputText = body,
                    reason = "Drafting message. Not sending for safety."
                ),
                reason = "Found text field to draft message."
            )
        } else {
             ScreenStepResult(
                success = false,
                stepExecuted = ScreenStep(action = ScreenAction.NO_OP, reason = "Could not find message field."),
                reason = "Message field not found.",
                error = "ELEMENT_NOT_FOUND"
            )
        }
    }

    private fun handleGenericAction(action: BrainAction, snapshot: ScreenSnapshot): ScreenStepResult {
         return ScreenStepResult(
             success = false,
             stepExecuted = ScreenStep(action = ScreenAction.NO_OP, reason = "No specific safe step known for this screen/action combination."),
             reason = "Screen not mapped to specific flow.",
             error = "SCREEN_NOT_UNDERSTOOD"
         )
    }
}
