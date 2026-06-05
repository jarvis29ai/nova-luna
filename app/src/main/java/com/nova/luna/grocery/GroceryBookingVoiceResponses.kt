package com.nova.luna.grocery

object GroceryBookingVoiceResponses {
    fun askItems(): String {
        return "What groceries should I add?"
    }

    fun askBrandPreference(): String {
        return "Do you want any specific brand, or should I pick the best matching regular options?"
    }

    fun askQuantity(itemName: String): String {
        return "How much $itemName do you want?"
    }

    fun askComparisonConfirmation(providerNames: List<String>): String {
        val providers = providerNames.joinToString(separator = ", ")
        return "I can compare this basket on $providers. Do you want me to continue?"
    }

    fun showComparison(comparison: GroceryComparisonResult): String {
        if (comparison.candidates.isEmpty()) {
            return "I could not compare the basket because no provider returned a readable cart."
        }

        return buildList {
            add("Here’s the comparison:")
            comparison.candidates.forEach { candidate ->
                add("${candidate.provider.displayName()}: ${formatCandidate(candidate)}.")
            }
            comparison.recommendedCandidate?.let { recommended ->
                val cheapest = comparison.cheapestCompleteCandidate
                if (recommended == cheapest && cheapest != null) {
                    add("Cheapest complete basket is ${recommended.provider.displayName()}.")
                } else {
                    add("Best fast option is ${recommended.provider.displayName()}.")
                }
            }
            comparison.cheapestCompleteCandidate?.let { cheapest ->
                if (comparison.recommendedCandidate != cheapest) {
                    add("Cheapest is ${cheapest.provider.displayName()}.")
                }
            }
            add("Which one should I prepare?")
        }.joinToString(separator = " ")
    }

    fun explainUnavailableItems(provider: GroceryProvider, unavailableItems: List<String>): String {
        if (unavailableItems.isEmpty()) return "${provider.displayName()} looks complete."
        return "${provider.displayName()} could not find ${unavailableItems.joinToString(separator = ", ")}. Do you want a replacement or a different provider?"
    }

    fun askProviderChoice(comparison: GroceryComparisonResult): String {
        val cheapest = comparison.cheapestCompleteCandidate?.provider?.displayName()
            ?: comparison.recommendedCandidate?.provider?.displayName()
            ?: "one of the providers"
        return "Which one should I prepare? The cheapest complete option right now is $cheapest."
    }

    fun askFinalConfirmation(candidate: GroceryCartCandidate): String {
        return "${candidate.provider.displayName()} cart is ready at ${formatPayable(candidate.summary.finalPayableValue)}. I’m stopping before payment. Please confirm manually or say confirm if you want me to tap the final order button."
    }

    fun manualActionRequired(provider: GroceryProvider?, reason: String): String {
        val providerLabel = provider?.displayName()?.let { " in $it" }.orEmpty()
        return "This step needs you$providerLabel. Please handle $reason manually."
    }

    fun finalCheckoutReady(candidate: GroceryCartCandidate): String {
        return askFinalConfirmation(candidate)
    }

    fun bookingCancelled(): String {
        return "Okay, I cancelled the grocery flow."
    }

    fun bookingFailed(reason: String): String {
        return "I could not complete the grocery flow: $reason."
    }

    fun noProvidersAvailable(skippedProviders: Map<GroceryProvider, String>): String {
        if (skippedProviders.isEmpty()) {
            return "I could not find Blinkit, JioMart, or Instamart installed on this phone."
        }

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} was skipped because ${it.value}"
        }
        return "I could not find a usable grocery app. $skippedText."
    }

    fun describeManualActionReason(reason: GroceryManualActionReason?): String {
        return when (reason) {
            GroceryManualActionReason.LOGIN -> "login"
            GroceryManualActionReason.OTP -> "OTP"
            GroceryManualActionReason.PAYMENT -> "payment"
            GroceryManualActionReason.CAPTCHA -> "captcha"
            GroceryManualActionReason.ADDRESS -> "address selection"
            GroceryManualActionReason.REPLACEMENT -> "replacement selection"
            GroceryManualActionReason.UNAVAILABLE_ITEMS -> "unavailable items"
            GroceryManualActionReason.MANUAL_SCREEN -> "manual action"
            GroceryManualActionReason.PERMISSION -> "permission"
            GroceryManualActionReason.UNKNOWN, null -> "manual action"
        }
    }

    fun formatCandidate(candidate: GroceryCartCandidate): String {
        val summary = candidate.summary
        val finalText = when {
            summary.finalPayableValue != null -> "₹${summary.finalPayableValue}"
            summary.itemSubtotal != null -> "₹${summary.itemSubtotal}"
            else -> "price unavailable"
        }

        val feeParts = buildList<String> {
            summary.deliveryFee?.let { add("delivery ₹$it") }
            summary.handlingFee?.let { add("handling ₹$it") }
            summary.couponDiscount?.takeIf { it > 0 }?.let { add("coupon -₹$it") }
        }

        val etaText = summary.etaText ?: summary.etaMinutes?.let { "$it min" }
        val unavailableText = if (summary.unavailableItems.isNotEmpty()) {
            "unavailable ${summary.unavailableItems.joinToString(separator = ", ")}"
        } else {
            null
        }

        return buildList {
            add(finalText)
            if (feeParts.isNotEmpty()) {
                add(feeParts.joinToString(separator = ", "))
            }
            if (!etaText.isNullOrBlank()) {
                add("ETA $etaText")
            }
            if (summary.couponApplied) {
                add(summary.couponCode?.let { "coupon $it applied" } ?: "coupon applied")
            }
            if (!unavailableText.isNullOrBlank()) {
                add(unavailableText)
            }
        }.joinToString(separator = ", ")
    }

    private fun formatPayable(value: Long?): String {
        return value?.let { "₹$it" } ?: "price unavailable"
    }
}
