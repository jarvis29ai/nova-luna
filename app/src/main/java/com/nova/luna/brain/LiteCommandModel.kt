package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision

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
                intent = "open_app",
                reply = "Opening ${displayAppName(commandIntent)}.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.STOP_SERVICE -> brainAction(
                intent = "stop_service",
                reply = "Stopping listening.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.GO_HOME -> brainAction(
                intent = "go_home",
                reply = "Going home.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.GO_BACK -> brainAction(
                intent = "go_back",
                reply = "Going back.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.OPEN_RECENTS -> brainAction(
                intent = "open_recents",
                reply = "Opening recent apps.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.OPEN_NOTIFICATIONS -> brainAction(
                intent = "open_notifications",
                reply = "Opening notifications.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.READ_NOTIFICATIONS -> brainAction(
                intent = "read_notifications",
                reply = "Reading notifications.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.SCROLL_FORWARD -> brainAction(
                intent = "scroll_forward",
                reply = "Scrolling down.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.SCROLL_BACKWARD -> brainAction(
                intent = "scroll_backward",
                reply = "Scrolling up.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.CLICK_TEXT -> brainAction(
                intent = "tap_text",
                reply = "Tapping ${commandIntent.entities["text"].orEmpty()}.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.TYPE_TEXT -> brainAction(
                intent = "type_text",
                reply = "Typing text.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.OPEN_SETTINGS -> brainAction(
                intent = "open_settings",
                reply = "Opening settings.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> brainAction(
                intent = "open_accessibility_settings",
                reply = "Opening accessibility settings.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.OPEN_USAGE_ACCESS_SETTINGS -> brainAction(
                intent = "open_usage_access_settings",
                reply = "Opening usage access settings.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.CALL_CONTACT -> brainAction(
                intent = "call_contact",
                reply = "I can prepare the call flow, but the final call step stays manual.",
                actionType = BrainActionType.PREPARE,
                riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
                requiresConfirmation = true,
                finalActionAllowed = false,
                params = commandIntent.entities + mapOf("rawText" to request.rawText),
                nextQuestion = "Say yes if you want me to continue."
            )

            ActionType.GROCERY_BOOKING -> brainAction(
                intent = "grocery_booking",
                reply = "I can prepare the grocery flow and stop before payment.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = commandIntent.entities + mapOf("rawText" to request.rawText)
            )

            ActionType.TAKE_SCREENSHOT -> brainAction(
                intent = "take_screenshot",
                reply = "I can prepare a screenshot flow, but I need your confirmation.",
                actionType = BrainActionType.PREPARE,
                riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
                requiresConfirmation = true,
                finalActionAllowed = false,
                params = commandIntent.entities + mapOf("rawText" to request.rawText),
                nextQuestion = "Say yes if you want me to continue."
            )

            else -> null
        }

        if (candidateAction == null) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "Lite command parser did not produce a supported command candidate.",
                safetyNotes = routeDecision.safetyNotes
            )
        }

        return BrainModelResult.available(
            role = role,
            candidateAction = candidateAction,
            rawResponse = codec.encode(candidateAction),
            reason = "Lite command path produced a safe local candidate.",
            safetyNotes = routeDecision.safetyNotes + listOf(
                "LiteCommandModel handles simple offline phone actions.",
                "It must never execute phone actions directly."
            )
        )
    }

    private fun displayAppName(commandIntent: com.nova.luna.model.CommandIntent): String {
        return commandIntent.entities["resolvedLabel"]
            ?: commandIntent.entities["appName"]
            ?: commandIntent.entities["query"]
            ?: "the app"
    }

    private fun brainAction(
        intent: String,
        reply: String,
        actionType: BrainActionType,
        riskLevel: BrainRiskLevel,
        requiresConfirmation: Boolean,
        finalActionAllowed: Boolean,
        params: Map<String, String>,
        nextQuestion: String? = null
    ): BrainAction {
        return BrainAction(
            intent = intent,
            reply = reply,
            actionType = actionType,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            finalActionAllowed = finalActionAllowed,
            params = params,
            nextQuestion = nextQuestion
        )
    }
}
