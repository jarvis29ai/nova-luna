package com.nova.luna.cab

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType

data class CabPickupLocation(
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class CabBookingRequest(
    val rawText: String,
    val pickupLocation: String? = null,
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val dropLocation: String? = null,
    val rideType: RideType? = null,
    val preferredProvider: CabProvider? = null,
    val finalUserConfirmed: Boolean = false
)

enum class CabProvider {
    UBER,
    OLA,
    RAPIDO,
    INDRIVE
}

enum class RideType {
    AUTO,
    BIKE,
    MINI,
    SEDAN,
    SUV,
    ANY
}

data class CabFareOption(
    val provider: CabProvider,
    val rideType: RideType,
    val visibleFareText: String? = null,
    val visibleFareAmount: Long? = null,
    val finalFareText: String? = null,
    val finalFareAmount: Long? = null,
    val etaText: String? = null,
    val etaMinutes: Int? = null,
    val couponText: String? = null,
    val discountText: String? = null,
    val packageName: String? = null
)

enum class CabBookingState {
    IDLE,
    NEED_PICKUP,
    NEED_DROP,
    NEED_RIDE_TYPE,
    OPENING_PROVIDERS,
    COLLECTING_FARES,
    SHOWING_COMPARISON,
    WAITING_FOR_PLATFORM_CHOICE,
    WAITING_FOR_FINAL_CONFIRMATION,
    BOOKING,
    COMPLETED,
    FAILED,
    MANUAL_ACTION_REQUIRED
}

data class CabBookingResult(
    val state: CabBookingState,
    val message: String,
    val request: CabBookingRequest? = null,
    val fareOptions: List<CabFareOption> = emptyList(),
    val selectedOption: CabFareOption? = null,
    val availableProviders: List<CabProvider> = emptyList(),
    val skippedProviders: Map<CabProvider, String> = emptyMap(),
    val manualActionRequired: Boolean = false,
    val manualActionReason: String? = null,
    val finalUserConfirmed: Boolean = false
)

fun CabProvider.displayName(): String {
    return when (this) {
        CabProvider.UBER -> "Uber"
        CabProvider.OLA -> "Ola"
        CabProvider.RAPIDO -> "Rapido"
        CabProvider.INDRIVE -> "inDrive"
    }
}

fun RideType.displayName(): String {
    return when (this) {
        RideType.AUTO -> "Auto"
        RideType.BIKE -> "Bike"
        RideType.MINI -> "Mini"
        RideType.SEDAN -> "Sedan"
        RideType.SUV -> "SUV"
        RideType.ANY -> "Any"
    }
}

fun CommandIntent.toCabBookingRequest(): CabBookingRequest {
    val rideType = entities["rideType"]?.let { value ->
        RideType.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
    val preferredProvider = entities["preferredProvider"]?.let { value ->
        CabProvider.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    return CabBookingRequest(
        rawText = rawText,
        pickupLocation = entities["pickupLocation"]?.takeIf { it.isNotBlank() },
        pickupLatitude = entities["pickupLatitude"]?.toDoubleOrNull(),
        pickupLongitude = entities["pickupLongitude"]?.toDoubleOrNull(),
        dropLocation = entities["dropLocation"]?.takeIf { it.isNotBlank() },
        rideType = rideType,
        preferredProvider = preferredProvider,
        finalUserConfirmed = entities["finalUserConfirmed"]?.toBooleanStrictOrNull() ?: false
    )
}

fun CabBookingRequest.withPickupLocation(pickup: CabPickupLocation): CabBookingRequest {
    return copy(
        pickupLocation = pickup.label,
        pickupLatitude = pickup.latitude,
        pickupLongitude = pickup.longitude
    )
}

fun CabBookingRequest.toEntities(): Map<String, String> {
    return buildMap {
        put("rawText", rawText)
        pickupLocation?.takeIf { it.isNotBlank() }?.let { put("pickupLocation", it) }
        pickupLatitude?.let { put("pickupLatitude", it.toString()) }
        pickupLongitude?.let { put("pickupLongitude", it.toString()) }
        dropLocation?.takeIf { it.isNotBlank() }?.let { put("dropLocation", it) }
        rideType?.let { put("rideType", it.name) }
        preferredProvider?.let { put("preferredProvider", it.name) }
        put("finalUserConfirmed", finalUserConfirmed.toString())
    }
}

fun CabBookingResult.toEntities(): Map<String, String> {
    return buildMap {
        put("cabState", state.name)
        put("manualActionRequired", manualActionRequired.toString())
        manualActionReason?.takeIf { it.isNotBlank() }?.let { put("manualActionReason", it) }
        request?.let { request ->
            putAll(request.toEntities())
        }
        selectedOption?.let { option ->
            put("selectedProvider", option.provider.name)
            put("selectedRideType", option.rideType.name)
            option.finalFareText?.takeIf { it.isNotBlank() }?.let { put("selectedFareText", it) }
            option.finalFareAmount?.let { put("selectedFareAmount", it.toString()) }
            option.visibleFareText?.takeIf { it.isNotBlank() }?.let { put("selectedVisibleFareText", it) }
            option.visibleFareAmount?.let { put("selectedVisibleFareAmount", it.toString()) }
            option.etaText?.takeIf { it.isNotBlank() }?.let { put("selectedEtaText", it) }
            option.etaMinutes?.let { put("selectedEtaMinutes", it.toString()) }
            option.couponText?.takeIf { it.isNotBlank() }?.let { put("selectedCouponText", it) }
            option.discountText?.takeIf { it.isNotBlank() }?.let { put("selectedDiscountText", it) }
            option.packageName?.takeIf { it.isNotBlank() }?.let { put("selectedPackageName", it) }
        }
        if (fareOptions.isNotEmpty()) {
            put("fareOptions", fareOptions.joinToString(separator = ",") { it.provider.name })
        }
        if (availableProviders.isNotEmpty()) {
            put("availableProviders", availableProviders.joinToString(separator = ",") { it.name })
        }
        if (skippedProviders.isNotEmpty()) {
            put(
                "skippedProviders",
                skippedProviders.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
            )
        }
        put("finalUserConfirmed", finalUserConfirmed.toString())
    }
}

fun CabBookingResult.toCommandResult(): CommandResult {
    val entities = toEntities()
    return if (state == CabBookingState.FAILED || state == CabBookingState.MANUAL_ACTION_REQUIRED) {
        CommandResult.failure(
            message = message,
            intentType = IntentType.CAB_BOOKING,
            actionType = ActionType.CAB_BOOKING,
            entities = entities
        )
    } else {
        CommandResult.success(
            message = message,
            intentType = IntentType.CAB_BOOKING,
            actionType = ActionType.CAB_BOOKING,
            entities = entities
        )
    }
}
