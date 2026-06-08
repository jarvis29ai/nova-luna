package com.nova.luna.executor

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService

class TypeExecutor {
    fun typeText(commandIntent: CommandIntent): CommandResult {
        val text = commandIntent.entities["text"].orEmpty()
        if (text.isBlank()) {
            return CommandResult.failure(
                message = "No text was provided to type.",
                status = ActionResultStatus.FAILED,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
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
                message = "Could not type text because no editable node was ready.",
                status = ActionResultStatus.NOT_FOUND,
                intentType = commandIntent.intentType,
                actionType = ActionType.TYPE_TEXT,
                entities = commandIntent.entities
            )
        }
    }
}

