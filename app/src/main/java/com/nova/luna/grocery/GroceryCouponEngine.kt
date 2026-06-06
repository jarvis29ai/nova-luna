package com.nova.luna.grocery

interface GroceryCouponAutomation {
    fun clickTextOrDescriptionAnyOf(candidates: List<String>): Boolean
    fun typeIntoFocusedField(text: String): Boolean
}

class GroceryCouponEngine(
    private val priceComparator: GroceryPriceComparator = GroceryPriceComparator()
) {
    fun detectVisibleCouponText(texts: List<String>): String? {
        return chooseBestCoupon(texts).first
    }

    fun chooseBestCoupon(texts: List<String>): Pair<String?, Long?> {
        val candidateTexts = texts.filter { text ->
            val normalized = text.lowercase()
            normalized.contains("coupon") ||
                normalized.contains("offer") ||
                normalized.contains("promo") ||
                normalized.contains("discount") ||
                normalized.contains("apply")
        }

        if (candidateTexts.isEmpty()) return null to null

        val ranked = candidateTexts.map { text ->
            val savings = priceComparator.extractCouponSaving(text)
                ?: priceComparator.extractAmount(text)
                ?: 0L
            text to savings
        }

        return ranked.maxByOrNull { it.second } ?: (candidateTexts.first() to null)
    }

    fun applyVisibleCoupon(
        provider: GroceryProvider,
        snapshot: GroceryScreenSnapshot?,
        automation: GroceryCouponAutomation,
        userCouponCode: String? = null
    ): GroceryCouponResult {
        val visibleText = snapshot?.visibleText.orEmpty()
        val unsafeReason = detectUnsafeCouponFlow(visibleText)
        if (unsafeReason != null) {
            return GroceryCouponResult(
                provider = provider,
                applied = false,
                found = false,
                message = "I found a coupon screen on ${provider.displayName()}, but it looks like a $unsafeReason screen and must stay manual.",
                warning = unsafeReason
            )
        }

        val (couponText, visibleSavings) = chooseBestCoupon(visibleText)

        if (!userCouponCode.isNullOrBlank()) {
            val opened = automation.clickTextOrDescriptionAnyOf(
                listOf("Apply coupon", "Coupon", "Offers", "Promo code", "Apply offer")
            ) || (couponText != null && automation.clickTextOrDescriptionAnyOf(listOf(couponText)))

            if (!opened) {
                return GroceryCouponResult(
                    provider = provider,
                    couponCode = userCouponCode,
                    applied = false,
                    found = false,
                    message = "I could not find a safe coupon field on ${provider.displayName()}.",
                    visibleCouponText = couponText,
                    savingsAmount = visibleSavings,
                    warning = "coupon field not found"
                )
            }

            val typed = automation.typeIntoFocusedField(userCouponCode)
            return GroceryCouponResult(
                provider = provider,
                couponCode = userCouponCode,
                applied = typed,
                found = true,
                message = if (typed) {
                    "I entered the coupon code on ${provider.displayName()}."
                } else {
                    "I found the coupon field on ${provider.displayName()}, but could not type the code."
                },
                visibleCouponText = couponText,
                savingsAmount = visibleSavings,
                warning = if (typed) null else "coupon entry failed"
            )
        }

        if (couponText == null) {
            return GroceryCouponResult(
                provider = provider,
                applied = false,
                found = false,
                message = "No visible coupon was found on ${provider.displayName()}.",
                warning = "no visible coupon"
            )
        }

        val clicked = automation.clickTextOrDescriptionAnyOf(
            listOf(couponText, "Apply coupon", "Coupon", "Offers", "Promo code", "Apply offer")
        )

        return GroceryCouponResult(
            provider = provider,
            applied = clicked,
            found = true,
            message = if (clicked) {
                "I applied the visible coupon on ${provider.displayName()}."
            } else {
                "I found a visible coupon on ${provider.displayName()}, but could not apply it safely."
            },
            visibleCouponText = couponText,
            savingsAmount = visibleSavings,
            warning = if (clicked) null else "coupon apply failed"
        )
    }

    private fun detectUnsafeCouponFlow(texts: List<String>): String? {
        val normalized = texts.joinToString(separator = " ").lowercase()
        return when {
            listOf("otp", "one time password").any { normalized.contains(it) } -> "OTP"
            listOf("payment", "pay now", "proceed to pay", "checkout", "place order", "complete payment").any { normalized.contains(it) } -> "payment"
            listOf("login", "sign in").any { normalized.contains(it) } -> "login"
            listOf("password").any { normalized.contains(it) } -> "password"
            listOf("cvv", "pin", "upi pin").any { normalized.contains(it) } -> "payment"
            listOf("captcha").any { normalized.contains(it) } -> "captcha"
            else -> null
        }
    }
}
