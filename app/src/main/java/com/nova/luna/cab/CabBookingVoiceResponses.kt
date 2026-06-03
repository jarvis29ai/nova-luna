package com.nova.luna.cab

object CabBookingVoiceResponses {
    fun askPickup(): String {
        return "What is your pickup location?"
    }

    fun askDrop(): String {
        return "Where do you want to go?"
    }

    fun askRideType(): String {
        return "Which ride type do you want - auto, bike, mini, sedan, SUV, or any?"
    }

    fun askPlatformChoice(
        options: List<CabFareOption>,
        skippedProviders: Map<CabProvider, String>,
        providerFailures: Map<CabProvider, String> = emptyMap()
    ): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        val parts = buildList {
            add("Which one should I book?")
            add("You can say Book the cheapest, Book Rapido, or Book the first one.")
            val failureSummary = providerFailureSummary(providerFailures)
            if (failureSummary.isNotBlank()) {
                add(failureSummary)
            }
        }

        return parts.joinToString(separator = " ")
    }

    fun showComparison(
        options: List<CabFareOption>,
        skippedProviders: Map<CabProvider, String>,
        providerFailures: Map<CabProvider, String> = emptyMap()
    ): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        val optionText = options.mapIndexed { index, option ->
            "${index + 1}. ${formatFareOption(option)}"
        }.joinToString(separator = "\n")

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} skipped because ${it.value}"
        }
        val failureText = providerFailureSummary(providerFailures)

        return buildList {
            add("I found these options:\n$optionText")
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            if (failureText.isNotBlank()) {
                add(failureText)
            }
            add("Which one should I book?")
        }.joinToString(separator = " ")
    }

    fun askFinalConfirmation(option: CabFareOption): String {
        return "I found ${formatFareOption(option)}. Should I confirm booking?"
    }

    fun manualActionRequired(provider: CabProvider?, reason: String): String {
        val providerLabel = provider?.displayName() ?: "this provider"
        return "I found a manual step in $providerLabel: $reason. Please complete it yourself, then tell me to continue."
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
            "${it.key.displayName()} failed because ${it.value}"
        }
        return "Provider issues: $failureText"
    }

    fun noProvidersAvailable(skippedProviders: Map<CabProvider, String>): String {
        return "I could not find Uber, Ola, Rapido, or inDrive installed on this phone."
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
}
