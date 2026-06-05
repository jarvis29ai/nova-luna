package com.nova.luna.cab

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import java.util.Locale

data class CabPickupLocation(
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class LocationValue(
    val rawText: String,
    val isCurrentLocation: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val displayName: String? = null
) {
    fun displayText(): String {
        return displayName?.takeIf { it.isNotBlank() } ?: rawText
    }
}

enum class PickupMode {
    CURRENT_LOCATION,
    USER_TEXT,
    UNKNOWN
}

object CabFailureReasons {
    const val PROVIDER_NOT_OPENED = "provider_not_opened"
    const val PROVIDER_FOREGROUND_TIMEOUT = "provider_foreground_timeout"
    const val PICKUP_FIELD_NOT_FOUND = "pickup_field_not_found"
    const val DESTINATION_FIELD_NOT_FOUND = "destination_field_not_found"
    const val BLOCKED_BY_LOCATION_PERMISSION = "blocked_by_location_permission"
    const val NO_FARE_VISIBLE = "no_fare_visible"
    const val MANUAL_ACTION_REQUIRED = "manual_action_required"
    const val RIDE_TYPE_NOT_SELECTED = "ride_type_not_selected"
    const val PROVIDER_SCREEN_UNAVAILABLE = "provider_screen_unavailable"

    fun isFieldMissing(reason: String?): Boolean {
        if (reason.isNullOrBlank()) return false
        return reason.contains(PICKUP_FIELD_NOT_FOUND) || reason.contains(DESTINATION_FIELD_NOT_FOUND)
    }

    fun isProviderLaunchIssue(reason: String?): Boolean {
        if (reason.isNullOrBlank()) return false
        return reason == PROVIDER_NOT_OPENED || reason == PROVIDER_FOREGROUND_TIMEOUT
    }
}

fun CabPickupLocation.toLocationValue(
    rawText: String = label,
    isCurrentLocation: Boolean = false
): LocationValue {
    return LocationValue(
        rawText = rawText,
        isCurrentLocation = isCurrentLocation,
        latitude = latitude,
        longitude = longitude,
        displayName = label
    )
}

fun LocationValue.toCabPickupLocation(): CabPickupLocation {
    return CabPickupLocation(
        label = displayText(),
        latitude = latitude,
        longitude = longitude
    )
}

data class CabIntentParseResult(
    val rawText: String,
    val isCabBooking: Boolean,
    val pickupText: String? = null,
    val pickupMode: PickupMode = PickupMode.UNKNOWN,
    val dropText: String? = null,
    val rideType: RideType? = null,
    val providerPreference: CabProvider? = null,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false
) {
    fun pickupValue(): LocationValue? {
        val text = pickupText?.takeIf { it.isNotBlank() }
        return when (pickupMode) {
            PickupMode.CURRENT_LOCATION -> LocationValue(
                rawText = text ?: "current location",
                isCurrentLocation = true,
                displayName = text?.takeIf { it.isNotBlank() } ?: "Current location"
            )

            PickupMode.USER_TEXT -> text?.let {
                LocationValue(
                    rawText = it,
                    isCurrentLocation = false,
                    displayName = it
                )
            }

            PickupMode.UNKNOWN -> text?.let {
                val currentLocation = it.equals("current location", ignoreCase = true)
                LocationValue(
                    rawText = it,
                    isCurrentLocation = currentLocation,
                    displayName = if (currentLocation) "Current location" else it
                )
            }
        }
    }

    fun toBookingRequest(): CabBookingRequest {
        val pickup = pickupValue()
        return CabBookingRequest(
            rawText = rawText,
            pickupLocation = pickup?.displayText(),
            pickupLatitude = pickup?.latitude,
            pickupLongitude = pickup?.longitude,
            pickupMode = when {
                pickup?.isCurrentLocation == true -> PickupMode.CURRENT_LOCATION
                pickup != null -> PickupMode.USER_TEXT
                else -> PickupMode.UNKNOWN
            },
            dropLocation = dropText?.takeIf { it.isNotBlank() },
            rideType = rideType,
            preferredProvider = providerPreference,
            wantsCheapest = wantsCheapest,
            wantsFirstOne = wantsFirstOne
        )
    }

    fun toEntities(): Map<String, String> {
        return buildMap {
            put("rawText", rawText)
            put("isCabBooking", isCabBooking.toString())
            pickupText?.takeIf { it.isNotBlank() }?.let {
                put("pickupText", it)
                put("pickupLocation", it)
            }
            put("pickupMode", pickupMode.name)
            dropText?.takeIf { it.isNotBlank() }?.let {
                put("dropText", it)
                put("dropLocation", it)
            }
            rideType?.let { put("rideType", it.name) }
            providerPreference?.let {
                put("providerPreference", it.name)
                put("preferredProvider", it.name)
            }
            put("wantsCheapest", wantsCheapest.toString())
            put("wantsFirstOne", wantsFirstOne.toString())
        }
    }
}

data class CabBookingRequest(
    val rawText: String,
    val pickupLocation: String? = null,
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val pickupMode: PickupMode = PickupMode.UNKNOWN,
    val dropLocation: String? = null,
    val rideType: RideType? = null,
    val preferredProvider: CabProvider? = null,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false,
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
    val originalFareAmount: Long? = null,
    val finalFareText: String? = null,
    val finalFareAmount: Long? = null,
    val etaText: String? = null,
    val etaMinutes: Int? = null,
    val couponText: String? = null,
    val discountText: String? = null,
    val visibleRawText: String? = null,
    val packageName: String? = null
)

data class CabTripFillResult(
    val filledPickup: Boolean,
    val filledDrop: Boolean,
    val selectedRideType: Boolean,
    val canContinueToFareScreen: Boolean,
    val failureReason: String? = null,
    val warningReason: String? = null
)

data class CabFareCollectionResult(
    val fareOption: CabFareOption? = null,
    val failureReason: String? = null,
    val snapshot: CabScreenSnapshot? = null
)

data class CabBookingSession(
    val rawText: String,
    var state: CabBookingState = CabBookingState.IDLE,
    var pickup: LocationValue? = null,
    var pickupMode: PickupMode = PickupMode.UNKNOWN,
    var drop: LocationValue? = null,
    var rideType: RideType? = null,
    var providerPreference: CabProvider? = null,
    var wantsCheapest: Boolean = false,
    var wantsFirstOne: Boolean = false,
    val fareOptions: MutableList<CabFareOption> = mutableListOf(),
    var selectedFare: CabFareOption? = null,
    val skippedProviders: MutableMap<CabProvider, String> = linkedMapOf(),
    val providerFailures: MutableMap<CabProvider, String> = linkedMapOf(),
    var currentProviderIndex: Int = 0,
    var finalConfirmationAsked: Boolean = false,
    var finalUserConfirmed: Boolean = false,
    var manualActionReason: String? = null,
    var lastProviderScreenText: String? = null,
    var currentProvider: CabProvider? = null
) {
    fun toRequest(): CabBookingRequest {
        return CabBookingRequest(
            rawText = rawText,
            pickupLocation = pickup?.displayText(),
            pickupLatitude = pickup?.latitude,
            pickupLongitude = pickup?.longitude,
            pickupMode = when {
                pickupMode == PickupMode.CURRENT_LOCATION -> PickupMode.CURRENT_LOCATION
                pickup?.isCurrentLocation == true -> PickupMode.CURRENT_LOCATION
                pickup != null -> PickupMode.USER_TEXT
                else -> PickupMode.UNKNOWN
            },
            dropLocation = drop?.displayText(),
            rideType = rideType,
            preferredProvider = providerPreference,
            wantsCheapest = wantsCheapest,
            wantsFirstOne = wantsFirstOne,
            finalUserConfirmed = finalUserConfirmed
        )
    }
}

data class ProviderAttemptResult(
    val provider: CabProvider,
    val opened: Boolean,
    val filledPickup: Boolean,
    val filledDrop: Boolean,
    val selectedRideType: Boolean,
    val fareOption: CabFareOption? = null,
    val failureReason: String? = null,
    val manualActionReason: String? = null,
    val skipped: Boolean = false
)

enum class CabBookingState {
    IDLE,
    PARSING_REQUEST,
    NEED_PICKUP,
    NEED_DROP,
    NEED_RIDE_TYPE,
    CHECKING_PROVIDERS,
    OPENING_PROVIDER,
    FILLING_TRIP,
    COLLECTING_FARES,
    SHOWING_COMPARISON,
    WAITING_FOR_PLATFORM_CHOICE,
    WAITING_FOR_FINAL_CONFIRMATION,
    BOOKING,
    COMPLETED,
    CANCELLED,
    FAILED,
    MANUAL_ACTION_REQUIRED
}

data class CabBookingResult(
    val state: CabBookingState,
    val message: String,
    val request: CabBookingRequest? = null,
    val fareOptions: List<CabFareOption> = emptyList(),
    val selectedOption: CabFareOption? = null,
    val selectedFareOption: CabFareOption? = selectedOption,
    val selectedFare: CabFareOption? = selectedOption,
    val selectedProvider: CabProvider? = selectedOption?.provider,
    val availableProviders: List<CabProvider> = emptyList(),
    val skippedProviders: Map<CabProvider, String> = emptyMap(),
    val providerFailures: Map<CabProvider, String> = emptyMap(),
    val currentProviderIndex: Int = 0,
    val finalConfirmationAsked: Boolean = false,
    val manualActionRequired: Boolean = false,
    val manualActionReason: String? = null,
    val pickupBlockedReason: String? = null,
    val finalUserConfirmed: Boolean = false,
    val currentState: CabBookingState = state
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
    val preferredProvider = (entities["providerPreference"] ?: entities["preferredProvider"])?.let { value ->
        CabProvider.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
    val pickupMode = entities["pickupMode"]?.let { value ->
        PickupMode.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    } ?: PickupMode.UNKNOWN

    return CabBookingRequest(
        rawText = rawText,
        pickupLocation = (entities["pickupText"] ?: entities["pickupLocation"])?.takeIf { it.isNotBlank() }
            ?: if (pickupMode == PickupMode.CURRENT_LOCATION) "Current location" else null,
        pickupLatitude = entities["pickupLatitude"]?.toDoubleOrNull(),
        pickupLongitude = entities["pickupLongitude"]?.toDoubleOrNull(),
        pickupMode = pickupMode,
        dropLocation = (entities["dropText"] ?: entities["dropLocation"])?.takeIf { it.isNotBlank() },
        rideType = rideType,
        preferredProvider = preferredProvider,
        wantsCheapest = entities["wantsCheapest"]?.toBooleanStrictOrNull() ?: false,
        wantsFirstOne = entities["wantsFirstOne"]?.toBooleanStrictOrNull() ?: false,
        finalUserConfirmed = entities["finalUserConfirmed"]?.toBooleanStrictOrNull() ?: false
    )
}

fun CabBookingRequest.withPickupLocation(pickup: CabPickupLocation): CabBookingRequest {
    return copy(
        pickupLocation = pickup.label,
        pickupLatitude = pickup.latitude,
        pickupLongitude = pickup.longitude,
        pickupMode = if (pickup.label.equals("current location", ignoreCase = true)) {
            PickupMode.CURRENT_LOCATION
        } else {
            PickupMode.USER_TEXT
        }
    )
}

fun CabBookingRequest.withPickupLocation(pickup: LocationValue): CabBookingRequest {
    return copy(
        pickupLocation = pickup.displayText(),
        pickupLatitude = pickup.latitude,
        pickupLongitude = pickup.longitude,
        pickupMode = when {
            pickup.isCurrentLocation -> PickupMode.CURRENT_LOCATION
            pickup.rawText.isNotBlank() -> PickupMode.USER_TEXT
            else -> PickupMode.UNKNOWN
        }
    )
}

fun CabBookingRequest.toEntities(): Map<String, String> {
    return buildMap {
        put("rawText", rawText)
        pickupLocation?.takeIf { it.isNotBlank() }?.let { put("pickupLocation", it) }
        pickupLocation?.takeIf { it.isNotBlank() }?.let { put("pickupText", it) }
        pickupLatitude?.let { put("pickupLatitude", it.toString()) }
        pickupLongitude?.let { put("pickupLongitude", it.toString()) }
        put("pickupMode", pickupMode.name)
        dropLocation?.takeIf { it.isNotBlank() }?.let { put("dropLocation", it) }
        dropLocation?.takeIf { it.isNotBlank() }?.let { put("dropText", it) }
        rideType?.let { put("rideType", it.name) }
        preferredProvider?.let {
            put("preferredProvider", it.name)
            put("providerPreference", it.name)
        }
        put("wantsCheapest", wantsCheapest.toString())
        put("wantsFirstOne", wantsFirstOne.toString())
        put("finalUserConfirmed", finalUserConfirmed.toString())
    }
}

fun CabBookingResult.toEntities(): Map<String, String> {
    return buildMap {
        put("cabState", state.name)
        put("currentState", currentState.name)
        put("manualActionRequired", manualActionRequired.toString())
        put("currentProviderIndex", currentProviderIndex.toString())
        put("finalConfirmationAsked", finalConfirmationAsked.toString())
        manualActionReason?.takeIf { it.isNotBlank() }?.let { put("manualActionReason", it) }
        pickupBlockedReason?.takeIf { it.isNotBlank() }?.let { put("pickupBlockedReason", it) }
        request?.let { request ->
            putAll(request.toEntities())
        }
        selectedOption?.let { option ->
            put("selectedProvider", option.provider.name)
            put("selectedFareOption", option.provider.name)
            put("selectedOption", option.provider.name)
            put("selectedFare", option.provider.name)
            put("selectedRideType", option.rideType.name)
            option.finalFareText?.takeIf { it.isNotBlank() }?.let { put("selectedFareText", it) }
            option.finalFareAmount?.let { put("selectedFareAmount", it.toString()) }
            option.visibleFareText?.takeIf { it.isNotBlank() }?.let { put("selectedVisibleFareText", it) }
            option.visibleFareAmount?.let { put("selectedVisibleFareAmount", it.toString()) }
            option.originalFareAmount?.let { put("selectedOriginalFareAmount", it.toString()) }
            option.etaText?.takeIf { it.isNotBlank() }?.let { put("selectedEtaText", it) }
            option.etaMinutes?.let { put("selectedEtaMinutes", it.toString()) }
            option.couponText?.takeIf { it.isNotBlank() }?.let { put("selectedCouponText", it) }
            option.discountText?.takeIf { it.isNotBlank() }?.let { put("selectedDiscountText", it) }
            option.visibleRawText?.takeIf { it.isNotBlank() }?.let { put("selectedVisibleRawText", it) }
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
        if (providerFailures.isNotEmpty()) {
            put(
                "providerFailures",
                providerFailures.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
            )
        }
        selectedProvider?.let { put("selectedProviderName", it.name) }
        selectedFareOption?.let { put("selectedFareOptionName", it.provider.name) }
        selectedFare?.let { put("selectedFareName", it.provider.name) }
        put("finalUserConfirmed", finalUserConfirmed.toString())
    }
}

fun CabBookingResult.toCabCommandResult(): CommandResult {
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
