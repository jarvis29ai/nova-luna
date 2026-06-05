package com.nova.luna.cab

import com.nova.luna.util.AccessibilityReadiness

object CabBookingVoiceResponses {
    fun askPickup(): String {
        return "I need your pickup location. Should I use your current location or a manual pickup?"
    }

    fun currentLocationUnavailable(): String {
        return "I could not access your current location. Please tell me your pickup location."
    }

    fun currentLocationPermissionRequired(): String {
        return "I could not use current location because Location permission is off. Please enable Location for Nova/Luna in Android settings, then say current location again, or tell me your pickup location."
    }

    fun usingCurrentLocationAsPickup(): String {
        return "I will use current location as pickup in the cab app."
    }

    fun askDrop(): String {
        return "I need the destination."
    }

    fun askRideType(): String {
        return "Which cab type do you prefer: bike, auto, mini, sedan, SUV, or any?"
    }

    fun askRideTime(): String {
        return "Is this ride for now or should I schedule it for later?"
    }

    fun askPreference(): String {
        return "What matters most: cheapest, fastest, comfortable, or a preferred app/provider?"
    }

    fun askPassengerMode(): String {
        return "Is this ride for you or for someone else?"
    }

    fun askPlatformChoice(
        options: List<CabFareOption>,
        skippedProviders: Map<CabProvider, String>,
        providerFailures: Map<CabProvider, String> = emptyMap()
    ): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        val lines = options.take(3).mapIndexed { index, option ->
            val label = when (index) {
                0 -> "Recommended"
                else -> "${index + 1}"
            }
            "$label: ${formatFareOption(option)}"
        }
        val recommendation = options.firstOrNull()?.let { "Recommended: ${formatFareOption(it)}." }.orEmpty()
        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} was skipped because ${it.value}"
        }
        val failureSummary = providerFailureSummary(providerFailures)

        return buildList {
            add("I found these fares, lowest to highest:")
            add(lines.joinToString(separator = "\n"))
            if (recommendation.isNotBlank()) {
                add(recommendation)
            }
            add("Which ride should I choose?")
            add("You can say first one, second one, third one, cheapest, fastest, Ola, Uber, Rapido, or inDrive.")
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            if (failureSummary.isNotBlank()) {
                add(failureSummary)
            }
        }.joinToString(separator = " ")
    }

    fun fillingProvider(provider: CabProvider): String {
        return "I am filling pickup and drop in ${provider.displayName()} now."
    }

    fun manualDestinationHandoff(provider: CabProvider): String {
        return "I opened ${provider.displayName()}, but the destination field is not accessible on this screen. Please enter or select the destination manually. Once ride options are visible, I can compare the fares."
    }

    fun noFareVisible(provider: CabProvider? = null): String {
        val providerPart = provider?.displayName()?.let { " on $it" }.orEmpty()
        return "I can see the provider screen$providerPart, but no fare is visible yet. Once ride options are visible, say done or compare now and I will read them."
    }

    fun providerSkipped(
        provider: CabProvider,
        reason: String,
        nextProvider: CabProvider? = null
    ): String {
        val nextPart = nextProvider?.let { ", so I am skipping ${provider.displayName()} and checking ${it.displayName()}." }
            ?: ", so I am skipping ${provider.displayName()}."
        return "I could not finish ${provider.displayName()} because ${describeFailureReason(reason)}$nextPart"
    }

    fun providerFailed(provider: CabProvider, reason: String): String {
        return "${provider.displayName()} failed because ${describeFailureReason(reason)}."
    }

    fun fareComparison(options: List<CabFareOption>): String {
        if (options.isEmpty()) return "I could not read any fares yet."

        return options.mapIndexed { index, option ->
            "${index + 1}. ${formatFareOption(option)}"
        }.joinToString(separator = "\n")
    }

    fun showComparison(
        options: List<CabFareOption>,
        skippedProviders: Map<CabProvider, String>,
        providerFailures: Map<CabProvider, String> = emptyMap()
    ): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} was skipped because ${it.value}"
        }
        val failureText = providerFailureSummary(providerFailures)
        val top3 = options.take(3)

        return buildList {
            add("I found these fares, lowest to highest:")
            add(top3.mapIndexed { index, option ->
                val label = if (index == 0) "Recommended" else "${index + 1}"
                "$label. ${formatFareOption(option)}"
            }.joinToString(separator = "\n"))
            if (options.size > 3) {
                add("There are ${options.size} total options.")
            }
            add("Which ride should I choose?")
            add("You can say first one, second one, third one, cheapest, fastest, Ola, Uber, Rapido, or inDrive.")
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            if (failureText.isNotBlank()) {
                add(failureText)
            }
        }.joinToString(separator = " ")
    }

    fun cheapestSelected(option: CabFareOption): String {
        return "I selected ${formatFareOption(option)}. I need your confirmation before booking."
    }

    fun askFinalConfirmation(option: CabFareOption): String {
        return "Please confirm: book ${formatFareOption(option)}?"
    }

    fun askFinalConfirmation(summary: CabBookingFinalSummary): String {
        val providerPart = summary.provider?.displayName()?.let { "$it " }.orEmpty()
        val cabTypePart = summary.cabType?.displayName()?.let { "${it.lowercase()} " }.orEmpty()
        val farePart = summary.estimatedFareText ?: "fare unavailable"
        val routePart = listOfNotNull(summary.pickup, summary.destination).joinToString(separator = " to ")
        return "Please confirm: book ${providerPart}${cabTypePart}from ${routePart.ifBlank { "this route" }} for $farePart?"
    }

    fun finalRideSummary(summary: CabBookingFinalSummary): String {
        val parts = buildList {
            summary.provider?.let { add("App: ${it.displayName()}") }
            summary.pickup?.takeIf { it.isNotBlank() }?.let { add("Pickup: $it") }
            summary.destination?.takeIf { it.isNotBlank() }?.let { add("Destination: $it") }
            summary.cabType?.let { add("Cab type: ${it.displayName()}") }
            summary.estimatedFareText?.takeIf { it.isNotBlank() }?.let { add("Fare: $it") }
            summary.pickupEtaText?.takeIf { it.isNotBlank() }?.let { add("Pickup ETA: $it") }
            summary.travelTimeText?.takeIf { it.isNotBlank() }?.let { add("Travel time: $it") }
            summary.paymentModeText?.takeIf { it.isNotBlank() }?.let { add("Payment: $it") }
            summary.driverName?.takeIf { it.isNotBlank() }?.let { add("Driver: $it") }
            summary.vehicleNumber?.takeIf { it.isNotBlank() }?.let { add("Vehicle: $it") }
            if (summary.warnings.isNotEmpty()) {
                add("Warnings: ${summary.warnings.joinToString(separator = ", ")}")
            }
            if (summary.nextSteps.isNotEmpty()) {
                add("Next steps: ${summary.nextSteps.joinToString(separator = ", ")}")
            }
        }
        if (parts.isEmpty()) {
            return "I do not have a ride summary yet."
        }
        return parts.joinToString(separator = ". ") + "."
    }

    fun manualActionRequired(provider: CabProvider?, reason: String): String {
        val providerLabel = provider?.displayName()?.let { " in $it" }.orEmpty()
        val manualHint = when {
            reason.contains("payment", ignoreCase = true) ||
                reason.contains("otp", ignoreCase = true) ||
                reason.contains("login", ignoreCase = true) ||
                reason.contains("captcha", ignoreCase = true) ->
                "Payment/OTP/login/CAPTCHA must be completed manually."

            else -> "Please complete $reason manually."
        }
        return "This step needs you$providerLabel. $manualHint"
    }

    fun bookingCompleted(option: CabFareOption): String {
        return "Booking completed for ${formatFareOption(option)}."
    }

    fun bookingCompleted(summary: CabBookingFinalSummary): String {
        return buildString {
            append("The ride appears booked.")
            summary.provider?.let { append(" App: ${it.displayName()}.") }
            summary.driverName?.takeIf { it.isNotBlank() }?.let { append(" Driver: $it.") }
            summary.vehicleNumber?.takeIf { it.isNotBlank() }?.let { append(" Vehicle: $it.") }
            summary.pickupEtaText?.takeIf { it.isNotBlank() }?.let { append(" Pickup ETA: $it.") }
            summary.estimatedFareText?.takeIf { it.isNotBlank() }?.let { append(" Fare: $it.") }
            if (summary.nextSteps.isNotEmpty()) {
                append(" Next steps: ${summary.nextSteps.joinToString(separator = ", ")}.")
            }
        }
    }

    fun bookingCancelled(): String {
        return "Okay, I cancelled the cab booking."
    }

    fun bookingFailed(reason: String): String {
        return "I could not complete the cab booking: $reason."
    }

    fun providerFailureSummary(providerFailures: Map<CabProvider, String>): String {
        if (providerFailures.isEmpty()) return ""

        val failureText = providerFailures.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} failed because ${describeFailureReason(it.value)}"
        }
        return "Provider issues: $failureText."
    }

    fun noProvidersAvailable(skippedProviders: Map<CabProvider, String>): String {
        if (skippedProviders.isEmpty()) {
            return "I could not find Uber, Ola, Rapido, or inDrive installed on this phone. You can install a cab app or use a browser/maps flow."
        }

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} was skipped because ${it.value}"
        }
        return "I could not find a usable cab app. $skippedText. You can install a cab app or use a browser/maps flow."
    }

    fun formatFareOption(option: CabFareOption): String {
        val finalText = when {
            !option.finalFareText.isNullOrBlank() -> option.finalFareText
            option.finalFareAmount != null -> "₹${option.finalFareAmount}"
            !option.visibleFareText.isNullOrBlank() -> option.visibleFareText
            option.visibleFareAmount != null -> "₹${option.visibleFareAmount}"
            else -> "fare unavailable"
        }

        val originalText = when {
            option.originalFareAmount != null &&
                option.finalFareAmount != null &&
                option.originalFareAmount != option.finalFareAmount ->
                "original ₹${option.originalFareAmount}"

            option.visibleFareAmount != null &&
                option.finalFareAmount != null &&
                option.visibleFareAmount != option.finalFareAmount ->
                "original ₹${option.visibleFareAmount}"

            !option.visibleFareText.isNullOrBlank() &&
                !option.finalFareText.isNullOrBlank() &&
                option.visibleFareText != option.finalFareText ->
                "original ${option.visibleFareText}"

            else -> null
        }

        val etaText = option.etaText
            ?: option.etaMinutes?.let { "$it min" }

        val extras = buildList<String> {
            option.couponText?.takeIf { it.isNotBlank() }?.let { add(it) }
            option.discountText?.takeIf { it.isNotBlank() }?.let { add(it) }
        }

        return buildString {
            append(option.provider.displayName())
            append(' ')
            append(option.rideType.displayName())
            append(" - ")
            append(finalText)
            originalText?.let {
                append(" (")
                append(it)
                append(')')
            }
            if (!etaText.isNullOrBlank()) {
                if (etaText.trim().lowercase().startsWith("eta")) {
                    append(", ")
                    append(etaText.trim())
                } else {
                    append(", ETA ")
                    append(etaText)
                }
            }
            if (extras.isNotEmpty()) {
                append(", ")
                append(extras.joinToString(separator = ", "))
            }
        }
    }

    fun describeFailureReason(reason: String): String {
        return when (reason) {
            "provider_did_not_open_foreground" -> "the provider app did not come to the foreground"
            else -> when {
                reason.contains(CabFailureReasons.PICKUP_FIELD_NOT_FOUND) &&
                    reason.contains(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND) ->
                    "the pickup and destination fields were not accessible on this screen"

                reason.contains(CabFailureReasons.PICKUP_FIELD_NOT_FOUND) ->
                    "the pickup field was not accessible on this screen"

                reason.contains(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND) ->
                    "the destination field was not accessible on this screen"

                reason.contains(CabFailureReasons.DESTINATION_FIELD_INACCESSIBLE) ->
                    "the destination field was not accessible on this screen"

                reason == CabFailureReasons.PROVIDER_NOT_OPENED ->
                    "the provider app could not be opened"

                reason == CabFailureReasons.PROVIDER_FOREGROUND_TIMEOUT ->
                    "the provider app did not come to the foreground"

                reason == CabFailureReasons.BLOCKED_BY_LOCATION_PERMISSION ->
                    "location permission is missing. Please enable Location for Nova/Luna in Android settings."

                reason == CabFailureReasons.NO_FARE_VISIBLE ->
                    "no fare was visible on the provider screen"

                reason == CabFailureReasons.MANUAL_ACTION_REQUIRED ->
                    "manual action is required on the provider screen"

                reason == CabFailureReasons.RIDE_TYPE_NOT_SELECTED ->
                    "the ride type could not be selected"

                reason == CabFailureReasons.PROVIDER_SCREEN_UNAVAILABLE ->
                    "the provider screen was not available"

                reason == AccessibilityReadiness.BLOCKED_BY_ACCESSIBILITY_NOT_READY ->
                    "accessibility service is not ready"

                else -> reason
            }
        }
    }
}
