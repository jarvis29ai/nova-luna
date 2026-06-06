package com.nova.luna.music

import android.content.pm.PackageManager

/**
 * Registry of supported music providers and their status on the device.
 */
class MusicProviderRegistry(private val packageManager: PackageManager) {

    private val providers = listOf(
        MusicProviderStatus(
            MusicProvider.SPOTIFY, "Spotify", 
            listOf("com.spotify.music"), MusicProviderType.STREAMING
        ),
        MusicProviderStatus(
            MusicProvider.YOUTUBE_MUSIC, "YouTube Music", 
            listOf("com.google.android.apps.youtube.music"), MusicProviderType.STREAMING
        ),
        MusicProviderStatus(
            MusicProvider.APPLE_MUSIC, "Apple Music", 
            listOf("com.apple.android.music"), MusicProviderType.STREAMING
        ),
        MusicProviderStatus(
            MusicProvider.JIOSAAVN, "JioSaavn", 
            listOf("com.jio.media.jiobeats"), MusicProviderType.STREAMING
        ),
        MusicProviderStatus(
            MusicProvider.WYNK_MUSIC, "Wynk Music", 
            listOf("com.bsb.hike"), MusicProviderType.STREAMING
        ),
        MusicProviderStatus(
            MusicProvider.GAANA, "Gaana", 
            listOf("com.gaana"), MusicProviderType.STREAMING
        ),
        MusicProviderStatus(
            MusicProvider.LOCAL_DEVICE_MUSIC, "Local Music", 
            listOf("com.android.music", "com.google.android.apps.photos"), MusicProviderType.LOCAL
        )
    )

    fun getProviders(): List<MusicProviderStatus> {
        return providers.map { it.copy(isInstalled = isAppInstalled(it.packageNames)) }
    }

    fun getInstalledProviders(): List<MusicProviderStatus> {
        return getProviders().filter { it.isInstalled }
    }

    fun getProviderStatus(provider: MusicProvider): MusicProviderStatus? {
        val status = providers.firstOrNull { it.provider == provider } ?: return null
        return status.copy(isInstalled = isAppInstalled(status.packageNames))
    }

    private fun isAppInstalled(packageNames: List<String>): Boolean {
        for (packageName in packageNames) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }
        return false
    }
}
