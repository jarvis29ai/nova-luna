package com.nova.luna.cab

import android.content.pm.PackageManager

data class CabProviderDiscovery(
    val installedProviders: List<CabProvider>,
    val skippedProviders: Map<CabProvider, String>
)

class CabProviderRegistry(
    private val packageManager: PackageManager
) {
    fun supportedProviders(): List<CabProvider> {
        return CabProvider.values().toList()
    }

    fun packageName(provider: CabProvider): String {
        return providerSpec(provider).packageNames.first()
    }

    fun installedPackageName(provider: CabProvider): String? {
        return providerSpec(provider).packageNames.firstOrNull { packageName ->
            isLaunchable(packageName)
        }
    }

    fun isInstalled(provider: CabProvider): Boolean {
        return installedPackageName(provider) != null
    }

    fun installedProviders(): List<CabProvider> {
        return discoverProviders().installedProviders
    }

    fun missingProviders(): List<CabProvider> {
        return discoverProviders().skippedProviders.keys.toList()
    }

    fun discoverProviders(): CabProviderDiscovery {
        val installed = mutableListOf<CabProvider>()
        val skipped = linkedMapOf<CabProvider, String>()

        supportedProviders().forEach { provider ->
            val launchablePackage = installedPackageName(provider)
            if (launchablePackage != null) {
                installed.add(provider)
            } else {
                skipped[provider] = "app is not installed"
            }
        }

        CabLogger.d(
            "provider_discovery",
            mapOf(
                "installed" to installed.joinToString(separator = ",") { it.name },
                "skipped" to skipped.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
            )
        )

        return CabProviderDiscovery(
            installedProviders = installed,
            skippedProviders = skipped
        )
    }

    private fun providerSpec(provider: CabProvider): CabProviderSpec {
        return PROVIDER_SPECS.getValue(provider)
    }

    private fun isLaunchable(packageName: String): Boolean {
        return runCatching {
            packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
    }

    private data class CabProviderSpec(
        val packageNames: List<String>
    )

    companion object {
        const val UBER_PACKAGE_NAME = "com.ubercab"
        const val OLA_PACKAGE_NAME = "com.olacabs.customer"
        const val RAPIDO_PACKAGE_NAME = "com.rapido.passenger"
        const val INDRIVE_PACKAGE_NAME = "sinet.startup.inDriver"
        const val INDRIVE_PACKAGE_NAME_LOWER = "sinet.startup.indriver"
        const val INDRIVE_PACKAGE_NAME_FALLBACK = "com.indriver"

        private val PROVIDER_SPECS = mapOf(
            CabProvider.UBER to CabProviderSpec(
                packageNames = listOf(UBER_PACKAGE_NAME)
            ),
            CabProvider.OLA to CabProviderSpec(
                packageNames = listOf(OLA_PACKAGE_NAME)
            ),
            CabProvider.RAPIDO to CabProviderSpec(
                packageNames = listOf(RAPIDO_PACKAGE_NAME)
            ),
            CabProvider.INDRIVE to CabProviderSpec(
                packageNames = listOf(
                    INDRIVE_PACKAGE_NAME,
                    INDRIVE_PACKAGE_NAME_LOWER,
                    INDRIVE_PACKAGE_NAME_FALLBACK
                )
            )
        )
    }
}
