package com.nova.luna.media

import android.content.pm.PackageManager

class MediaProviderRegistry(private val packageManager: PackageManager) {
    fun getInstalledProviders(): List<MediaProviderStatus> {
        return MediaProvider.values()
            .filter { it != MediaProvider.UNKNOWN_APP && it != MediaProvider.OTHER }
            .map { provider ->
                val installed = provider.packageNames.any { isPackageInstalled(it) }
                MediaProviderStatus(
                    provider = provider,
                    isInstalled = installed,
                    isAvailable = installed // Simplified for now
                )
            }
    }

    fun getProviderStatus(provider: MediaProvider): MediaProviderStatus {
        if (provider == MediaProvider.UNKNOWN_APP || provider == MediaProvider.OTHER) {
            return MediaProviderStatus(provider, isInstalled = false, isAvailable = false)
        }
        val installed = provider.packageNames.any { isPackageInstalled(it) }
        return MediaProviderStatus(
            provider = provider,
            isInstalled = installed,
            isAvailable = installed
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

data class MediaProviderStatus(
    val provider: MediaProvider,
    val isInstalled: Boolean,
    val isAvailable: Boolean,
    val reason: String? = null
)
