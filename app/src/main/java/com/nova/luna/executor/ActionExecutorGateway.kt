package com.nova.luna.executor

import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult

interface ActionExecutorGateway {
    fun execute(commandIntent: CommandIntent): CommandResult
    fun hasActiveCabBookingSession(): Boolean
    fun cancelCabBookingSession(): CommandResult
    fun handleCabBookingText(rawText: String): CommandResult
    fun hasActiveFoodBookingSession(): Boolean
    fun cancelFoodBookingSession(): CommandResult
    fun handleFoodBookingText(rawText: String): CommandResult
    fun hasActiveGroceryBookingSession(): Boolean
    fun cancelGroceryBookingSession(): CommandResult
    fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean = false): CommandResult
}
