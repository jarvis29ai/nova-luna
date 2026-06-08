package com.nova.luna.executor

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService

class TapExecutor {
    fun tap(commandIntent: CommandIntent): CommandResult {
        val query = commandIntent.entities["text"].orEmpty()
        if (query.isBlank()) {
            return CommandResult.failure(
                message = "No tap target was provided.",
                status = ActionResultStatus.FAILED,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
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
                message = "Could not find a tappable node for \"$query\".",
                status = ActionResultStatus.NOT_FOUND,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }
    }
}

