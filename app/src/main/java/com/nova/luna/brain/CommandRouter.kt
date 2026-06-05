package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate

class CommandRouter(
    private val actionExecutor: ActionExecutorGateway,
    private val safetyGate: SafetyGate = SafetyGate()
) {
    fun route(commandIntent: CommandIntent): CommandResult {
        val decision = safetyGate.evaluate(commandIntent)
        if (!decision.allowed) {
            return if (decision.requiresBiometric) {
                CommandResult.biometricRequired(
                    message = decision.message,
                    intentType = commandIntent.intentType,
                    actionType = commandIntent.actionType,
                    entities = commandIntent.entities
                )
            } else if (decision.requiresConfirmation) {
                CommandResult.confirmationRequired(
                    message = decision.message,
                    intentType = commandIntent.intentType,
                    actionType = commandIntent.actionType,
                    entities = commandIntent.entities
                )
            } else {
                CommandResult.blocked(
                    message = decision.message,
                    intentType = commandIntent.intentType,
                    actionType = commandIntent.actionType,
                    entities = commandIntent.entities
                ).copy(safetyDecision = decision)
            }
        }

        val result = actionExecutor.execute(commandIntent)
        return result.copy(safetyDecision = decision)
    }

    fun route(brainAction: BrainAction, userConfirmed: Boolean = false): CommandResult {
        val decision = safetyGate.evaluate(brainAction, userConfirmed)
        if (!decision.allowed) {
            val intentType = mapIntentType(brainAction)
            val actionType = mapActionType(brainAction)
            return if (decision.requiresConfirmation) {
                CommandResult.confirmationRequired(
                    message = decision.message,
                    intentType = intentType,
                    actionType = actionType,
                    entities = brainAction.params
                )
            } else if (decision.humanRequired || brainAction.actionType == BrainActionType.HUMAN_ONLY || brainAction.riskLevel == BrainRiskLevel.BLOCKED) {
                CommandResult.blocked(
                    message = decision.message,
                    intentType = intentType,
                    actionType = actionType,
                    entities = brainAction.params
                ).copy(safetyDecision = decision)
            } else {
                CommandResult.blocked(
                    message = decision.message,
                    intentType = intentType,
                    actionType = actionType,
                    entities = brainAction.params
                ).copy(safetyDecision = decision)
            }
        }

        if (brainAction.actionType == BrainActionType.NONE) {
            return CommandResult.success(
                message = brainAction.reply.ifBlank { "Command noted." },
                intentType = mapIntentType(brainAction),
                actionType = mapActionType(brainAction),
                entities = brainAction.params
            ).copy(safetyDecision = decision)
        }

        if (brainAction.intent == "cab_session") {
            val rawText = brainAction.params["rawText"].orEmpty()
            val result = actionExecutor.handleCabBookingText(rawText)
            return result.copy(safetyDecision = decision)
        }

        if (brainAction.intent.startsWith("food", ignoreCase = true)) {
            val rawText = brainAction.params["rawText"].orEmpty()
            val result = actionExecutor.handleFoodBookingText(rawText)
            return result.copy(safetyDecision = decision)
        }

        if (brainAction.intent == "grocery_session") {
            val rawText = brainAction.params["rawText"].orEmpty().ifBlank { brainAction.reply }
            val result = actionExecutor.handleGroceryBookingText(rawText, userConfirmed)
            return result.copy(safetyDecision = decision)
        }

        val commandIntent = mapBrainActionToCommandIntent(brainAction) ?: return CommandResult.failure(
            message = brainAction.reply.ifBlank { "I could not map that request to an executable action." },
            intentType = mapIntentType(brainAction),
            actionType = mapActionType(brainAction),
            entities = brainAction.params
        ).copy(safetyDecision = decision)

        val result = actionExecutor.execute(commandIntent)
        return result.copy(safetyDecision = decision)
    }

    fun hasActiveCabBookingSession(): Boolean {
        return actionExecutor.hasActiveCabBookingSession()
    }

    fun cancelCabBookingSession(): CommandResult {
        return actionExecutor.cancelCabBookingSession()
    }

    fun hasActiveFoodBookingSession(): Boolean {
        return actionExecutor.hasActiveFoodBookingSession()
    }

    fun cancelFoodBookingSession(): CommandResult {
        return actionExecutor.cancelFoodBookingSession()
    }

    fun hasActiveGroceryBookingSession(): Boolean {
        return actionExecutor.hasActiveGroceryBookingSession()
    }

    fun cancelGroceryBookingSession(): CommandResult {
        return actionExecutor.cancelGroceryBookingSession()
    }

    fun routeFoodConversation(rawText: String): CommandResult {
        return actionExecutor.handleFoodBookingText(rawText)
    }

    fun routeCabConversation(rawText: String): CommandResult {
        val brainAction = BrainAction(
            intent = "cab_session",
            reply = "Continuing the cab flow.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to rawText)
        )
        return route(brainAction, userConfirmed = true)
    }

    fun routeGroceryConversation(rawText: String, userConfirmed: Boolean = false): CommandResult {
        val brainAction = BrainAction(
            intent = "grocery_session",
            reply = "Continuing the grocery flow.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to rawText)
        )
        return route(brainAction, userConfirmed = userConfirmed)
    }

    private fun mapBrainActionToCommandIntent(brainAction: BrainAction): CommandIntent? {
        val rawText = brainAction.params["rawText"] ?: brainAction.reply.ifBlank { brainAction.intent }
        return when (brainAction.intent) {
            "open_app" -> {
                val appName = brainAction.params["appName"].orEmpty().takeIf { it.isNotBlank() }
                    ?: brainAction.params["query"].orEmpty().takeIf { it.isNotBlank() }
                CommandIntent(
                    rawText = rawText,
                    intentType = IntentType.OPEN_APP,
                    actionType = ActionType.LAUNCH_APP,
                    entities = if (appName.isNullOrBlank()) {
                        brainAction.params
                    } else {
                        brainAction.params + mapOf(
                            "appName" to appName,
                            "query" to appName
                        )
                    }
                )
            }

            "go_home" -> commandIntent(rawText, IntentType.NAVIGATION, ActionType.GO_HOME, "go_home", brainAction.params)
            "go_back" -> commandIntent(rawText, IntentType.NAVIGATION, ActionType.GO_BACK, "go_back", brainAction.params)
            "open_recents" -> commandIntent(rawText, IntentType.NAVIGATION, ActionType.OPEN_RECENTS, "open_recents", brainAction.params)
            "open_notifications" -> commandIntent(rawText, IntentType.NAVIGATION, ActionType.OPEN_NOTIFICATIONS, "open_notifications", brainAction.params)
            "read_notifications" -> commandIntent(rawText, IntentType.READ_NOTIFICATIONS, ActionType.READ_NOTIFICATIONS, "read_notifications", brainAction.params)
            "scroll_forward" -> commandIntent(rawText, IntentType.NAVIGATION, ActionType.SCROLL_FORWARD, "scroll_down", brainAction.params)
            "scroll_backward" -> commandIntent(rawText, IntentType.NAVIGATION, ActionType.SCROLL_BACKWARD, "scroll_up", brainAction.params)
            "stop_service" -> commandIntent(rawText, IntentType.CONTROL, ActionType.STOP_SERVICE, "stop", brainAction.params)
            "tap_text" -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.INTERACTION,
                actionType = ActionType.CLICK_TEXT,
                entities = brainAction.params + mapOf("text" to (brainAction.params["text"] ?: brainAction.params["target"].orEmpty()))
            )
            "type_text" -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.TEXT_ENTRY,
                actionType = ActionType.TYPE_TEXT,
                entities = brainAction.params + mapOf("text" to (brainAction.params["message"] ?: brainAction.params["text"].orEmpty()))
            )
            "prepare_message" -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.OPEN_APP,
                actionType = ActionType.LAUNCH_APP,
                entities = run {
                    val appName = brainAction.params["appName"].orEmpty().takeIf { it.isNotBlank() }
                        ?: brainAction.params["query"].orEmpty().takeIf { it.isNotBlank() }
                    if (appName.isNullOrBlank()) {
                        brainAction.params
                    } else {
                        brainAction.params + mapOf(
                            "appName" to appName,
                            "query" to appName
                        )
                    }
                }
            )
            "open_settings" -> commandIntent(rawText, IntentType.SENSITIVE, ActionType.OPEN_SETTINGS, "open_settings", brainAction.params)
            "open_accessibility_settings" -> commandIntent(rawText, IntentType.SENSITIVE, ActionType.OPEN_ACCESSIBILITY_SETTINGS, "open_accessibility_settings", brainAction.params)
            "open_usage_access_settings" -> commandIntent(rawText, IntentType.SENSITIVE, ActionType.OPEN_USAGE_ACCESS_SETTINGS, "open_usage_access_settings", brainAction.params)
            "cab_booking", "cab_compare" -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.CAB_BOOKING,
                actionType = ActionType.CAB_BOOKING,
                entities = normalizeCabEntities(brainAction.params)
            )
            "food_order", "food_planning", "food_session" -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.FOOD_ORDER,
                actionType = ActionType.FOOD_ORDER,
                entities = normalizeFoodEntities(brainAction.params)
            )
            "grocery_booking", "grocery_compare", "grocery_session" -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = normalizeGroceryEntities(brainAction.params)
            )
            "call_contact" -> commandIntent(rawText, IntentType.SENSITIVE, ActionType.CALL_CONTACT, "call_contact", brainAction.params)
            "take_screenshot" -> commandIntent(rawText, IntentType.SENSITIVE, ActionType.TAKE_SCREENSHOT, "take_screenshot", brainAction.params)
            "human_only" -> null
            else -> null
        }
    }

    private fun mapIntentType(brainAction: BrainAction): IntentType {
        return when (brainAction.intent) {
            "open_app" -> IntentType.OPEN_APP
            "go_home", "go_back", "open_recents", "open_notifications", "scroll_forward", "scroll_backward" -> IntentType.NAVIGATION
            "read_notifications" -> IntentType.READ_NOTIFICATIONS
            "tap_text" -> IntentType.INTERACTION
            "stop_service" -> IntentType.CONTROL
            "type_text" -> IntentType.TEXT_ENTRY
            "prepare_message" -> IntentType.OPEN_APP
            "open_settings", "open_accessibility_settings", "open_usage_access_settings", "call_contact", "take_screenshot" -> IntentType.SENSITIVE
            "cab_booking", "cab_compare", "cab_session" -> IntentType.CAB_BOOKING
            "food_order", "food_planning", "food_session" -> IntentType.FOOD_ORDER
            "grocery_booking", "grocery_compare", "grocery_session" -> IntentType.GROCERY_BOOKING
            "human_only" -> IntentType.BLOCKED
            else -> IntentType.UNKNOWN
        }
    }

    private fun mapActionType(brainAction: BrainAction): ActionType {
        return when (brainAction.intent) {
            "open_app" -> ActionType.LAUNCH_APP
            "go_home" -> ActionType.GO_HOME
            "go_back" -> ActionType.GO_BACK
            "open_recents" -> ActionType.OPEN_RECENTS
            "open_notifications" -> ActionType.OPEN_NOTIFICATIONS
            "read_notifications" -> ActionType.READ_NOTIFICATIONS
            "scroll_forward" -> ActionType.SCROLL_FORWARD
            "scroll_backward" -> ActionType.SCROLL_BACKWARD
            "tap_text" -> ActionType.CLICK_TEXT
            "stop_service" -> ActionType.STOP_SERVICE
            "type_text" -> ActionType.TYPE_TEXT
            "prepare_message" -> ActionType.LAUNCH_APP
            "open_settings" -> ActionType.OPEN_SETTINGS
            "open_accessibility_settings" -> ActionType.OPEN_ACCESSIBILITY_SETTINGS
            "open_usage_access_settings" -> ActionType.OPEN_USAGE_ACCESS_SETTINGS
            "cab_booking", "cab_compare", "cab_session" -> ActionType.CAB_BOOKING
            "food_order", "food_planning", "food_session" -> ActionType.FOOD_ORDER
            "grocery_booking", "grocery_compare", "grocery_session" -> ActionType.GROCERY_BOOKING
            "call_contact" -> ActionType.CALL_CONTACT
            "take_screenshot" -> ActionType.TAKE_SCREENSHOT
            "human_only" -> ActionType.BLOCKED
            else -> ActionType.UNKNOWN
        }
    }

    private fun commandIntent(
        rawText: String,
        intentType: IntentType,
        actionType: ActionType,
        command: String,
        entities: Map<String, String>
    ): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = intentType,
            actionType = actionType,
            entities = entities + mapOf("command" to command)
        )
    }

    private fun normalizeCabEntities(params: Map<String, String>): Map<String, String> {
        val entities = params.toMutableMap()
        entities["pickupText"] = entities["pickupText"] ?: entities["pickupLocation"].orEmpty()
        entities["dropText"] = entities["dropText"] ?: entities["dropLocation"].orEmpty()
        entities["appName"] = entities["appName"] ?: entities["query"].orEmpty()
        return entities
    }

    private fun normalizeGroceryEntities(params: Map<String, String>): Map<String, String> {
        val entities = params.toMutableMap()
        entities["rawText"] = entities["rawText"] ?: entities["text"].orEmpty()
        entities["preferredProvider"] = entities["preferredProvider"] ?: entities["providerPreference"].orEmpty()
        entities["providerPreference"] = entities["providerPreference"] ?: entities["preferredProvider"].orEmpty()
        entities["finalUserConfirmed"] = entities["finalUserConfirmed"] ?: "false"
        return entities
    }

    private fun normalizeFoodEntities(params: Map<String, String>): Map<String, String> {
        val entities = params.toMutableMap()
        entities["rawText"] = entities["rawText"] ?: entities["text"].orEmpty()
        entities["foodItem"] = entities["foodItem"] ?: entities["item"].orEmpty()
        entities["restaurantName"] = entities["restaurantName"] ?: entities["restaurant"].orEmpty()
        entities["provider"] = entities["provider"] ?: entities["preferredProvider"].orEmpty()
        return entities
    }

    private fun isGroceryBrainAction(brainAction: BrainAction): Boolean {
        return brainAction.intent.startsWith("grocery", ignoreCase = true)
    }
}
