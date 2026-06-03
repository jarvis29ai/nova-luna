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

    fun routeCabConversation(rawText: String): CommandResult {
        return actionExecutor.handleCabBookingText(rawText)
    }
}
