package com.nova.luna.cab

object CabBookingVoiceResponses {
    fun askPickup(): String {
        return "Tell me your pickup location, or say current location."
    }

    fun currentLocationUnavailable(): String {
        return "I could not access your current location. Please tell me your pickup location."
    }

    fun usingCurrentLocationAsPickup(): String {
        return "I will use current location as pickup in the cab app."
    }

    fun askDrop(): String {
        return "Tell me your destination."
    }

    fun askRideType(): String {
        return "Which ride type do you want: auto, bike, mini, sedan, SUV, or any?"
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

    fun askPlatformChoice(
        options: List<CabFareOption>,
        skippedProviders: Map<CabProvider, String>,
        providerFailures: Map<CabProvider, String> = emptyMap()
    ): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        return buildList {
            add("I found these fares, lowest to highest:")
            add(options.mapIndexed { index, option ->
                "${index + 1}. ${formatFareOption(option)}"
            }.joinToString(separator = "\n"))
            add("You can say book the cheapest, book the first one, book Rapido, or use Uber.")
            val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
                "${it.key.displayName()} was skipped because ${it.value}"
            }
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            val failureSummary = providerFailureSummary(providerFailures)
            if (failureSummary.isNotBlank()) {
                add(failureSummary)
            }
        }.joinToString(separator = " ")
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

        return buildList {
            add("I found these fares, lowest to highest:\n${fareComparison(options)}")
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            if (failureText.isNotBlank()) {
                add(failureText)
            }
            add("You can say book the cheapest, book the first one, book Rapido, or use Uber.")
        }.joinToString(separator = " ")
    }

    fun cheapestSelected(option: CabFareOption): String {
        return "I selected ${formatFareOption(option)}. I need your confirmation before booking."
    }

    fun askFinalConfirmation(option: CabFareOption): String {
        return "I found ${formatFareOption(option)}. Should I confirm booking?"
    }

    fun manualActionRequired(provider: CabProvider?, reason: String): String {
        val providerLabel = provider?.displayName()?.let { " in $it" }.orEmpty()
        return "This step needs you$providerLabel. Please complete $reason manually."
    }

    fun bookingCompleted(option: CabFareOption): String {
        return "Booking completed for ${formatFareOption(option)}."
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
            return "I could not find Uber, Ola, Rapido, or inDrive installed on this phone."
        }

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} was skipped because ${it.value}"
        }
        return "I could not find a usable cab app. $skippedText."
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

                reason == CabFailureReasons.PROVIDER_NOT_OPENED ->
                    "the provider app could not be opened"

                reason == CabFailureReasons.PROVIDER_FOREGROUND_TIMEOUT ->
                    "the provider app did not come to the foreground"

                reason == CabFailureReasons.BLOCKED_BY_LOCATION_PERMISSION ->
                    "location permission is missing"

                reason == CabFailureReasons.NO_FARE_VISIBLE ->
                    "no fare was visible on the provider screen"

                reason == CabFailureReasons.MANUAL_ACTION_REQUIRED ->
                    "manual action is required on the provider screen"

                reason == CabFailureReasons.RIDE_TYPE_NOT_SELECTED ->
                    "the ride type could not be selected"

                reason == CabFailureReasons.PROVIDER_SCREEN_UNAVAILABLE ->
                    "the provider screen was not available"

                else -> reason
            }
        }
    }
}
