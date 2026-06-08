package com.nova.luna.executor

import android.content.Context
import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.PermissionUtils

class NotificationReader(private val context: Context) {
    fun readNotifications(commandIntent: CommandIntent): CommandResult {
        if (!PermissionUtils.hasAccessibilityPermission(context)) {
            return CommandResult.failure(
                "Enable the accessibility service first so notification events can be observed.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }

        if (!PermissionUtils.hasNotificationAccess(context)) {
            return CommandResult.failure(
                "Notification access is not enabled for this app.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }

        val summary = NovaAccessibilityService.instance?.latestNotificationSummary().orEmpty()
        return if (summary.isNotBlank()) {
            CommandResult.success(
                message = "Latest notification: $summary",
                intentType = commandIntent.intentType,
                actionType = ActionType.READ_NOTIFICATIONS,
                entities = commandIntent.entities
            )
        } else {
            CommandResult.failure(
                "No recent notification text is available yet.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }
    }
}

