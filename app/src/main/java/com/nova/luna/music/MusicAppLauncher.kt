package com.nova.luna.music

import android.content.Context
import android.content.Intent

/**
 * Launches music apps and handles deep links.
 */
class MusicAppLauncher(
    private val context: Context,
    private val deepLinkBuilder: MusicDeepLinkBuilder,
    private val registry: MusicProviderRegistry
) {

    fun launchProvider(provider: MusicProvider, query: String? = null): Boolean {
        val status = registry.getProviderStatus(provider) ?: return false
        if (!status.isInstalled) return false

        if (query != null) {
            val intent = deepLinkBuilder.buildSearchIntent(provider, query)
            if (intent != null && canHandleIntent(intent)) {
                context.startActivity(intent)
                return true
            }
        }

        // Fallback to launcher intent
        for (packageName in status.packageNames) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        return false
    }

    private fun canHandleIntent(intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }
}
