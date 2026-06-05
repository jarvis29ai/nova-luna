package com.nova.luna.grocery

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.nova.luna.util.FuzzyMatcher

data class GroceryProviderDiscovery(
    val installedProviders: List<GroceryProvider>,
    val skippedProviders: Map<GroceryProvider, String>
)

class GroceryProviderRegistry(
    private val packageManager: PackageManager
) {
    fun supportedProviders(): List<GroceryProvider> {
        return GroceryProvider.entries.toList()
    }

    fun packageName(provider: GroceryProvider): String {
        return providerSpec(provider).packageNames.first()
    }

    fun installedPackageName(provider: GroceryProvider): String? {
        val spec = providerSpec(provider)
        spec.packageNames.firstOrNull { isLaunchable(it) }?.let { return it }

        return findPackageByLabel(spec.labelAliases)
    }

    fun isInstalled(provider: GroceryProvider): Boolean {
        return installedPackageName(provider) != null
    }

    fun installedProviders(): List<GroceryProvider> {
        return discoverProviders().installedProviders
    }

    fun missingProviders(): List<GroceryProvider> {
        return discoverProviders().skippedProviders.keys.toList()
    }

    fun discoverProviders(): GroceryProviderDiscovery {
        val installed = mutableListOf<GroceryProvider>()
        val skipped = linkedMapOf<GroceryProvider, String>()

        supportedProviders().forEach { provider ->
            val launchablePackage = installedPackageName(provider)
            if (launchablePackage != null) {
                installed.add(provider)
            } else {
                skipped[provider] = "app is not installed"
            }
        }

        GroceryLogger.d(
            "provider_discovery",
            mapOf(
                "installed" to installed.joinToString(separator = ",") { it.name },
                "skipped" to skipped.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
            )
        )

        return GroceryProviderDiscovery(
            installedProviders = installed,
            skippedProviders = skipped
        )
    }

    private fun providerSpec(provider: GroceryProvider): GroceryProviderSpec {
        return PROVIDER_SPECS.getValue(provider)
    }

    private fun isLaunchable(packageName: String): Boolean {
        return runCatching {
            packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
    }

    private fun findPackageByLabel(labelAliases: List<String>): String? {
        val installedApps = runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        val bestMatch = installedApps.firstOrNull { appInfo ->
            val label = runCatching {
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrDefault("")
            labelAliases.any { alias ->
                FuzzyMatcher.similarity(label, alias) >= 85 ||
                    FuzzyMatcher.normalize(label).contains(FuzzyMatcher.normalize(alias))
            }
        }

        return bestMatch?.packageName?.takeIf { isLaunchable(it) }
    }

    private data class GroceryProviderSpec(
        val packageNames: List<String>,
        val labelAliases: List<String>
    )

    companion object {
        const val BLINKIT_PACKAGE_NAME = "com.grofers.customerapp"
        const val JIOMART_PACKAGE_NAME = "com.jpl.jiomart"
        const val INSTAMART_PACKAGE_NAME = "in.swiggy.android.instamart"
        const val SWIGGY_PACKAGE_NAME = "in.swiggy.android"

        private val PROVIDER_SPECS = mapOf(
            GroceryProvider.BLINKIT to GroceryProviderSpec(
                packageNames = listOf(BLINKIT_PACKAGE_NAME),
                labelAliases = listOf("blinkit", "grofers")
            ),
            GroceryProvider.JIOMART to GroceryProviderSpec(
                packageNames = listOf(JIOMART_PACKAGE_NAME),
                labelAliases = listOf("jiomart", "jio mart", "jio mart online shopping app")
            ),
            GroceryProvider.INSTAMART to GroceryProviderSpec(
                packageNames = listOf(INSTAMART_PACKAGE_NAME, SWIGGY_PACKAGE_NAME),
                labelAliases = listOf("instamart", "swiggy instamart", "swiggy")
            )
        )
    }
}
