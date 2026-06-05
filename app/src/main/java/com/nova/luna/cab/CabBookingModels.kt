package com.nova.luna.cab

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.util.AccessibilityReadiness
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

enum class CabRideTime {
    NOW,
    SCHEDULED
}

enum class CabPassengerMode {
    SELF,
    SOMEONE_ELSE
}

enum class CabRidePreference {
    CHEAPEST,
    FASTEST,
    COMFORTABLE,
    PROVIDER_SPECIFIC,
    UNKNOWN
}

enum class CabLaunchStatus {
    SUCCESS,
    APP_MISSING,
    APP_FAILED_TO_FOREGROUND,
    UNSUPPORTED_DEEP_LINK,
    MANUAL_ACTION_REQUIRED,
    UNKNOWN
}

data class CabRankingReason(
    val code: String,
    val message: String,
    val scoreDelta: Int = 0
)

data class CabRequirementProfile(
    val pickupMode: PickupMode = PickupMode.UNKNOWN,
    val pickupText: String? = null,
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val destinationText: String? = null,
    val cabType: RideType? = null,
    val rideTime: CabRideTime = CabRideTime.NOW,
    val scheduledTimeText: String? = null,
    val preference: CabRidePreference? = null,
    val preferredProvider: CabProvider? = null,
    val passengerMode: CabPassengerMode = CabPassengerMode.SELF,
    val remotePassengerName: String? = null,
    val remotePassengerPhone: String? = null,
    val allowComparison: Boolean = true,
    val requiresFinalConfirmation: Boolean = true,
    val safetyNotes: List<String> = emptyList(),
    val compareOnly: Boolean = false,
    val manualPickupRequested: Boolean = false
)

data class CabProviderResult(
    val provider: CabProvider,
    val displayName: String = provider.displayName(),
    val packageName: String? = null,
    val installed: Boolean = false,
    val unavailableReason: String? = null,
    val supportsDeepLink: Boolean = false,
    val supportsSearch: Boolean = false,
    val supportedRideTypes: List<RideType> = emptyList(),
    val remoteBookingSupported: Boolean? = null,
    val launchStatus: CabLaunchStatus = CabLaunchStatus.UNKNOWN,
    val failureReason: String? = null,
    val rideOption: CabFareOption? = null,
    val diagnostics: CabUiDiagnostics? = null,
    val warnings: List<String> = emptyList()
)

data class CabComparisonResult(
    val options: List<CabFareOption> = emptyList(),
    val rankedTop3: List<CabFareOption> = emptyList(),
    val recommendedOption: CabFareOption? = rankedTop3.firstOrNull(),
    val skippedProviders: Map<CabProvider, String> = emptyMap(),
    val providerFailures: Map<CabProvider, String> = emptyMap(),
    val rankingReasons: Map<CabProvider, List<CabRankingReason>> = emptyMap(),
    val userPreference: CabRidePreference? = null,
    val comparisonNotes: List<String> = emptyList()
)

data class CabBookingFinalSummary(
    val state: CabBookingState,
    val provider: CabProvider? = null,
    val pickup: String? = null,
    val destination: String? = null,
    val cabType: RideType? = null,
    val estimatedFareText: String? = null,
    val pickupEtaText: String? = null,
    val travelTimeText: String? = null,
    val paymentModeText: String? = null,
    val driverName: String? = null,
    val vehicleNumber: String? = null,
    val warnings: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val manualActionRequired: Boolean = false,
    val voiceMessage: String? = null,
    val popupMessage: String? = null
)

typealias CabRideOption = CabFareOption

object CabFailureReasons {
    const val PROVIDER_NOT_OPENED = "provider_not_opened"
    const val PROVIDER_FOREGROUND_TIMEOUT = "provider_foreground_timeout"
    const val PICKUP_FIELD_NOT_FOUND = "pickup_field_not_found"
    const val DESTINATION_FIELD_NOT_FOUND = "destination_field_not_found"
    const val DESTINATION_FIELD_INACCESSIBLE = "destination_field_inaccessible"
    const val BLOCKED_BY_LOCATION_PERMISSION = "blocked_by_location_permission"
    const val NO_FARE_VISIBLE = "no_fare_visible"
    const val MANUAL_ACTION_REQUIRED = "manual_action_required"
    const val RIDE_TYPE_NOT_SELECTED = "ride_type_not_selected"
    const val PROVIDER_SCREEN_UNAVAILABLE = "provider_screen_unavailable"

    fun isFieldMissing(reason: String?): Boolean {
        if (reason.isNullOrBlank()) return false
        return reason.contains(PICKUP_FIELD_NOT_FOUND) ||
            reason.contains(DESTINATION_FIELD_NOT_FOUND) ||
            reason.contains(DESTINATION_FIELD_INACCESSIBLE)
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
    val rideTime: CabRideTime? = null,
    val scheduledTimeText: String? = null,
    val preference: CabRidePreference? = null,
    val passengerMode: CabPassengerMode = CabPassengerMode.SELF,
    val remotePassengerName: String? = null,
    val remotePassengerPhone: String? = null,
    val allowComparison: Boolean = true,
    val requiresFinalConfirmation: Boolean = true,
    val safetyNotes: List<String> = emptyList(),
    val compareOnly: Boolean = false,
    val bookNow: Boolean = false,
    val scheduleLater: Boolean = false,
    val manualPickupRequested: Boolean = false,
    val selectionIndex: Int? = null,
    val wantsCheapest: Boolean = false,
    val wantsFastest: Boolean = false,
    val wantsComfortable: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val changePickupRequest: Boolean = false,
    val changeDropRequest: Boolean = false,
    val changeRideTypeRequest: Boolean = false,
    val tryAnotherAppRequest: Boolean = false
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
            rideTime = rideTime ?: CabRideTime.NOW,
            scheduledTimeText = scheduledTimeText?.takeIf { it.isNotBlank() },
            preference = preference,
            passengerMode = passengerMode,
            remotePassengerName = remotePassengerName?.takeIf { it.isNotBlank() },
            remotePassengerPhone = remotePassengerPhone?.takeIf { it.isNotBlank() },
            allowComparison = allowComparison,
            requiresFinalConfirmation = requiresFinalConfirmation,
            safetyNotes = safetyNotes,
            compareOnly = compareOnly,
            bookNow = bookNow,
            scheduleLater = scheduleLater,
            manualPickupRequested = manualPickupRequested,
            selectionIndex = selectionIndex,
            wantsCheapest = wantsCheapest,
            wantsFastest = wantsFastest,
            wantsComfortable = wantsComfortable,
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
            rideTime?.let { put("rideTime", it.name) }
            scheduledTimeText?.takeIf { it.isNotBlank() }?.let { put("scheduledTimeText", it) }
            preference?.let { put("preference", it.name) }
            put("passengerMode", passengerMode.name)
            remotePassengerName?.takeIf { it.isNotBlank() }?.let { put("remotePassengerName", it) }
            remotePassengerPhone?.takeIf { it.isNotBlank() }?.let { put("remotePassengerPhone", it) }
            put("allowComparison", allowComparison.toString())
            put("requiresFinalConfirmation", requiresFinalConfirmation.toString())
            if (safetyNotes.isNotEmpty()) {
                put("safetyNotes", safetyNotes.joinToString(separator = ";"))
            }
            put("compareOnly", compareOnly.toString())
            put("bookNow", bookNow.toString())
            put("scheduleLater", scheduleLater.toString())
            put("manualPickupRequested", manualPickupRequested.toString())
            selectionIndex?.let { put("selectionIndex", it.toString()) }
            put("wantsCheapest", wantsCheapest.toString())
            put("wantsFastest", wantsFastest.toString())
            put("wantsComfortable", wantsComfortable.toString())
            put("wantsFirstOne", wantsFirstOne.toString())
            put("changePickupRequest", changePickupRequest.toString())
            put("changeDropRequest", changeDropRequest.toString())
            put("changeRideTypeRequest", changeRideTypeRequest.toString())
            put("tryAnotherAppRequest", tryAnotherAppRequest.toString())
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
    val rideTime: CabRideTime = CabRideTime.NOW,
    val scheduledTimeText: String? = null,
    val preference: CabRidePreference? = null,
    val passengerMode: CabPassengerMode = CabPassengerMode.SELF,
    val remotePassengerName: String? = null,
    val remotePassengerPhone: String? = null,
    val allowComparison: Boolean = true,
    val requiresFinalConfirmation: Boolean = true,
    val safetyNotes: List<String> = emptyList(),
    val compareOnly: Boolean = false,
    val bookNow: Boolean = false,
    val scheduleLater: Boolean = false,
    val manualPickupRequested: Boolean = false,
    val selectionIndex: Int? = null,
    val wantsCheapest: Boolean = false,
    val wantsFastest: Boolean = false,
    val wantsComfortable: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val changePickupRequest: Boolean = false,
    val changeDropRequest: Boolean = false,
    val changeRideTypeRequest: Boolean = false,
    val tryAnotherAppRequest: Boolean = false,
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
    val warningReason: String? = null,
    val diagnostics: CabUiDiagnostics? = null
)

data class CabFareCollectionResult(
    val fareOption: CabFareOption? = null,
    val failureReason: String? = null,
    val snapshot: CabScreenSnapshot? = null
)

data class CabUiDiagnostics(
    val provider: CabProvider? = null,
    val reason: String? = null,
    val packageName: String? = null,
    val screenClassName: String? = null,
    val screenText: String? = null,
    val visibleLabels: List<String> = emptyList(),
    val editableLabels: List<String> = emptyList(),
    val clickableLabels: List<String> = emptyList(),
    val attemptedQueries: List<String> = emptyList(),
    val missingControlType: String? = null
) {
    fun toSummary(): String {
        val parts = buildList {
            provider?.let { add("provider=${it.name}") }
            reason?.takeIf { it.isNotBlank() }?.let { add("reason=$it") }
            packageName?.takeIf { it.isNotBlank() }?.let { add("package=$it") }
            screenClassName?.takeIf { it.isNotBlank() }?.let { add("screenClass=$it") }
            screenText?.takeIf { it.isNotBlank() }?.let { add("screen=$it") }
            if (visibleLabels.isNotEmpty()) {
                add("visible=${visibleLabels.take(8).joinToString(separator = " | ")}")
            }
            if (editableLabels.isNotEmpty()) {
                add("editable=${editableLabels.take(8).joinToString(separator = " | ")}")
            }
            if (clickableLabels.isNotEmpty()) {
                add("clickable=${clickableLabels.take(8).joinToString(separator = " | ")}")
            }
            if (attemptedQueries.isNotEmpty()) {
                add("queries=${attemptedQueries.take(8).joinToString(separator = " | ")}")
            }
            missingControlType?.takeIf { it.isNotBlank() }?.let { add("missingControl=$it") }
        }
        return parts.joinToString(separator = "; ")
    }
}

data class CabBookingSession(
    val rawText: String,
    var state: CabBookingState = CabBookingState.IDLE,
    var pickup: LocationValue? = null,
    var pickupMode: PickupMode = PickupMode.UNKNOWN,
    var drop: LocationValue? = null,
    var rideType: RideType? = null,
    var providerPreference: CabProvider? = null,
    var rideTime: CabRideTime = CabRideTime.NOW,
    var scheduledTimeText: String? = null,
    var preference: CabRidePreference? = null,
    var passengerMode: CabPassengerMode = CabPassengerMode.SELF,
    var remotePassengerName: String? = null,
    var remotePassengerPhone: String? = null,
    var allowComparison: Boolean = true,
    var requiresFinalConfirmation: Boolean = true,
    var safetyNotes: MutableList<String> = mutableListOf(),
    var compareOnly: Boolean = false,
    var bookNow: Boolean = false,
    var scheduleLater: Boolean = false,
    var manualPickupRequested: Boolean = false,
    var selectionIndex: Int? = null,
    var wantsCheapest: Boolean = false,
    var wantsFastest: Boolean = false,
    var wantsComfortable: Boolean = false,
    var wantsFirstOne: Boolean = false,
    var changePickupRequest: Boolean = false,
    var changeDropRequest: Boolean = false,
    var changeRideTypeRequest: Boolean = false,
    var tryAnotherAppRequest: Boolean = false,
    val fareOptions: MutableList<CabFareOption> = mutableListOf(),
    var selectedFare: CabFareOption? = null,
    var comparisonResult: CabComparisonResult? = null,
    var finalSummary: CabBookingFinalSummary? = null,
    var requirementProfile: CabRequirementProfile? = null,
    val skippedProviders: MutableMap<CabProvider, String> = linkedMapOf(),
    val providerFailures: MutableMap<CabProvider, String> = linkedMapOf(),
    var currentProviderIndex: Int = 0,
    var finalConfirmationAsked: Boolean = false,
    var finalUserConfirmed: Boolean = false,
    var manualActionReason: String? = null,
    var lastProviderScreenText: String? = null,
    var currentProvider: CabProvider? = null,
    val providerDiagnostics: MutableMap<CabProvider, CabUiDiagnostics> = linkedMapOf()
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
            rideTime = rideTime,
            scheduledTimeText = scheduledTimeText?.takeIf { it.isNotBlank() },
            preference = preference,
            passengerMode = passengerMode,
            remotePassengerName = remotePassengerName?.takeIf { it.isNotBlank() },
            remotePassengerPhone = remotePassengerPhone?.takeIf { it.isNotBlank() },
            allowComparison = allowComparison,
            requiresFinalConfirmation = requiresFinalConfirmation,
            safetyNotes = safetyNotes.toList(),
            compareOnly = compareOnly,
            bookNow = bookNow,
            scheduleLater = scheduleLater,
            manualPickupRequested = manualPickupRequested,
            selectionIndex = selectionIndex,
            wantsCheapest = wantsCheapest,
            wantsFastest = wantsFastest,
            wantsComfortable = wantsComfortable,
            wantsFirstOne = wantsFirstOne,
            changePickupRequest = changePickupRequest,
            changeDropRequest = changeDropRequest,
            changeRideTypeRequest = changeRideTypeRequest,
            tryAnotherAppRequest = tryAnotherAppRequest,
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
    val diagnostics: CabUiDiagnostics? = null,
    val skipped: Boolean = false
)

enum class CabBookingState {
    IDLE,
    PARSING_REQUEST,
    NEED_PICKUP,
    NEED_DROP,
    NEED_RIDE_TYPE,
    NEED_RIDE_TIME,
    NEED_PREFERENCE,
    CHECKING_PERMISSIONS,
    PERMISSION_BLOCKED,
    CHECKING_PROVIDERS,
    NO_PROVIDER_AVAILABLE,
    OPENING_PROVIDER,
    FILLING_TRIP,
    COLLECTING_FARES,
    COMPARING_OPTIONS,
    SHOWING_COMPARISON,
    WAITING_FOR_PLATFORM_CHOICE,
    REFINING_SEARCH,
    OPENING_SELECTED_PROVIDER,
    SELECTING_RIDE,
    SHOWING_FINAL_SUMMARY,
    WAITING_FOR_FINAL_CONFIRMATION,
    CHECKING_FINAL_ACTION_SAFETY,
    BOOKING,
    WAITING_FOR_BOOKING_RESPONSE,
    COMPLETED,
    CANCELLED,
    FAILED,
    MANUAL_ACTION_REQUIRED
}

typealias CabBookingStatus = CabBookingState
typealias CabFlowState = CabBookingState

data class CabBookingResult(
    val state: CabBookingState,
    val message: String,
    val request: CabBookingRequest? = null,
    val fareOptions: List<CabFareOption> = emptyList(),
    val comparisonResult: CabComparisonResult? = null,
    val finalSummary: CabBookingFinalSummary? = null,
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
    val providerDiagnostics: Map<CabProvider, CabUiDiagnostics> = emptyMap(),
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
    val rideTime = entities["rideTime"]?.let { value ->
        CabRideTime.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    } ?: CabRideTime.NOW
    val preference = entities["preference"]?.let { value ->
        CabRidePreference.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
    val passengerMode = entities["passengerMode"]?.let { value ->
        CabPassengerMode.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    } ?: CabPassengerMode.SELF
    val pickupMode = entities["pickupMode"]?.let { value ->
        PickupMode.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    } ?: PickupMode.UNKNOWN
    val selectionIndex = entities["selectionIndex"]?.toIntOrNull()
    val safetyNotes = entities["safetyNotes"]
        ?.split(";")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

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
        rideTime = rideTime,
        scheduledTimeText = entities["scheduledTimeText"]?.takeIf { it.isNotBlank() },
        preference = preference,
        passengerMode = passengerMode,
        remotePassengerName = entities["remotePassengerName"]?.takeIf { it.isNotBlank() },
        remotePassengerPhone = entities["remotePassengerPhone"]?.takeIf { it.isNotBlank() },
        allowComparison = entities["allowComparison"]?.toBooleanStrictOrNull() ?: true,
        requiresFinalConfirmation = entities["requiresFinalConfirmation"]?.toBooleanStrictOrNull() ?: true,
        safetyNotes = safetyNotes,
        compareOnly = entities["compareOnly"]?.toBooleanStrictOrNull() ?: false,
        bookNow = entities["bookNow"]?.toBooleanStrictOrNull() ?: false,
        scheduleLater = entities["scheduleLater"]?.toBooleanStrictOrNull() ?: false,
        manualPickupRequested = entities["manualPickupRequested"]?.toBooleanStrictOrNull() ?: false,
        selectionIndex = selectionIndex,
        wantsCheapest = entities["wantsCheapest"]?.toBooleanStrictOrNull() ?: false,
        wantsFastest = entities["wantsFastest"]?.toBooleanStrictOrNull() ?: false,
        wantsComfortable = entities["wantsComfortable"]?.toBooleanStrictOrNull() ?: false,
        wantsFirstOne = entities["wantsFirstOne"]?.toBooleanStrictOrNull() ?: false,
        changePickupRequest = entities["changePickupRequest"]?.toBooleanStrictOrNull() ?: false,
        changeDropRequest = entities["changeDropRequest"]?.toBooleanStrictOrNull() ?: false,
        changeRideTypeRequest = entities["changeRideTypeRequest"]?.toBooleanStrictOrNull() ?: false,
        tryAnotherAppRequest = entities["tryAnotherAppRequest"]?.toBooleanStrictOrNull() ?: false,
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
        put("rideTime", rideTime.name)
        scheduledTimeText?.takeIf { it.isNotBlank() }?.let { put("scheduledTimeText", it) }
        preference?.let { put("preference", it.name) }
        put("passengerMode", passengerMode.name)
        remotePassengerName?.takeIf { it.isNotBlank() }?.let { put("remotePassengerName", it) }
        remotePassengerPhone?.takeIf { it.isNotBlank() }?.let { put("remotePassengerPhone", it) }
        put("allowComparison", allowComparison.toString())
        put("requiresFinalConfirmation", requiresFinalConfirmation.toString())
        if (safetyNotes.isNotEmpty()) {
            put("safetyNotes", safetyNotes.joinToString(separator = ";"))
        }
        put("compareOnly", compareOnly.toString())
        put("bookNow", bookNow.toString())
        put("scheduleLater", scheduleLater.toString())
        put("manualPickupRequested", manualPickupRequested.toString())
        selectionIndex?.let { put("selectionIndex", it.toString()) }
        put("wantsCheapest", wantsCheapest.toString())
        put("wantsFastest", wantsFastest.toString())
        put("wantsComfortable", wantsComfortable.toString())
        put("wantsFirstOne", wantsFirstOne.toString())
        put("changePickupRequest", changePickupRequest.toString())
        put("changeDropRequest", changeDropRequest.toString())
        put("changeRideTypeRequest", changeRideTypeRequest.toString())
        put("tryAnotherAppRequest", tryAnotherAppRequest.toString())
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
        comparisonResult?.let { comparison ->
            if (comparison.rankedTop3.isNotEmpty()) {
                put("comparisonTop3", comparison.rankedTop3.joinToString(separator = ",") { it.provider.name })
            }
            comparison.recommendedOption?.let { option ->
                put("comparisonRecommendedProvider", option.provider.name)
            }
            if (comparison.rankingReasons.isNotEmpty()) {
                put(
                    "comparisonRankingReasons",
                    comparison.rankingReasons.entries.joinToString(separator = ";") {
                        "${it.key.name}:${it.value.joinToString(separator = "|") { reason -> reason.code }}"
                    }
                )
            }
        }
        finalSummary?.let { summary ->
            summary.provider?.let { put("finalSummaryProvider", it.name) }
            summary.pickup?.takeIf { it.isNotBlank() }?.let { put("finalSummaryPickup", it) }
            summary.destination?.takeIf { it.isNotBlank() }?.let { put("finalSummaryDestination", it) }
            summary.cabType?.let { put("finalSummaryCabType", it.name) }
            summary.estimatedFareText?.takeIf { it.isNotBlank() }?.let { put("finalSummaryFareText", it) }
            summary.pickupEtaText?.takeIf { it.isNotBlank() }?.let { put("finalSummaryPickupEtaText", it) }
            summary.travelTimeText?.takeIf { it.isNotBlank() }?.let { put("finalSummaryTravelTimeText", it) }
            summary.paymentModeText?.takeIf { it.isNotBlank() }?.let { put("finalSummaryPaymentModeText", it) }
            summary.driverName?.takeIf { it.isNotBlank() }?.let { put("finalSummaryDriverName", it) }
            summary.vehicleNumber?.takeIf { it.isNotBlank() }?.let { put("finalSummaryVehicleNumber", it) }
            if (summary.warnings.isNotEmpty()) {
                put("finalSummaryWarnings", summary.warnings.joinToString(separator = ";"))
            }
            if (summary.nextSteps.isNotEmpty()) {
                put("finalSummaryNextSteps", summary.nextSteps.joinToString(separator = ";"))
            }
            put("finalSummaryManualActionRequired", summary.manualActionRequired.toString())
            summary.voiceMessage?.takeIf { it.isNotBlank() }?.let { put("finalSummaryVoiceMessage", it) }
            summary.popupMessage?.takeIf { it.isNotBlank() }?.let { put("finalSummaryPopupMessage", it) }
            put("finalSummaryState", summary.state.name)
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
        if (providerDiagnostics.isNotEmpty()) {
            put(
                "providerDiagnostics",
                providerDiagnostics.entries.joinToString(separator = ";") { "${it.key.name}:${it.value.toSummary()}" }
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
    return if (manualActionReason == AccessibilityReadiness.BLOCKED_BY_ACCESSIBILITY_NOT_READY) {
        CommandResult.blocked(
            message = message,
            intentType = IntentType.CAB_BOOKING,
            actionType = ActionType.CAB_BOOKING,
            entities = entities
        )
    } else if (state == CabBookingState.FAILED || state == CabBookingState.MANUAL_ACTION_REQUIRED) {
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

fun CabBookingRequest.toRequirementProfile(): CabRequirementProfile {
    return CabRequirementProfile(
        pickupMode = pickupMode,
        pickupText = pickupLocation,
        pickupLatitude = pickupLatitude,
        pickupLongitude = pickupLongitude,
        destinationText = dropLocation,
        cabType = rideType,
        rideTime = rideTime,
        scheduledTimeText = scheduledTimeText,
        preference = preference,
        preferredProvider = preferredProvider,
        passengerMode = passengerMode,
        remotePassengerName = remotePassengerName,
        remotePassengerPhone = remotePassengerPhone,
        allowComparison = allowComparison,
        requiresFinalConfirmation = requiresFinalConfirmation,
        safetyNotes = safetyNotes,
        compareOnly = compareOnly,
        manualPickupRequested = manualPickupRequested
    )
}

fun CabBookingSession.toRequirementProfile(): CabRequirementProfile {
    return CabRequirementProfile(
        pickupMode = pickupMode,
        pickupText = pickup?.displayText(),
        pickupLatitude = pickup?.latitude,
        pickupLongitude = pickup?.longitude,
        destinationText = drop?.displayText(),
        cabType = rideType,
        rideTime = rideTime,
        scheduledTimeText = scheduledTimeText,
        preference = preference,
        preferredProvider = providerPreference,
        passengerMode = passengerMode,
        remotePassengerName = remotePassengerName,
        remotePassengerPhone = remotePassengerPhone,
        allowComparison = allowComparison,
        requiresFinalConfirmation = requiresFinalConfirmation,
        safetyNotes = safetyNotes.toList(),
        compareOnly = compareOnly,
        manualPickupRequested = manualPickupRequested
    )
}

fun CabBookingResult.toFinalSummary(): CabBookingFinalSummary {
    return finalSummary ?: CabBookingFinalSummary(
        state = state,
        provider = selectedProvider,
        pickup = request?.pickupLocation,
        destination = request?.dropLocation,
        cabType = selectedFare?.rideType,
        estimatedFareText = selectedFare?.finalFareText
            ?: selectedFare?.visibleFareText
            ?: selectedFare?.finalFareAmount?.let { "₹$it" }
            ?: selectedFare?.visibleFareAmount?.let { "₹$it" },
        pickupEtaText = selectedFare?.etaText,
        voiceMessage = message,
        popupMessage = message,
        manualActionRequired = manualActionRequired
    )
}
