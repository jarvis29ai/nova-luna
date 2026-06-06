package com.nova.luna.media

import android.content.Context
import android.content.Intent
import android.net.Uri

class MediaAppLauncher(private val context: Context) {
    fun launch(provider: MediaProvider, searchQuery: String? = null): LaunchResult {
        if (provider == MediaProvider.UNKNOWN_APP) return LaunchResult.FAILED_MISSING_APP

        val deepLinkBuilder = MediaDeepLinkBuilder()
        val deepLink = deepLinkBuilder.build(provider, searchQuery)
        
        if (deepLink != null) {
            val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                provider.packageNames.firstOrNull()?.let { `package` = it }
            }
            if (canHandleIntent(intent)) {
                context.startActivity(intent)
                return LaunchResult.SUCCESS
            }
        }

        // Fallback to basic launcher intent
        provider.packageNames.forEach { pkg ->
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return LaunchResult.SUCCESS
            }
        }

        return LaunchResult.FAILED_MISSING_APP
    }

    private fun canHandleIntent(intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }
}

enum class LaunchResult {
    SUCCESS,
    FAILED_MISSING_APP,
    FAILED_FOREGROUND_ERROR,
    MANUAL_ACTION_REQUIRED
}
