package com.nova.luna.executor

import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService

class ScrollExecutor {
    fun scrollForward(commandIntent: CommandIntent): CommandResult {
        val success = NovaAccessibilityService.instance?.scrollForward() == true
        return if (success) {
            CommandResult.success(
                message = "Scrolled down.",
                intentType = commandIntent.intentType,
                actionType = ActionType.SCROLL_FORWARD,
                entities = commandIntent.entities
            )
        } else {
            CommandResult.failure(
                message = "Could not scroll down. No scrollable node was found.",
                status = ActionResultStatus.NOT_FOUND,
                intentType = commandIntent.intentType,
                actionType = ActionType.SCROLL_FORWARD,
                entities = commandIntent.entities
            )
        }
    }

    fun scrollBackward(commandIntent: CommandIntent): CommandResult {
        val success = NovaAccessibilityService.instance?.scrollBackward() == true
        return if (success) {
            CommandResult.success(
                message = "Scrolled up.",
                intentType = commandIntent.intentType,
                actionType = ActionType.SCROLL_BACKWARD,
                entities = commandIntent.entities
            )
        } else {
            CommandResult.failure(
                message = "Could not scroll up. No scrollable node was found.",
                status = ActionResultStatus.NOT_FOUND,
                intentType = commandIntent.intentType,
                actionType = ActionType.SCROLL_BACKWARD,
                entities = commandIntent.entities
            )
        }
    }
}

