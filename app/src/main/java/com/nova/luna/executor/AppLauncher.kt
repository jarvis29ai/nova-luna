package com.nova.luna.executor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.util.FuzzyMatcher

class AppLauncher(private val context: Context) {
    data class AppEntry(
        val label: String,
        val packageName: String
    )

    private val packageManager = context.packageManager
    @Volatile
    private var cachedApps: List<AppEntry> = emptyList()

    fun refreshInstalledApps(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        cachedApps = resolveInfos
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                if (label.isBlank() || activityInfo.packageName.isBlank()) return@mapNotNull null
                AppEntry(
                    label = label,
                    packageName = activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        return cachedApps
    }

    fun findBestMatch(query: String): AppEntry? {
        val apps = if (cachedApps.isEmpty()) refreshInstalledApps() else cachedApps
        return FuzzyMatcher.bestMatch(query, apps, selector = { "${it.label} ${it.packageName}" })
    }

    fun launchApp(commandIntent: CommandIntent): CommandResult {
        val query = commandIntent.entities["resolvedLabel"]
            ?: commandIntent.entities["appName"]
            ?: commandIntent.entities["query"]
            ?: commandIntent.rawText

        val match = findBestMatch(query)
        if (match == null) {
            return CommandResult.failure(
                message = "I could not find an installed app that matches \"$query\".",
                status = ActionResultStatus.NOT_FOUND,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(match.packageName)
        if (launchIntent == null) {
            return CommandResult.failure(
                message = "Found ${match.label}, but it cannot be launched from a normal launcher intent.",
                status = ActionResultStatus.UNSUPPORTED,
                intentType = IntentType.OPEN_APP,
                actionType = ActionType.LAUNCH_APP,
                entities = commandIntent.entities
            )
        }

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            CommandResult.success(
                message = "Opening ${match.label}.",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities + mapOf(
                    "resolvedPackage" to match.packageName,
                    "resolvedLabel" to match.label
                )
            )
        } catch (throwable: Throwable) {
            CommandResult.failure(
                message = "Could not open ${match.label}: ${throwable.localizedMessage ?: "unknown error"}.",
                status = ActionResultStatus.FAILED,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }
    }
}

