package com.nova.luna.executor

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService

class TapExecutor {
    fun tap(commandIntent: CommandIntent): CommandResult {
        val query = commandIntent.entities["text"].orEmpty()
        if (query.isBlank()) {
            return CommandResult.failure(
                "No tap target was provided.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }

        val success = NovaAccessibilityService.instance?.clickByTextOrDescription(query) == true
        return if (success) {
            CommandResult.success(
                message = "Tapped $query.",
                intentType = commandIntent.intentType,
                actionType = ActionType.CLICK_TEXT,
                entities = commandIntent.entities
            )
        } else {
            CommandResult.failure(
                "Could not find a tappable node for \"$query\".",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }
    }
}

