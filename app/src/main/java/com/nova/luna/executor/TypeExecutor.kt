package com.nova.luna.executor

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService

class TypeExecutor {
    fun typeText(commandIntent: CommandIntent): CommandResult {
        val text = commandIntent.entities["text"].orEmpty()
        if (text.isBlank()) {
            return CommandResult.failure(
                "No text was provided to type.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }

        val success = NovaAccessibilityService.instance?.typeText(text) == true
        return if (success) {
            CommandResult.success(
                message = "Typed text.",
                intentType = commandIntent.intentType,
                actionType = ActionType.TYPE_TEXT,
                entities = commandIntent.entities
            )
        } else {
            CommandResult.failure(
                "Could not type text because no editable node was ready.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }
    }
}

