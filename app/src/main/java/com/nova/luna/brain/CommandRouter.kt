package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutor
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult

class CommandRouter(
    private val actionExecutor: ActionExecutor
) {
    fun route(commandIntent: CommandIntent): CommandResult {
        return actionExecutor.execute(commandIntent)
    }

    fun hasActiveCabBookingSession(): Boolean {
        return actionExecutor.hasActiveCabBookingSession()
    }

    fun hasActiveFoodBookingSession(): Boolean {
        return actionExecutor.hasActiveFoodBookingSession()
    }

    fun hasActivePhoneContactSession(): Boolean {
        return actionExecutor.hasActivePhoneContactSession()
    }

    fun hasActiveCommunicationSession(): Boolean {
        return actionExecutor.hasActiveCommunicationSession()
    }

    fun routeCabConversation(rawText: String): CommandResult {
        return actionExecutor.handleCabBookingText(rawText)
    }

    fun routeFoodConversation(rawText: String): CommandResult {
        return actionExecutor.handleFoodBookingText(rawText)
    }

    fun routePhoneContactConversation(rawText: String): CommandResult {
        return actionExecutor.handlePhoneContactText(rawText, CommandIntent(rawText))
    }

    fun routeCommunicationConversation(rawText: String): CommandResult {
        return actionExecutor.handleCommunicationText(rawText, CommandIntent(rawText))
    }
}
