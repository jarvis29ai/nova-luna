package com.nova.luna.grocery

interface GroceryCouponAutomation {
    fun clickTextOrDescriptionAnyOf(candidates: List<String>): Boolean
    fun typeIntoFocusedField(text: String): Boolean
}

class GroceryCouponEngine {
    fun detectVisibleCouponText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase()
            normalized.contains("coupon") ||
                normalized.contains("offer") ||
                normalized.contains("promo") ||
                normalized.contains("discount") ||
                normalized.contains("apply")
        }
    }

    fun applyVisibleCoupon(
        provider: GroceryProvider,
        snapshot: GroceryScreenSnapshot?,
        automation: GroceryCouponAutomation,
        userCouponCode: String? = null
    ): GroceryCouponResult {
        val visibleText = snapshot?.visibleText.orEmpty()
        val couponText = detectVisibleCouponText(visibleText)

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
                    message = "I could not find a coupon field on ${provider.displayName()}."
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
                visibleCouponText = couponText
            )
        }

        if (couponText == null) {
            return GroceryCouponResult(
                provider = provider,
                applied = false,
                found = false,
                message = "No visible coupon was found on ${provider.displayName()}."
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
            visibleCouponText = couponText
        )
    }
}
