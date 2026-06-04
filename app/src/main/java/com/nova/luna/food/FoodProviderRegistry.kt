package com.nova.luna.food

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.ComponentName
import com.nova.luna.util.FuzzyMatcher
import java.util.Locale

class FoodProviderRegistry(
    private val packageManager: PackageManager
) {
    data class LauncherApp(
        val label: String,
        val packageName: String,
        val activityName: String
    )

    private data class ProviderProfile(
        val packageNames: List<String>,
        val labelAliases: List<String>
    )

    @Volatile
    private var cachedLauncherApps: List<LauncherApp> = emptyList()

    private val profiles = mapOf(
        FoodProvider.SWIGGY to ProviderProfile(
            packageNames = listOf("in.swiggy.android"),
            labelAliases = listOf("swiggy")
        ),
        FoodProvider.ZOMATO to ProviderProfile(
            packageNames = listOf("com.application.zomato"),
            labelAliases = listOf("zomato")
        ),
        FoodProvider.TOINGS to ProviderProfile(
            packageNames = listOf("com.toings.app", "com.toings.android", "com.toings.food"),
            labelAliases = listOf("toings", "toings food", "toings delivery", "toings app")
        )
    )

    fun supportedProviders(): List<FoodProvider> {
        return FoodProvider.values().toList()
    }

    fun packageName(provider: FoodProvider): String {
        val profile = profiles[provider] ?: return provider.name.lowercase(Locale.US)
        return resolveInstalledPackageName(provider)
            ?: profile.packageNames.firstOrNull()
            ?: provider.name.lowercase(Locale.US)
    }

    fun isInstalled(provider: FoodProvider): Boolean {
        return resolveLaunchIntent(provider) != null
    }

    fun installedProviders(): List<FoodProvider> {
        return supportedProviders().filter { isInstalled(it) }
    }

    fun missingProviders(): List<FoodProvider> {
        return supportedProviders().filterNot { isInstalled(it) }
    }

    fun resolveLaunchIntent(provider: FoodProvider): Intent? {
        val profile = profiles[provider] ?: return null

        profile.packageNames.forEach { packageName ->
            runCatching {
                packageManager.getLaunchIntentForPackage(packageName)
            }.getOrNull()?.let { intent ->
                return intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        val launcherApp = findBestLauncherApp(provider) ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(launcherApp.packageName, launcherApp.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun resolveInstalledPackageName(provider: FoodProvider): String? {
        val launcherApp = findBestLauncherApp(provider)
        if (launcherApp != null) {
            return launcherApp.packageName
        }

        val profile = profiles[provider] ?: return null
        for (packageName in profile.packageNames) {
            val launchIntent = runCatching { packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
            if (launchIntent != null) {
                return packageName
            }
        }

        return null
    }

    private fun findBestLauncherApp(provider: FoodProvider): LauncherApp? {
        val apps = refreshInstalledApps()
        if (apps.isEmpty()) return null

        val profile = profiles[provider] ?: return null
        val aliases = (profile.labelAliases + profile.packageNames + listOf(provider.displayName()))
            .filter { it.isNotBlank() }

        var bestApp: LauncherApp? = null
        var bestScore = 0

        apps.forEach { app ->
            val candidateText = listOf(app.label, app.packageName, app.activityName).joinToString(separator = " ")
            val score = aliases.maxOfOrNull { alias ->
                FuzzyMatcher.similarity(alias, candidateText)
            } ?: 0

            if (score > bestScore) {
                bestScore = score
                bestApp = app
            }
        }

        return bestApp?.takeIf { bestScore >= 60 }
    }

    private fun refreshInstalledApps(): List<LauncherApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = runCatching {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }.getOrDefault(emptyList())

        cachedLauncherApps = resolveInfos
            .mapNotNull { resolveInfo -> resolveInfo.toLauncherApp(packageManager) }
            .distinctBy { it.packageName to it.activityName }
            .sortedBy { it.label.lowercase(Locale.US) }

        return cachedLauncherApps
    }

    private fun ResolveInfo.toLauncherApp(packageManager: PackageManager): LauncherApp? {
        val activityInfo = activityInfo ?: return null
        val label = loadLabel(packageManager)?.toString().orEmpty()
        val packageName = activityInfo.packageName.orEmpty()
        val activityName = activityInfo.name.orEmpty()
        if (label.isBlank() || packageName.isBlank() || activityName.isBlank()) return null
        return LauncherApp(
            label = label,
            packageName = packageName,
            activityName = activityName
        )
    }
}
