package com.nova.luna.food

object FoodBookingVoiceResponses {
    fun askFoodItem(): String {
        return "What would you like to order?"
    }

    fun askRestaurant(): String {
        return "Which restaurant should I search for?"
    }

    fun askPlatformChoice(options: List<FoodPlatformQuote>, skippedProviders: Map<FoodProvider, String>): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        return "Which platform should I order from? Say the cheapest option, or say the platform name you want me to use."
    }

    fun showComparison(options: List<FoodPlatformQuote>, skippedProviders: Map<FoodProvider, String>): String {
        if (options.isEmpty()) {
            return noProvidersAvailable(skippedProviders)
        }

        val optionText = options.joinToString(separator = ". ") { formatQuote(it) }
        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} skipped because ${it.value}"
        }

        val parts = buildList {
            add("I found these options: $optionText.")
            if (skippedText.isNotBlank()) {
                add("Skipped apps: $skippedText.")
            }
            add("Which platform should I order from?")
        }

        return parts.joinToString(separator = " ")
    }

    fun askFinalConfirmation(option: FoodPlatformQuote): String {
        val amountText = formatMoney(option.finalPayableAmount ?: option.visiblePriceAmount)
        return "I found ${formatQuote(option)}. Confirm placing order on ${option.provider.displayName()} for $amountText?"
    }

    fun manualActionRequired(reason: String): String {
        return "I found a screen that needs manual action: $reason. Please complete it yourself, then tell me to continue."
    }

    fun orderCompleted(option: FoodPlatformQuote): String {
        return "Order handoff completed for ${formatQuote(option)}."
    }

    fun orderFailed(reason: String): String {
        return "I could not complete the food order: $reason."
    }

    fun noProvidersAvailable(skippedProviders: Map<FoodProvider, String>): String {
        if (skippedProviders.isEmpty()) {
            return "I could not find any supported food apps installed on this phone."
        }

        val skippedText = skippedProviders.entries.joinToString(separator = ". ") {
            "${it.key.displayName()} skipped because ${it.value}"
        }
        return "I could not find any supported food apps installed on this phone. $skippedText."
    }

    fun formatQuote(option: FoodPlatformQuote): String {
        val priceText = when {
            !option.finalPayableText.isNullOrBlank() -> option.finalPayableText
            !option.visiblePriceText.isNullOrBlank() -> option.visiblePriceText
            option.finalPayableAmount != null -> formatMoney(option.finalPayableAmount)
            option.visiblePriceAmount != null -> formatMoney(option.visiblePriceAmount)
            else -> "price unavailable"
        }

        val parts = mutableListOf<String>()
        parts.add("${option.provider.displayName()}: $priceText final")

        if (option.selectedCoupon != null) {
            val coupon = option.selectedCoupon
            val couponLabel = when {
                !coupon.code.isNullOrBlank() && coupon.applied -> "coupon ${coupon.code} applied"
                !coupon.code.isNullOrBlank() -> "coupon ${coupon.code}"
                !coupon.savingsText.isNullOrBlank() -> coupon.savingsText
                else -> "coupon applied"
            }
            parts.add(couponLabel)
        } else if (!option.couponText.isNullOrBlank()) {
            parts.add(option.couponText!!.trim())
        } else {
            parts.add("no coupon found")
        }

        if (option.deliveryFeeAmount != null) {
            parts.add("delivery fee ${formatMoney(option.deliveryFeeAmount)}")
        } else if (!option.deliveryFeeText.isNullOrBlank()) {
            parts.add(option.deliveryFeeText!!.trim())
        }

        if (option.taxAmount != null) {
            parts.add("taxes ${formatMoney(option.taxAmount)}")
        } else if (!option.taxText.isNullOrBlank()) {
            parts.add(option.taxText!!.trim())
        }

        if (option.discountAmount != null) {
            parts.add("discount ${formatMoney(option.discountAmount)}")
        } else if (!option.discountText.isNullOrBlank()) {
            parts.add(option.discountText!!.trim())
        }

        if (option.etaMinutes != null) {
            parts.add("ETA ${option.etaMinutes} min")
        } else if (!option.etaText.isNullOrBlank()) {
            parts.add(option.etaText!!.trim())
        }

        return parts.joinToString(separator = ", ")
    }

    private fun formatMoney(amount: Long?): String {
        return amount?.let { "\u20B9$it" } ?: "price unavailable"
    }
}
