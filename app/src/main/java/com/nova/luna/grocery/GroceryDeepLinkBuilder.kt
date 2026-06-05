package com.nova.luna.grocery

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri

data class GroceryDeepLinkResult(
    val provider: GroceryProvider,
    val intent: Intent? = null,
    val searchIntents: List<Intent> = emptyList(),
    val launched: Boolean = false,
    val supportsDirectBasketIntent: Boolean = false,
    val needsAccessibilityFill: Boolean = true,
    val failureReason: String? = null,
    val launchMode: String? = null
)

open class GroceryDeepLinkBuilder(
    private val context: Context,
    private val providerRegistry: GroceryProviderRegistry
) {
    open fun buildLaunchPlan(
        provider: GroceryProvider,
        request: GroceryBookingRequest,
        selectedCandidate: GroceryCartCandidate? = null
    ): GroceryDeepLinkResult {
        val launchPackageName = providerRegistry.installedPackageName(provider)
            ?: providerRegistry.packageName(provider)

        val launchIntent = buildProviderOpenIntent(provider, launchPackageName)
        val searchIntents = buildSearchIntents(provider, request.basket, launchPackageName)

        return GroceryDeepLinkResult(
            provider = provider,
            intent = launchIntent,
            searchIntents = searchIntents,
            launched = launchIntent != null,
            supportsDirectBasketIntent = false,
            needsAccessibilityFill = true,
            failureReason = null,
            launchMode = if (providerRegistry.isInstalled(provider)) "search" else "market"
        )
    }

    open fun buildProviderOpenIntent(
        provider: GroceryProvider,
        packageName: String? = providerRegistry.installedPackageName(provider)
    ): Intent? {
        val resolvedPackage = packageName ?: providerRegistry.packageName(provider)

        return runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(resolvedPackage)
            if (launchIntent != null) {
                launchIntent.apply {
                    setPackage(resolvedPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$resolvedPackage")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.android.vending")
                }
            }
        }.getOrNull()
    }

    open fun buildSearchIntent(
        provider: GroceryProvider,
        query: String,
        packageName: String? = providerRegistry.installedPackageName(provider)
    ): Intent? {
        val targetPackage = packageName ?: providerRegistry.packageName(provider)
        return runCatching {
            Intent(Intent.ACTION_SEARCH).apply {
                setPackage(targetPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(SearchManager.QUERY, query)
                putExtra("query", query)
                putExtra("search_query", query)
            }
        }.getOrNull()
    }

    open fun buildSearchIntents(
        provider: GroceryProvider,
        basket: GroceryBasket,
        packageName: String? = providerRegistry.installedPackageName(provider)
    ): List<Intent> {
        return basket.items.mapNotNull { item ->
            buildSearchIntent(provider, item.displayText(), packageName)
        }
    }
}
