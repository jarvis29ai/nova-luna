package com.nova.luna.grocery

import com.nova.luna.util.AccessibilityReadiness

object GroceryBookingVoiceResponses {
    fun askItems(): String {
        return "Which grocery items do you want?"
    }

    fun askPreviousList(): String {
        return "Which previous grocery list should I use?"
    }

    fun askQuantity(itemName: String? = null): String {
        return if (itemName.isNullOrBlank()) {
            "What quantity do you want?"
        } else {
            "What quantity do you want for ${itemName.trim()}?"
        }
    }

    fun askBrandPreference(itemName: String? = null): String {
        return if (itemName.isNullOrBlank()) {
            "Do you prefer any brand, or should I pick the best matching regular options?"
        } else {
            "Do you prefer any brand for ${itemName.trim()}, or should I pick the best matching regular options?"
        }
    }

    fun askBudgetPreference(): String {
        return "What budget preference should I use: cheapest, best quality, fast delivery, or best overall?"
    }

    fun askDeliveryUrgency(): String {
        return "How soon do you want delivery: now, today, or scheduled?"
    }

    fun askDeliveryLocation(): String {
        return "What delivery address or location should I use?"
    }

    fun askReplacementPreference(itemName: String? = null): String {
        return if (itemName.isNullOrBlank()) {
            "This item is unavailable. Should I choose a similar item or remove it?"
        } else {
            "The ${itemName.trim()} item is unavailable. Should I choose a similar item or remove it?"
        }
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
            add("Here is the grocery comparison.")
            comparison.candidates.forEachIndexed { index, candidate ->
                add("${index + 1}. ${candidate.provider.displayName()}: ${formatCandidate(candidate)}.")
            }
            comparison.cheapestCompleteCandidate?.let { add("Cheapest cart: ${it.provider.displayName()}.") }
            comparison.fastestCandidate?.let { add("Fastest delivery: ${it.provider.displayName()}.") }
            comparison.bestQualityCandidate?.let { add("Best quality: ${it.provider.displayName()}.") }
            comparison.bestOverallCandidate?.let { add("Best overall: ${it.provider.displayName()}.") }
            comparison.recommendedCandidate?.let { add("Recommended cart: ${it.provider.displayName()}.") }
            add("Which grocery cart should I choose?")
        }.joinToString(separator = " ")
    }

    fun askProviderChoice(comparison: GroceryComparisonResult): String {
        val cheapest = comparison.cheapestCompleteCandidate?.provider?.displayName()
            ?: comparison.recommendedCandidate?.provider?.displayName()
            ?: "one of the providers"
        return "Which grocery cart should I choose? The cheapest complete option right now is $cheapest."
    }

    fun askSelectionPrompt(): String {
        return "Which grocery cart should I choose?"
    }

    fun showFinalSummary(summary: GroceryOrderFinalSummary): String {
        val itemsText = summary.items.joinToString(separator = ", ") { item ->
            buildString {
                item.packSizeText?.takeIf { it.isNotBlank() }?.let {
                    append(it.trim())
                    append(' ')
                }
                item.brand?.takeIf { it.isNotBlank() }?.let {
                    append(it.trim())
                    append(' ')
                }
                append(item.title.trim())
            }.trim()
        }

        return buildList {
            add("${summary.appName ?: summary.provider?.displayName() ?: "The selected grocery app"} summary:")
            if (itemsText.isNotBlank()) add("Items: $itemsText.")
            summary.itemTotal?.let { add("Item total: ₹$it.") }
            summary.deliveryFee?.let { add("Delivery fee: ₹$it.") }
            summary.handlingFee?.let { add("Handling fee: ₹$it.") }
            summary.couponSaving?.let { add("Coupon saving: ₹$it.") }
            summary.finalPrice?.let { add("Final price: ₹$it.") }
            summary.deliveryAddress?.takeIf { it.isNotBlank() }?.let { add("Delivery address: ${it.trim()}.") }
            summary.deliveryTime?.takeIf { it.isNotBlank() }?.let { add("Delivery time: ${it.trim()}.") }
            summary.unavailableItems.takeIf { it.isNotEmpty() }?.let {
                add("Unavailable items: ${it.joinToString(separator = ", ")}.")
            }
            summary.replacedItems.takeIf { it.isNotEmpty() }?.let {
                add("Replaced items: ${it.joinToString(separator = ", ")}.")
            }
            summary.bestCartSuggestion?.takeIf { it.isNotBlank() }?.let {
                add("Best cart suggestion: ${it.trim()}.")
            }
            summary.bestCartReason?.takeIf { it.isNotBlank() }?.let {
                add("Why this cart is best: ${it.trim()}.")
            }
            summary.warning?.takeIf { it.isNotBlank() }?.let {
                add("Warning: ${it.trim()}.")
            }
            add("Confirm this grocery order?")
        }.joinToString(separator = " ")
    }

    fun askFinalConfirmation(candidate: GroceryCartCandidate): String {
        val summary = candidate.summary
        return buildList {
            add("${candidate.provider.displayName()} cart is ready.")
            summary.finalPayableValue?.let { add("Final price is ₹$it.") }
            summary.deliveryFee?.let { add("Delivery fee is ₹$it.") }
            summary.couponDiscount?.let { add("Coupon saving is ₹$it.") }
            summary.etaText?.takeIf { it.isNotBlank() }?.let { add("Delivery time is ${it.trim()}.") }
            if (summary.unavailableItems.isNotEmpty()) {
                add("Unavailable items: ${summary.unavailableItems.joinToString(separator = ", ")}.")
            }
            add("Confirm this grocery order?")
        }.joinToString(separator = " ")
    }

    fun askPaymentMethod(): String {
        return "Which payment method should I use: UPI, card, net banking, wallet, or cash on delivery?"
    }

    fun askWalletConfirmation(summary: GroceryOrderFinalSummary, walletText: String): String {
        val price = summary.finalPrice?.let { "₹$it" } ?: "the order total"
        return "I found wallet balance details: $walletText. If the wallet balance is enough for $price, do you want me to continue with wallet payment?"
    }

    fun walletBalanceUnknown(): String {
        return "I could not read wallet balance. Please check it manually or choose another payment method."
    }

    fun walletInsufficient(balance: Long, orderAmount: Long): String {
        return "Wallet balance is ₹$balance, which is lower than the order amount ₹$orderAmount. Please choose UPI, card, cash on delivery, or manual payment."
    }

    fun askCodConfirmation(summary: GroceryOrderFinalSummary): String {
        val price = summary.finalPrice?.let { "₹$it" } ?: "the order total"
        return "Cash on delivery is available. Do you want me to continue with COD for $price?"
    }

    fun codUnavailable(): String {
        return "Cash on delivery is not available for this order. Please choose another payment method."
    }

    fun paymentManualRequired(method: GroceryPaymentMethod): String {
        return "I opened the ${method.displayName()} payment page, but payment, OTP, PIN, password, or CVV must be completed manually."
    }

    fun paymentBoundaryRequired(): String {
        return "This screen needs payment, OTP, PIN, password, or login. Please complete it manually."
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
            return "I could not find Blinkit, Zepto, Instamart, JioMart, or BigBasket installed on this phone."
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
            GroceryManualActionReason.PASSWORD -> "password"
            GroceryManualActionReason.CAPTCHA -> "captcha"
            GroceryManualActionReason.UPI_PIN -> "UPI PIN"
            GroceryManualActionReason.CARD_CVV -> "card CVV"
            GroceryManualActionReason.NET_BANKING -> "net banking"
            GroceryManualActionReason.WALLET_TOPUP -> "wallet top-up"
            GroceryManualActionReason.BIOMETRIC -> "biometric"
            GroceryManualActionReason.ADDRESS -> "address selection"
            GroceryManualActionReason.LOCATION_PERMISSION -> "location permission"
            GroceryManualActionReason.USAGE_ACCESS -> "usage access"
            GroceryManualActionReason.ACCESSIBILITY -> "accessibility"
            GroceryManualActionReason.REPLACEMENT -> "replacement selection"
            GroceryManualActionReason.UNAVAILABLE_ITEMS -> "unavailable items"
            GroceryManualActionReason.MANUAL_SCREEN -> "manual action"
            GroceryManualActionReason.PERMISSION -> "permission"
            GroceryManualActionReason.UNKNOWN, null -> "manual action"
        }
    }

    fun manualActionRequired(provider: GroceryProvider?, reason: String): String {
        val providerLabel = provider?.displayName()?.let { " in $it" }.orEmpty()
        val spokenReason = when (reason) {
            AccessibilityReadiness.BLOCKED_BY_ACCESSIBILITY_NOT_READY -> AccessibilityReadiness.blockedMessage()
            GroceryFailureReasons.BLOCKED_BY_LOCATION_PERMISSION -> "location permission"
            GroceryFailureReasons.BLOCKED_BY_USAGE_ACCESS -> "usage access"
            else -> reason
        }
        return "This step needs you$providerLabel. Please handle $spokenReason manually."
    }

    fun orderPlaced(summary: GroceryOrderFinalSummary, confirmation: GroceryOrderConfirmation?): String {
        val orderId = confirmation?.orderId ?: summary.orderId
        val deliveryEstimate = confirmation?.deliveryEstimate ?: summary.deliveryTime
        val finalPrice = confirmation?.finalPrice ?: summary.finalPrice
        val savings = confirmation?.totalSavings ?: summary.couponSaving
        return buildList {
            add("The grocery order appears placed.")
            summary.appName?.takeIf { it.isNotBlank() }?.let { add("App: ${it.trim()}.") }
            summary.deliveryAddress?.takeIf { it.isNotBlank() }?.let { add("Delivery address: ${it.trim()}.") }
            finalPrice?.let { add("Final price: ₹$it.") }
            savings?.let { add("Total savings: ₹$it.") }
            deliveryEstimate?.takeIf { it.isNotBlank() }?.let { add("Delivery estimate: ${it.trim()}.") }
            orderId?.takeIf { it.isNotBlank() }?.let { add("Order ID: ${it.trim()}.") }
            add("Payment/OTP/PIN/password must be completed manually if the app asks for it.")
        }.joinToString(separator = " ")
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
            if (candidate.productOptions.isNotEmpty()) {
                add(candidate.productOptions.take(3).joinToString(separator = ", ") { option ->
                    buildString {
                        append(option.title)
                        option.priceValue?.let { append(" ₹").append(it) }
                        option.packSizeText?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    }
                })
            }
        }.joinToString(separator = ", ")
    }
}
