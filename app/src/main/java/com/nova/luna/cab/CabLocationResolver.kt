package com.nova.luna.cab

import android.content.Context
import android.location.LocationManager
import com.nova.luna.util.PermissionUtils

interface CabLocationResolver {
    fun hasLocationPermission(): Boolean
    fun getCurrentLocationDisplay(): String?
    fun getCurrentLatLng(): Pair<Double, Double>?
}

fun interface CabPickupLocationResolver {
    fun resolvePickupLocation(): CabPickupLocation?
}

fun CabLocationResolver.asPickupLocationResolver(): CabPickupLocationResolver {
    return CabPickupLocationResolver {
        val location = getCurrentLatLng() ?: return@CabPickupLocationResolver null
        CabPickupLocation(
            label = getCurrentLocationDisplay() ?: "Current location",
            latitude = location.first,
            longitude = location.second
        )
    }
}

fun CabPickupLocationResolver.asLocationResolver(): CabLocationResolver {
    return object : CabLocationResolver {
        override fun hasLocationPermission(): Boolean = true

        override fun getCurrentLocationDisplay(): String? {
            return resolvePickupLocation()?.label
        }

        override fun getCurrentLatLng(): Pair<Double, Double>? {
            val pickup = resolvePickupLocation() ?: return null
            val lat = pickup.latitude ?: return null
            val lon = pickup.longitude ?: return null
            return lat to lon
        }
    }
}

class AndroidCabLocationResolver(
    private val context: Context
) : CabLocationResolver, CabPickupLocationResolver {
    override fun hasLocationPermission(): Boolean {
        return PermissionUtils.hasLocationPermission(context)
    }

    override fun getCurrentLocationDisplay(): String? {
        return getCurrentLatLng()?.let { "Current location" }
    }

    override fun getCurrentLatLng(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null

        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        val location = providers.asSequence()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .firstOrNull()
            ?: return null

        return location.latitude to location.longitude
    }

    override fun resolvePickupLocation(): CabPickupLocation? {
        val location = getCurrentLatLng() ?: return null
        return CabPickupLocation(
            label = getCurrentLocationDisplay() ?: "Current location",
            latitude = location.first,
            longitude = location.second
        )
    }
}
