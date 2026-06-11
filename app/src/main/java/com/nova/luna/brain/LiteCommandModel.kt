package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.CommandIntent
import com.nova.luna.util.AssistantTextNormalizer

class LiteCommandModel(
    private val parser: RuleBasedCommandParser = RuleBasedCommandParser(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.LITE_COMMAND
    override val available: Boolean = true

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        val commandIntent = parser.parse(request.rawText)
        val candidateAction = when (commandIntent.actionType) {
            ActionType.LAUNCH_APP -> brainAction(
                request,
                intent = "open_app",
                reply = "Opening ${displayAppName(commandIntent)}.",
                actionType = BrainActionType.OPEN_APP,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = commandIntent.entities
            )

            ActionType.STOP_SERVICE -> brainAction(
                request,
                intent = "stop",
                reply = "Stopping assistant.",
                actionType = BrainActionType.SET_DEVICE_SETTING,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.GO_HOME -> brainAction(
                request,
                intent = "go_home",
                reply = "Going home.",
                actionType = BrainActionType.OPEN_APP,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.GO_BACK -> brainAction(
                request,
                intent = "go_back",
                reply = "Going back.",
                actionType = BrainActionType.OPEN_APP,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.OPEN_RECENTS -> brainAction(
                request,
                intent = "open_recents",
                reply = "Opening recent apps.",
                actionType = BrainActionType.OPEN_APP,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.OPEN_NOTIFICATIONS -> brainAction(
                request,
                intent = "open_notifications",
                reply = "Opening notifications.",
                actionType = BrainActionType.OPEN_APP,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.READ_NOTIFICATIONS -> brainAction(
                request,
                intent = "read_notifications",
                reply = "Reading notifications.",
                actionType = BrainActionType.ASK_QUESTION,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.SCROLL_FORWARD -> brainAction(
                request,
                intent = "scroll_down",
                reply = "Scrolling down.",
                actionType = BrainActionType.SET_DEVICE_SETTING,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.SCROLL_BACKWARD -> brainAction(
                request,
                intent = "scroll_up",
                reply = "Scrolling up.",
                actionType = BrainActionType.SET_DEVICE_SETTING,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.CLICK_TEXT -> brainAction(
                request,
                intent = "click",
                reply = "Clicking ${commandIntent.entities["text"]}.",
                actionType = BrainActionType.SET_DEVICE_SETTING,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = commandIntent.entities
            )

            ActionType.TYPE_TEXT -> brainAction(
                request,
                intent = "type",
                reply = "Typing text.",
                actionType = BrainActionType.SET_DEVICE_SETTING,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = commandIntent.entities
            )

            ActionType.OPEN_SETTINGS -> brainAction(
                request,
                intent = "open_settings",
                reply = "Opening settings.",
                actionType = BrainActionType.OPEN_SETTINGS,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> brainAction(
                request,
                intent = "open_accessibility_settings",
                reply = "Opening accessibility settings.",
                actionType = BrainActionType.OPEN_SETTINGS,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.OPEN_USAGE_ACCESS_SETTINGS -> brainAction(
                request,
                intent = "open_usage_settings",
                reply = "Opening usage access settings.",
                actionType = BrainActionType.OPEN_SETTINGS,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            ActionType.CALL_CONTACT -> brainAction(
                request,
                intent = "call",
                reply = "I can prepare a call, but I will stop before placing it.",
                actionType = BrainActionType.MAKE_CALL_DRAFT,
                riskLevel = BrainRiskLevel.MEDIUM,
                requiresConfirmation = true,
                params = commandIntent.entities
            )

            ActionType.GROCERY_BOOKING -> brainAction(
                request,
                intent = "grocery",
                reply = "I can help with grocery booking, but I will stop before payment.",
                actionType = BrainActionType.GROCERY_SEARCH,
                riskLevel = BrainRiskLevel.MEDIUM,
                requiresConfirmation = true,
                params = commandIntent.entities
            )

            ActionType.TAKE_SCREENSHOT -> brainAction(
                request,
                intent = "screenshot",
                reply = "Taking screenshot.",
                actionType = BrainActionType.SET_DEVICE_SETTING,
                riskLevel = BrainRiskLevel.LOW,
                requiresConfirmation = false,
                params = emptyMap()
            )

            else -> brainAction(
                request,
                intent = "unknown",
                reply = "I'm not sure how to do that offline.",
                actionType = BrainActionType.UNKNOWN,
                riskLevel = BrainRiskLevel.UNKNOWN,
                requiresConfirmation = false,
                params = emptyMap()
            )
        }

        return BrainModelResult.available(
            role = role,
            candidateAction = candidateAction,
            rawResponse = codec.encode(candidateAction),
            reason = "Deterministic command mapping.",
            safetyNotes = routeDecision.safetyNotes + listOf("Handled by lite offline rules.")
        )
    }

    private fun displayAppName(commandIntent: CommandIntent): String {
        return commandIntent.entities["resolvedLabel"]
            ?: commandIntent.entities["appName"]
            ?: commandIntent.entities["query"]
            ?: "the app"
    }

    private fun brainAction(
        request: BrainRequest,
        intent: String,
        reply: String,
        actionType: BrainActionType,
        riskLevel: BrainRiskLevel,
        requiresConfirmation: Boolean,
        params: Map<String, String>
    ): BrainAction {
        return BrainAction(
            schemaVersion = 1,
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = request.rawText,
            normalizedCommand = AssistantTextNormalizer.normalize(request.rawText),
            intent = intent,
            actionType = actionType,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            params = params,
            confidence = 1.0,
            language = "en",
            assistantReply = reply,
            reason = "Fast offline command mapping."
        )
    }
}
