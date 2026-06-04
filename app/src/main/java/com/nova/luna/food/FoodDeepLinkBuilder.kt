package com.nova.luna.food

import android.content.Context
import android.content.Intent

open class FoodDeepLinkBuilder(
    private val context: Context,
    private val providerRegistry: FoodProviderRegistry
) {
    open fun buildLaunchIntent(
        provider: FoodProvider,
        request: FoodBookingRequest,
        selectedQuote: FoodPlatformQuote? = null
    ): Intent? {
        val launchIntent = providerRegistry.resolveLaunchIntent(provider) ?: return null
        return launchIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putSearchExtras(provider, request, selectedQuote)
        }
    }

    private fun Intent.putSearchExtras(
        provider: FoodProvider,
        request: FoodBookingRequest,
        selectedQuote: FoodPlatformQuote?
    ) {
        putExtra(EXTRA_PROVIDER, provider.name)
        putExtra(EXTRA_REQUEST_TEXT, request.rawText)
        request.foodItem?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_FOOD_ITEM, it) }
        request.restaurantName?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_RESTAURANT_NAME, it) }
        request.quantity?.let { putExtra(EXTRA_QUANTITY, it) }
        request.preferredProvider?.let { putExtra(EXTRA_PREFERRED_PROVIDER, it.name) }
        if (request.requestedProviders.isNotEmpty()) {
            putExtra(
                EXTRA_REQUESTED_PROVIDERS,
                request.requestedProviders.joinToString(separator = ",") { it.name }
            )
        }
        request.deliveryLocation?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_DELIVERY_LOCATION, it) }
        request.couponPreference?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COUPON_PREFERENCE, it) }
        putExtra(EXTRA_SEARCH_QUERY, request.toSearchTarget(provider).searchQuery())

        selectedQuote?.let { quote ->
            putExtra(EXTRA_SELECTED_PROVIDER, quote.provider.name)
            putExtra(EXTRA_SELECTED_FOOD_ITEM, quote.foodItem)
            quote.restaurantName?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_RESTAURANT_NAME, it) }
            putExtra(EXTRA_SELECTED_QUANTITY, quote.quantity)
            quote.visiblePriceText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_VISIBLE_PRICE_TEXT, it) }
            quote.visiblePriceAmount?.let { putExtra(EXTRA_SELECTED_VISIBLE_PRICE_AMOUNT, it) }
            quote.finalPayableText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_FINAL_PAYABLE_TEXT, it) }
            quote.finalPayableAmount?.let { putExtra(EXTRA_SELECTED_FINAL_PAYABLE_AMOUNT, it) }
            quote.deliveryFeeText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_DELIVERY_FEE_TEXT, it) }
            quote.deliveryFeeAmount?.let { putExtra(EXTRA_SELECTED_DELIVERY_FEE_AMOUNT, it) }
            quote.taxText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_TAX_TEXT, it) }
            quote.taxAmount?.let { putExtra(EXTRA_SELECTED_TAX_AMOUNT, it) }
            quote.discountText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_DISCOUNT_TEXT, it) }
            quote.discountAmount?.let { putExtra(EXTRA_SELECTED_DISCOUNT_AMOUNT, it) }
            quote.couponText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_COUPON_TEXT, it) }
            quote.selectedCoupon?.code?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_COUPON_CODE, it) }
            quote.selectedCoupon?.savingsText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_COUPON_SAVINGS_TEXT, it) }
            quote.etaText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_ETA_TEXT, it) }
            quote.etaMinutes?.let { putExtra(EXTRA_SELECTED_ETA_MINUTES, it) }
            quote.packageName?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SELECTED_PACKAGE_NAME, it) }
        }
    }

    companion object {
        const val EXTRA_PROVIDER = "com.nova.luna.food.extra.PROVIDER"
        const val EXTRA_SELECTED_PROVIDER = "com.nova.luna.food.extra.SELECTED_PROVIDER"
        const val EXTRA_REQUEST_TEXT = "com.nova.luna.food.extra.REQUEST_TEXT"
        const val EXTRA_FOOD_ITEM = "com.nova.luna.food.extra.FOOD_ITEM"
        const val EXTRA_RESTAURANT_NAME = "com.nova.luna.food.extra.RESTAURANT_NAME"
        const val EXTRA_QUANTITY = "com.nova.luna.food.extra.QUANTITY"
        const val EXTRA_PREFERRED_PROVIDER = "com.nova.luna.food.extra.PREFERRED_PROVIDER"
        const val EXTRA_REQUESTED_PROVIDERS = "com.nova.luna.food.extra.REQUESTED_PROVIDERS"
        const val EXTRA_DELIVERY_LOCATION = "com.nova.luna.food.extra.DELIVERY_LOCATION"
        const val EXTRA_COUPON_PREFERENCE = "com.nova.luna.food.extra.COUPON_PREFERENCE"
        const val EXTRA_SEARCH_QUERY = "com.nova.luna.food.extra.SEARCH_QUERY"
        const val EXTRA_SELECTED_FOOD_ITEM = "com.nova.luna.food.extra.SELECTED_FOOD_ITEM"
        const val EXTRA_SELECTED_RESTAURANT_NAME = "com.nova.luna.food.extra.SELECTED_RESTAURANT_NAME"
        const val EXTRA_SELECTED_QUANTITY = "com.nova.luna.food.extra.SELECTED_QUANTITY"
        const val EXTRA_SELECTED_VISIBLE_PRICE_TEXT = "com.nova.luna.food.extra.SELECTED_VISIBLE_PRICE_TEXT"
        const val EXTRA_SELECTED_VISIBLE_PRICE_AMOUNT = "com.nova.luna.food.extra.SELECTED_VISIBLE_PRICE_AMOUNT"
        const val EXTRA_SELECTED_FINAL_PAYABLE_TEXT = "com.nova.luna.food.extra.SELECTED_FINAL_PAYABLE_TEXT"
        const val EXTRA_SELECTED_FINAL_PAYABLE_AMOUNT = "com.nova.luna.food.extra.SELECTED_FINAL_PAYABLE_AMOUNT"
        const val EXTRA_SELECTED_DELIVERY_FEE_TEXT = "com.nova.luna.food.extra.SELECTED_DELIVERY_FEE_TEXT"
        const val EXTRA_SELECTED_DELIVERY_FEE_AMOUNT = "com.nova.luna.food.extra.SELECTED_DELIVERY_FEE_AMOUNT"
        const val EXTRA_SELECTED_TAX_TEXT = "com.nova.luna.food.extra.SELECTED_TAX_TEXT"
        const val EXTRA_SELECTED_TAX_AMOUNT = "com.nova.luna.food.extra.SELECTED_TAX_AMOUNT"
        const val EXTRA_SELECTED_DISCOUNT_TEXT = "com.nova.luna.food.extra.SELECTED_DISCOUNT_TEXT"
        const val EXTRA_SELECTED_DISCOUNT_AMOUNT = "com.nova.luna.food.extra.SELECTED_DISCOUNT_AMOUNT"
        const val EXTRA_SELECTED_COUPON_TEXT = "com.nova.luna.food.extra.SELECTED_COUPON_TEXT"
        const val EXTRA_SELECTED_COUPON_CODE = "com.nova.luna.food.extra.SELECTED_COUPON_CODE"
        const val EXTRA_SELECTED_COUPON_SAVINGS_TEXT = "com.nova.luna.food.extra.SELECTED_COUPON_SAVINGS_TEXT"
        const val EXTRA_SELECTED_ETA_TEXT = "com.nova.luna.food.extra.SELECTED_ETA_TEXT"
        const val EXTRA_SELECTED_ETA_MINUTES = "com.nova.luna.food.extra.SELECTED_ETA_MINUTES"
        const val EXTRA_SELECTED_PACKAGE_NAME = "com.nova.luna.food.extra.SELECTED_PACKAGE_NAME"
    }
}
