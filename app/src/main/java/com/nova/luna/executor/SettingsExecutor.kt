package com.nova.luna.executor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult

class SettingsExecutor(private val context: Context) {
    fun openSettings(commandIntent: CommandIntent): CommandResult {
        return startActivity(
            commandIntent,
            ActionType.OPEN_SETTINGS,
            Intent(Settings.ACTION_SETTINGS),
            "Opening system settings."
        )
    }

    fun openAccessibilitySettings(commandIntent: CommandIntent): CommandResult {
        return startActivity(
            commandIntent,
            ActionType.OPEN_ACCESSIBILITY_SETTINGS,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            "Opening accessibility settings."
        )
    }

    fun openUsageAccessSettings(commandIntent: CommandIntent): CommandResult {
        return startActivity(
            commandIntent,
            ActionType.OPEN_USAGE_ACCESS_SETTINGS,
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            "Opening usage access settings."
        )
    }

    private fun startActivity(
        commandIntent: CommandIntent,
        actionType: ActionType,
        intent: Intent,
        message: String
    ): CommandResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult.success(
                message = message,
                intentType = commandIntent.intentType,
                actionType = actionType,
                entities = commandIntent.entities
            )
        } catch (throwable: Throwable) {
            CommandResult.failure(
                "Could not open settings: ${throwable.localizedMessage ?: "unknown error"}.",
                commandIntent.intentType,
                actionType,
                commandIntent.entities
            )
        }
    }
}

