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
        val canonicalPackageName = providerRegistry.packageName(provider)
        val launchPackageName = if (provider == CabProvider.UBER) {
            canonicalPackageName
        } else {
            providerRegistry.installedPackageName(provider) ?: canonicalPackageName
        }

        val intent = when (provider) {
            CabProvider.UBER -> buildUberDeepLinkIntent(canonicalPackageName, request, selectedOption)
            else -> null
        } ?: run {
            val launchIntent = runCatching {
                context.packageManager.getLaunchIntentForPackage(launchPackageName)
            }.getOrNull()

            if (launchIntent == null) {
                CabLogger.w(
                    "launch_intent_unavailable",
                    mapOf(
                        "provider" to provider.name,
                        "packageName" to launchPackageName
                    )
                )
                return null
            }

            launchIntent
        }

        return runCatching {
            intent.apply {
                setPackage(launchPackageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putTripExtras(provider, request, selectedOption)
            }
        }.getOrElse { throwable ->
            CabLogger.e(
                "launch_intent_build_failed",
                mapOf(
                    "provider" to provider.name,
                    "packageName" to launchPackageName
                ),
                throwable
            )
            null
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

        return runCatching {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putTripExtras(CabProvider.UBER, request, selectedOption)
            }
        }.getOrElse { throwable ->
            CabLogger.e(
                "uber_deep_link_failed",
                mapOf("packageName" to packageName),
                throwable
            )
            null
        }
    }

    private fun Intent.putTripExtras(
        provider: CabProvider,
        request: CabBookingRequest,
        selectedOption: CabFareOption?
    ) {
        putExtra(EXTRA_PROVIDER, provider.name)
        putExtra(EXTRA_REQUEST_TEXT, request.rawText)
        request.pickupLocation?.takeIf { it.isNotBlank() }?.let {
            putExtra(EXTRA_PICKUP_LOCATION, it)
            putExtra(EXTRA_PICKUP_TEXT, it)
        }
        request.pickupLatitude?.let { putExtra(EXTRA_PICKUP_LATITUDE, it) }
        request.pickupLongitude?.let { putExtra(EXTRA_PICKUP_LONGITUDE, it) }
        request.dropLocation?.takeIf { it.isNotBlank() }?.let {
            putExtra(EXTRA_DROP_LOCATION, it)
            putExtra(EXTRA_DROP_TEXT, it)
        }
        request.rideType?.let { putExtra(EXTRA_RIDE_TYPE, it.name) }
        request.preferredProvider?.let {
            putExtra(EXTRA_PREFERRED_PROVIDER, it.name)
            putExtra(EXTRA_PROVIDER_PREFERENCE, it.name)
        }
        putExtra(EXTRA_WANTS_CHEAPEST, request.wantsCheapest)
        selectedOption?.let { option ->
            putExtra(EXTRA_SELECTED_PROVIDER, option.provider.name)
            putExtra(EXTRA_SELECTED_RIDE_TYPE, option.rideType.name)
            option.visibleFareText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_VISIBLE_FARE_TEXT, it) }
            option.visibleFareAmount?.let { putExtra(EXTRA_VISIBLE_FARE_AMOUNT, it) }
            option.originalFareAmount?.let { putExtra(EXTRA_ORIGINAL_FARE_AMOUNT, it) }
            option.finalFareText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_FINAL_FARE_TEXT, it) }
            option.finalFareAmount?.let { putExtra(EXTRA_FINAL_FARE_AMOUNT, it) }
            option.etaText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_ETA_TEXT, it) }
            option.etaMinutes?.let { putExtra(EXTRA_ETA_MINUTES, it) }
            option.couponText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COUPON_TEXT, it) }
            option.discountText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_DISCOUNT_TEXT, it) }
            option.visibleRawText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_VISIBLE_RAW_TEXT, it) }
            option.packageName?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
        }
    }

    companion object {
        const val EXTRA_PROVIDER = "com.nova.luna.cab.extra.PROVIDER"
        const val EXTRA_SELECTED_PROVIDER = "com.nova.luna.cab.extra.SELECTED_PROVIDER"
        const val EXTRA_SELECTED_RIDE_TYPE = "com.nova.luna.cab.extra.SELECTED_RIDE_TYPE"
        const val EXTRA_REQUEST_TEXT = "com.nova.luna.cab.extra.REQUEST_TEXT"
        const val EXTRA_PICKUP_LOCATION = "com.nova.luna.cab.extra.PICKUP_LOCATION"
        const val EXTRA_PICKUP_TEXT = "com.nova.luna.cab.extra.PICKUP_TEXT"
        const val EXTRA_PICKUP_LATITUDE = "com.nova.luna.cab.extra.PICKUP_LATITUDE"
        const val EXTRA_PICKUP_LONGITUDE = "com.nova.luna.cab.extra.PICKUP_LONGITUDE"
        const val EXTRA_DROP_LOCATION = "com.nova.luna.cab.extra.DROP_LOCATION"
        const val EXTRA_DROP_TEXT = "com.nova.luna.cab.extra.DROP_TEXT"
        const val EXTRA_RIDE_TYPE = "com.nova.luna.cab.extra.RIDE_TYPE"
        const val EXTRA_PREFERRED_PROVIDER = "com.nova.luna.cab.extra.PREFERRED_PROVIDER"
        const val EXTRA_PROVIDER_PREFERENCE = "com.nova.luna.cab.extra.PROVIDER_PREFERENCE"
        const val EXTRA_WANTS_CHEAPEST = "com.nova.luna.cab.extra.WANTS_CHEAPEST"
        const val EXTRA_VISIBLE_FARE_TEXT = "com.nova.luna.cab.extra.VISIBLE_FARE_TEXT"
        const val EXTRA_VISIBLE_FARE_AMOUNT = "com.nova.luna.cab.extra.VISIBLE_FARE_AMOUNT"
        const val EXTRA_ORIGINAL_FARE_AMOUNT = "com.nova.luna.cab.extra.ORIGINAL_FARE_AMOUNT"
        const val EXTRA_FINAL_FARE_TEXT = "com.nova.luna.cab.extra.FINAL_FARE_TEXT"
        const val EXTRA_FINAL_FARE_AMOUNT = "com.nova.luna.cab.extra.FINAL_FARE_AMOUNT"
        const val EXTRA_ETA_TEXT = "com.nova.luna.cab.extra.ETA_TEXT"
        const val EXTRA_ETA_MINUTES = "com.nova.luna.cab.extra.ETA_MINUTES"
        const val EXTRA_COUPON_TEXT = "com.nova.luna.cab.extra.COUPON_TEXT"
        const val EXTRA_DISCOUNT_TEXT = "com.nova.luna.cab.extra.DISCOUNT_TEXT"
        const val EXTRA_VISIBLE_RAW_TEXT = "com.nova.luna.cab.extra.VISIBLE_RAW_TEXT"
        const val EXTRA_PACKAGE_NAME = "com.nova.luna.cab.extra.PACKAGE_NAME"
    }
}
