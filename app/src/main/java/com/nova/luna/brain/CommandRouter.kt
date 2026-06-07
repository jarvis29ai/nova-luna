package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
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
        if (brainAction.intent.equals("unknown", ignoreCase = true) ||
            brainAction.actionType != BrainActionType.EXTERNAL_ACTION
        ) {
            return CommandResult.failure(
                message = "No executable action was produced.",
                intentType = IntentType.UNKNOWN,
                actionType = ActionType.UNKNOWN,
                entities = brainAction.params
            )
        }

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

    fun hasActiveShoppingSession(): Boolean {
        return actionExecutor.hasActiveShoppingSession()
    }

    fun hasActiveMusicSession(): Boolean {
        return actionExecutor.hasActiveMusicSession()
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

    fun routeShoppingConversation(rawText: String): CommandResult {
        return actionExecutor.handleShoppingText(rawText, CommandIntent(rawText))
    }

    fun routeMusicConversation(rawText: String): CommandResult {
        return actionExecutor.handleMusicText(rawText, CommandIntent(rawText))
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
                "go_home", "go_back", "open_recents", "open_notifications", "scroll_forward", "scroll_backward" -> IntentType.NAVIGATION
                "tap_text" -> IntentType.INTERACTION
                "type_text" -> IntentType.TEXT_ENTRY
                "read_notifications" -> IntentType.READ_NOTIFICATIONS
                "open_settings", "open_accessibility_settings", "open_usage_access_settings", "call_contact", "take_screenshot" -> IntentType.SENSITIVE
                "stop_service" -> IntentType.CONTROL
                "human_only" -> IntentType.BLOCKED
                "prepare_message" -> IntentType.COMMUNICATION
                "content_creation" -> IntentType.CONTENT_CREATION
                "shopping", "shopping_booking", "shopping_compare", "shopping_session" -> IntentType.SHOPPING
                "media_control" -> IntentType.MEDIA_CONTROL
                "music", "music_play", "music_control", "music_session" -> IntentType.CONTROL
                else -> IntentType.UNKNOWN
            },
            actionType = when (intent) {
                "cab_booking", "cab_compare", "cab_session" -> ActionType.CAB_BOOKING
                "food_order" -> ActionType.FOOD_ORDER
                "grocery_booking", "grocery_compare", "grocery_session" -> ActionType.GROCERY_BOOKING
                "open_app" -> ActionType.LAUNCH_APP
                "go_home" -> ActionType.GO_HOME
                "go_back" -> ActionType.GO_BACK
                "open_recents" -> ActionType.OPEN_RECENTS
                "open_notifications" -> ActionType.OPEN_NOTIFICATIONS
                "scroll_forward" -> ActionType.SCROLL_FORWARD
                "scroll_backward" -> ActionType.SCROLL_BACKWARD
                "tap_text" -> ActionType.CLICK_TEXT
                "type_text" -> ActionType.TYPE_TEXT
                "read_notifications" -> ActionType.READ_NOTIFICATIONS
                "open_settings" -> ActionType.OPEN_SETTINGS
                "open_accessibility_settings" -> ActionType.OPEN_ACCESSIBILITY_SETTINGS
                "open_usage_access_settings" -> ActionType.OPEN_USAGE_ACCESS_SETTINGS
                "call_contact" -> ActionType.CALL_CONTACT
                "take_screenshot" -> ActionType.TAKE_SCREENSHOT
                "stop_service" -> ActionType.STOP_SERVICE
                "human_only" -> ActionType.BLOCKED
                "prepare_message" -> ActionType.COMMUNICATION
                "content_creation" -> ActionType.CONTENT_CREATION
                "shopping", "shopping_booking", "shopping_compare", "shopping_session" -> ActionType.SHOPPING
                "media_control" -> ActionType.MEDIA_CONTROL
                "music", "music_play", "music_control", "music_session" -> ActionType.MUSIC
                else -> ActionType.UNKNOWN
        },
        entities = params
    )
}
