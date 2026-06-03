package com.nova.luna.executor

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService

class NavExecutor {
    fun goHome(commandIntent: CommandIntent): CommandResult {
        return actionResult(
            commandIntent,
            ActionType.GO_HOME,
            "Going home.",
            NovaAccessibilityService.instance?.goHome() == true
        )
    }

    fun goBack(commandIntent: CommandIntent): CommandResult {
        return actionResult(
            commandIntent,
            ActionType.GO_BACK,
            "Going back.",
            NovaAccessibilityService.instance?.goBack() == true
        )
    }

    fun openRecents(commandIntent: CommandIntent): CommandResult {
        return actionResult(
            commandIntent,
            ActionType.OPEN_RECENTS,
            "Opening recent apps.",
            NovaAccessibilityService.instance?.openRecents() == true
        )
    }

    fun openNotifications(commandIntent: CommandIntent): CommandResult {
        return actionResult(
            commandIntent,
            ActionType.OPEN_NOTIFICATIONS,
            "Opening notifications.",
            NovaAccessibilityService.instance?.openNotifications() == true
        )
    }

    private fun actionResult(
        commandIntent: CommandIntent,
        actionType: ActionType,
        successMessage: String,
        success: Boolean
    ): CommandResult {
        return if (success) {
            CommandResult.success(
                message = successMessage,
                intentType = commandIntent.intentType,
                actionType = actionType,
                entities = commandIntent.entities
            )
        } else {
            CommandResult.failure(
                "Accessibility service is not ready, or the global action failed.",
                commandIntent.intentType,
                actionType,
                commandIntent.entities
            )
        }
    }
}

