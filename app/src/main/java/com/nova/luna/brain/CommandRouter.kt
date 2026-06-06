package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType

class CommandRouter(
    private val actionExecutor: ActionExecutorGateway
) {
    fun route(commandIntent: CommandIntent): CommandResult {
        return actionExecutor.execute(commandIntent)
    }

    fun route(brainAction: BrainAction): CommandResult {
        return route(brainAction.toCommandIntent())
    }

    fun hasActiveCabBookingSession(): Boolean {
        return actionExecutor.hasActiveCabBookingSession()
    }

    fun hasActiveFoodBookingSession(): Boolean {
        return actionExecutor.hasActiveFoodBookingSession()
    }

    fun hasActiveGroceryBookingSession(): Boolean {
        return actionExecutor.hasActiveGroceryBookingSession()
    }

    fun hasActivePhoneContactSession(): Boolean {
        return actionExecutor.hasActivePhoneContactSession()
    }

    fun hasActiveCommunicationSession(): Boolean {
        return actionExecutor.hasActiveCommunicationSession()
    }

    fun hasActiveContentCreationSession(): Boolean {
        return actionExecutor.hasActiveContentCreationSession()
    }

    fun hasActiveMediaSession(): Boolean {
        return actionExecutor.hasActiveMediaSession()
    }

    fun routeCabConversation(rawText: String): CommandResult {
        return actionExecutor.handleCabBookingText(rawText)
    }

    fun routeFoodConversation(rawText: String): CommandResult {
        return actionExecutor.handleFoodBookingText(rawText)
    }

    fun routeGroceryConversation(rawText: String, userConfirmed: Boolean = false): CommandResult {
        return actionExecutor.handleGroceryBookingText(rawText, userConfirmed)
    }

    fun routePhoneContactConversation(rawText: String): CommandResult {
        return actionExecutor.handlePhoneContactText(rawText, CommandIntent(rawText))
    }

    fun routeCommunicationConversation(rawText: String): CommandResult {
        return actionExecutor.handleCommunicationText(rawText, CommandIntent(rawText))
    }

    fun routeContentCreationConversation(rawText: String): CommandResult {
        return actionExecutor.handleContentCreationText(rawText, CommandIntent(rawText))
    }

    fun routeMediaConversation(rawText: String): CommandResult {
        return actionExecutor.handleMediaText(rawText, CommandIntent(rawText))
    }
}

fun BrainAction.toCommandIntent(): CommandIntent {
    val rawText = params["rawText"] ?: ""
    return CommandIntent(
        rawText = rawText,
        intentType = when (intent) {
            "cab_booking", "cab_compare", "cab_session" -> IntentType.CAB_BOOKING
            "food_order" -> IntentType.FOOD_ORDER
            "grocery_booking", "grocery_compare", "grocery_session" -> IntentType.GROCERY_BOOKING
            "open_app" -> IntentType.OPEN_APP
            "stop_service" -> IntentType.CONTROL
            "human_only" -> IntentType.BLOCKED
            "content_creation" -> IntentType.CONTENT_CREATION
            else -> IntentType.UNKNOWN
        },
        actionType = when (intent) {
            "cab_booking", "cab_compare", "cab_session" -> ActionType.CAB_BOOKING
            "food_order" -> ActionType.FOOD_ORDER
            "grocery_booking", "grocery_compare", "grocery_session" -> ActionType.GROCERY_BOOKING
            "open_app" -> ActionType.LAUNCH_APP
            "stop_service" -> ActionType.STOP_SERVICE
            "human_only" -> ActionType.BLOCKED
            "content_creation" -> ActionType.CONTENT_CREATION
            else -> ActionType.UNKNOWN
        },
        entities = params
    )
}
