package com.nova.luna.cab

import android.content.pm.PackageManager

class CabProviderRegistry(
    private val packageManager: PackageManager
) {
    fun supportedProviders(): List<CabProvider> {
        return CabProvider.values().toList()
    }

    fun packageName(provider: CabProvider): String {
        return when (provider) {
            CabProvider.UBER -> UBER_PACKAGE_NAME
            CabProvider.OLA -> OLA_PACKAGE_NAME
            CabProvider.RAPIDO -> RAPIDO_PACKAGE_NAME
            CabProvider.INDRIVE -> INDRIVE_PACKAGE_NAME
        }
    }

    fun isInstalled(provider: CabProvider): Boolean {
        return runCatching {
            packageManager.getLaunchIntentForPackage(packageName(provider)) != null
        }.getOrDefault(false)
    }

    fun installedProviders(): List<CabProvider> {
        return supportedProviders().filter { isInstalled(it) }
    }

    fun missingProviders(): List<CabProvider> {
        return supportedProviders().filterNot { isInstalled(it) }
    }

    companion object {
        const val UBER_PACKAGE_NAME = "com.ubercab"
        const val OLA_PACKAGE_NAME = "com.olacabs.customer"
        const val RAPIDO_PACKAGE_NAME = "com.rapido.passenger"
        const val INDRIVE_PACKAGE_NAME = "sinet.startup.inDriver"
    }
}
