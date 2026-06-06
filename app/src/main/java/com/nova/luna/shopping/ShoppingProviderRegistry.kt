package com.nova.luna.shopping

import android.content.pm.PackageManager

class ShoppingProviderRegistry(private val packageManager: PackageManager? = null) {
    private val providers = listOf(
        ProviderInfo(ShoppingProvider.AMAZON, "Amazon", "com.amazon.mShop.android.shopping"),
        ProviderInfo(ShoppingProvider.FLIPKART, "Flipkart", "com.flipkart.android"),
        ProviderInfo(ShoppingProvider.CROMA, "Croma", "com.croma.deals"),
        ProviderInfo(ShoppingProvider.RELIANCE_DIGITAL, "Reliance Digital", "com.reliance.digital"),
        ProviderInfo(ShoppingProvider.OFFICIAL_BRAND, "Official Website", null)
    )

    fun getSupportedProviders(): List<ProviderInfo> = providers

    fun isAppInstalled(packageName: String): Boolean {
        if (packageManager == null) return false
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    data class ProviderInfo(
        val provider: ShoppingProvider,
        val displayName: String,
        val packageName: String?
    )
}
