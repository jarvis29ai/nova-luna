package com.nova.luna.cab

object CabBookingVoiceResponses {
    fun askPickup(): String {
        return "What is your pickup location?"
    }

    fun askDrop(): String {
        return "What is your drop location?"
    }

    fun askRideType(): String {
        return "Which ride type do you want? Auto, bike, mini, sedan, SUV, or any ride type?"
    }

    fun askPlatformChoice(options: List<CabFareOption>, skippedProviders: Map<CabProvider, String>): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        return "Say Book the cheapest, or say the provider name you want me to open."
    }

    fun showComparison(options: List<CabFareOption>, skippedProviders: Map<CabProvider, String>): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        val optionText = options.joinToString(separator = ". ") { formatFareOption(it) }
        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} skipped because ${it.value}"
        }

        val parts = buildList {
            add("I found these options: $optionText.")
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            add("You can say Book the cheapest, or say a provider name like Book Rapido.")
        }

        return parts.joinToString(separator = " ")
    }

    fun askFinalConfirmation(option: CabFareOption): String {
        return "I found ${formatFareOption(option)}. Should I confirm booking?"
    }

    fun manualActionRequired(reason: String): String {
        return "I found a screen that needs manual action: $reason. Please complete it yourself, then tell me to continue."
    }

    fun bookingCompleted(option: CabFareOption): String {
        return "Booking completed for ${formatFareOption(option)}."
    }

    fun bookingFailed(reason: String): String {
        return "I could not complete the cab booking: $reason."
    }

    fun noProvidersAvailable(skippedProviders: Map<CabProvider, String>): String {
        if (skippedProviders.isEmpty()) {
            return "I could not find any supported cab apps installed on this phone."
        }

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} skipped because ${it.value}"
        }
        return "I could not find any supported cab apps installed on this phone. $skippedText."
    }

    fun formatFareOption(option: CabFareOption): String {
        val fareText = when {
            !option.finalFareText.isNullOrBlank() -> option.finalFareText
            !option.visibleFareText.isNullOrBlank() -> option.visibleFareText
            option.finalFareAmount != null -> "₹${option.finalFareAmount}"
            option.visibleFareAmount != null -> "₹${option.visibleFareAmount}"
            else -> "fare unavailable"
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
            append(": ")
            append(fareText)
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
