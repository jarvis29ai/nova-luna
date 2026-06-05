package com.nova.luna.cab

import android.content.pm.PackageManager

data class CabProviderDiscovery(
    val installedProviders: List<CabProvider>,
    val skippedProviders: Map<CabProvider, String>,
    val providerInfo: Map<CabProvider, CabProviderInfo> = emptyMap()
)

data class CabProviderInfo(
    val provider: CabProvider,
    val providerId: String,
    val displayName: String,
    val packageNames: List<String>,
    val supportedRideTypes: List<RideType> = emptyList(),
    val supportsDeepLink: Boolean = false,
    val supportsSearch: Boolean = false,
    val remoteBookingSupported: Boolean? = null,
    val installedPackageName: String? = null,
    val installed: Boolean = false,
    val unavailableReason: String? = null
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

    fun providerInfo(provider: CabProvider): CabProviderInfo {
        val spec = providerSpec(provider)
        val installedPackage = installedPackageName(provider)
        return CabProviderInfo(
            provider = provider,
            providerId = spec.providerId,
            displayName = provider.displayName(),
            packageNames = spec.packageNames,
            supportedRideTypes = spec.supportedRideTypes,
            supportsDeepLink = spec.supportsDeepLink,
            supportsSearch = spec.supportsSearch,
            remoteBookingSupported = spec.remoteBookingSupported,
            installedPackageName = installedPackage,
            installed = installedPackage != null,
            unavailableReason = installedPackage?.let { null } ?: "app is not installed"
        )
    }

    fun providerInfos(): List<CabProviderInfo> {
        return supportedProviders().map { providerInfo(it) }
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
            skippedProviders = skipped,
            providerInfo = supportedProviders().associateWith { providerInfo(it) }
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
        val providerId: String,
        val packageNames: List<String>,
        val supportedRideTypes: List<RideType> = emptyList(),
        val supportsDeepLink: Boolean = false,
        val supportsSearch: Boolean = false,
        val remoteBookingSupported: Boolean? = null
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
                providerId = "uber",
                packageNames = listOf(UBER_PACKAGE_NAME),
                supportedRideTypes = listOf(RideType.BIKE, RideType.AUTO, RideType.MINI, RideType.SEDAN, RideType.SUV, RideType.ANY),
                supportsDeepLink = true,
                supportsSearch = true,
                remoteBookingSupported = true
            ),
            CabProvider.OLA to CabProviderSpec(
                providerId = "ola",
                packageNames = listOf(OLA_PACKAGE_NAME),
                supportedRideTypes = listOf(RideType.AUTO, RideType.BIKE, RideType.MINI, RideType.SEDAN, RideType.SUV, RideType.ANY),
                supportsDeepLink = true,
                supportsSearch = true,
                remoteBookingSupported = true
            ),
            CabProvider.RAPIDO to CabProviderSpec(
                providerId = "rapido",
                packageNames = listOf(RAPIDO_PACKAGE_NAME),
                supportedRideTypes = listOf(RideType.BIKE, RideType.AUTO, RideType.MINI, RideType.ANY),
                supportsDeepLink = false,
                supportsSearch = true,
                remoteBookingSupported = false
            ),
            CabProvider.INDRIVE to CabProviderSpec(
                providerId = "indrive",
                packageNames = listOf(
                    INDRIVE_PACKAGE_NAME,
                    INDRIVE_PACKAGE_NAME_LOWER,
                    INDRIVE_PACKAGE_NAME_FALLBACK
                ),
                supportedRideTypes = listOf(RideType.AUTO, RideType.MINI, RideType.SEDAN, RideType.SUV, RideType.ANY),
                supportsDeepLink = false,
                supportsSearch = true,
                remoteBookingSupported = null
            )
        )
    }
}
