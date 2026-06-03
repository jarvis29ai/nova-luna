package com.nova.luna.cab

import android.content.Context
import android.content.Intent
import android.net.Uri

open class CabDeepLinkBuilder(
    private val context: Context,
    private val providerRegistry: CabProviderRegistry
) {
    open fun buildLaunchIntent(
        provider: CabProvider,
        request: CabBookingRequest,
        selectedOption: CabFareOption? = null
    ): Intent? {
        val packageName = providerRegistry.packageName(provider)
        val deepLinkIntent = when (provider) {
            CabProvider.UBER -> buildUberDeepLinkIntent(packageName, request, selectedOption)
            else -> null
        }

        if (deepLinkIntent != null) {
            return deepLinkIntent
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return launchIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putTripExtras(provider, request, selectedOption)
        }
    }

    private fun buildUberDeepLinkIntent(
        packageName: String,
        request: CabBookingRequest,
        selectedOption: CabFareOption?
    ): Intent? {
        val dropLocation = request.dropLocation?.takeIf { it.isNotBlank() } ?: return null
        val uri = buildString {
            append("uber://?action=setPickup")
            if (request.pickupLatitude != null && request.pickupLongitude != null) {
                append("&pickup%5Blatitude%5D=")
                append(request.pickupLatitude)
                append("&pickup%5Blongitude%5D=")
                append(request.pickupLongitude)
            } else {
                val pickupLocation = request.pickupLocation?.takeIf { it.isNotBlank() } ?: "my_location"
                append("&pickup=")
                append(Uri.encode(pickupLocation))
            }
            append("&dropoff%5Bformatted_address%5D=")
            append(Uri.encode(dropLocation))
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putTripExtras(CabProvider.UBER, request, selectedOption)
        }
    }

    private fun Intent.putTripExtras(
        provider: CabProvider,
        request: CabBookingRequest,
        selectedOption: CabFareOption?
    ) {
        putExtra(EXTRA_PROVIDER, provider.name)
        putExtra(EXTRA_REQUEST_TEXT, request.rawText)
        request.pickupLocation?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_PICKUP_LOCATION, it) }
        request.pickupLatitude?.let { putExtra(EXTRA_PICKUP_LATITUDE, it) }
        request.pickupLongitude?.let { putExtra(EXTRA_PICKUP_LONGITUDE, it) }
        request.dropLocation?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_DROP_LOCATION, it) }
        request.rideType?.let { putExtra(EXTRA_RIDE_TYPE, it.name) }
        request.preferredProvider?.let { putExtra(EXTRA_PREFERRED_PROVIDER, it.name) }
        selectedOption?.let { option ->
            putExtra(EXTRA_SELECTED_PROVIDER, option.provider.name)
            putExtra(EXTRA_SELECTED_RIDE_TYPE, option.rideType.name)
            option.visibleFareText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_VISIBLE_FARE_TEXT, it) }
            option.finalFareText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_FINAL_FARE_TEXT, it) }
            option.visibleFareAmount?.let { putExtra(EXTRA_VISIBLE_FARE_AMOUNT, it) }
            option.finalFareAmount?.let { putExtra(EXTRA_FINAL_FARE_AMOUNT, it) }
            option.etaText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_ETA_TEXT, it) }
            option.etaMinutes?.let { putExtra(EXTRA_ETA_MINUTES, it) }
            option.couponText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COUPON_TEXT, it) }
            option.discountText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_DISCOUNT_TEXT, it) }
            option.packageName?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
        }
    }

    companion object {
        const val EXTRA_PROVIDER = "com.nova.luna.cab.extra.PROVIDER"
        const val EXTRA_SELECTED_PROVIDER = "com.nova.luna.cab.extra.SELECTED_PROVIDER"
        const val EXTRA_SELECTED_RIDE_TYPE = "com.nova.luna.cab.extra.SELECTED_RIDE_TYPE"
        const val EXTRA_REQUEST_TEXT = "com.nova.luna.cab.extra.REQUEST_TEXT"
        const val EXTRA_PICKUP_LOCATION = "com.nova.luna.cab.extra.PICKUP_LOCATION"
        const val EXTRA_PICKUP_LATITUDE = "com.nova.luna.cab.extra.PICKUP_LATITUDE"
        const val EXTRA_PICKUP_LONGITUDE = "com.nova.luna.cab.extra.PICKUP_LONGITUDE"
        const val EXTRA_DROP_LOCATION = "com.nova.luna.cab.extra.DROP_LOCATION"
        const val EXTRA_RIDE_TYPE = "com.nova.luna.cab.extra.RIDE_TYPE"
        const val EXTRA_PREFERRED_PROVIDER = "com.nova.luna.cab.extra.PREFERRED_PROVIDER"
        const val EXTRA_VISIBLE_FARE_TEXT = "com.nova.luna.cab.extra.VISIBLE_FARE_TEXT"
        const val EXTRA_VISIBLE_FARE_AMOUNT = "com.nova.luna.cab.extra.VISIBLE_FARE_AMOUNT"
        const val EXTRA_FINAL_FARE_TEXT = "com.nova.luna.cab.extra.FINAL_FARE_TEXT"
        const val EXTRA_FINAL_FARE_AMOUNT = "com.nova.luna.cab.extra.FINAL_FARE_AMOUNT"
        const val EXTRA_ETA_TEXT = "com.nova.luna.cab.extra.ETA_TEXT"
        const val EXTRA_ETA_MINUTES = "com.nova.luna.cab.extra.ETA_MINUTES"
        const val EXTRA_COUPON_TEXT = "com.nova.luna.cab.extra.COUPON_TEXT"
        const val EXTRA_DISCOUNT_TEXT = "com.nova.luna.cab.extra.DISCOUNT_TEXT"
        const val EXTRA_PACKAGE_NAME = "com.nova.luna.cab.extra.PACKAGE_NAME"
    }
}
