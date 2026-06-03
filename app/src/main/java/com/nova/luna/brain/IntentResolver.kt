package com.nova.luna.brain

import com.nova.luna.executor.AppLauncher
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent

class IntentResolver(
    private val appLauncher: AppLauncher
) {
    fun resolve(commandIntent: CommandIntent): CommandIntent {
        if (commandIntent.actionType != ActionType.LAUNCH_APP) {
            return commandIntent
        }

        val query = commandIntent.entities["appName"]
            ?: commandIntent.entities["query"]
            ?: commandIntent.rawText

        val match = appLauncher.findBestMatch(query)
        return if (match != null) {
            commandIntent.copy(
                entities = commandIntent.entities + mapOf(
                    "resolvedPackage" to match.packageName,
                    "resolvedLabel" to match.label
                )
            )
        } else {
            commandIntent
        }
    }
}

