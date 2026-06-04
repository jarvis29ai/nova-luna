package com.nova.luna.food

import java.util.Locale

class FoodCouponEngine(
    private val priceComparator: FoodPriceComparator = FoodPriceComparator()
) {
    fun extractCouponCandidates(visibleText: List<String>): List<FoodCouponCandidate> {
        if (visibleText.isEmpty()) return emptyList()

        val candidates = mutableListOf<FoodCouponCandidate>()
        visibleText.forEach { line ->
            val cleaned = line.trim()
            if (cleaned.isBlank()) return@forEach

            val normalized = cleaned.lowercase(Locale.US)
            val code = findCouponCode(cleaned)
            val hasCouponCue = couponCuePattern.containsMatchIn(normalized)
            if (!hasCouponCue && code == null && !looksLikeStandaloneCouponCode(cleaned)) return@forEach

            val amount = priceComparator.extractAmount(cleaned)
            candidates.add(
                FoodCouponCandidate(
                    code = code,
                    description = cleaned.takeIf { it != code },
                    savingsText = cleaned,
                    discountAmount = amount,
                    sourceText = line
                )
            )
        }

        return candidates.distinctBy { candidate ->
            listOfNotNull(candidate.code, candidate.savingsText, candidate.sourceText).joinToString("|")
        }
    }

    fun selectBestCoupon(
        candidates: List<FoodCouponCandidate>,
        preferredCode: String? = null
    ): FoodCouponCandidate? {
        if (candidates.isEmpty()) return null

        val normalizedPreference = preferredCode?.trim().orEmpty()
        if (normalizedPreference.equals("none", ignoreCase = true)) {
            return null
        }

        if (normalizedPreference.isNotBlank() &&
            normalizedPreference.lowercase(Locale.US) !in listOf("any", "best")
        ) {
            candidates.firstOrNull { candidate ->
                candidate.code?.equals(normalizedPreference, ignoreCase = true) == true ||
                    candidate.description?.contains(normalizedPreference, ignoreCase = true) == true ||
                    candidate.savingsText?.contains(normalizedPreference, ignoreCase = true) == true
            }?.let { return it }
        }

        return candidates.maxWithOrNull(
            compareBy<FoodCouponCandidate> { it.discountAmount ?: Long.MIN_VALUE }
                .thenByDescending { it.code?.length ?: 0 }
                .thenBy { it.code?.lowercase(Locale.US).orEmpty() }
        )
    }

    private fun findCouponCode(text: String): String? {
        val codePatterns = listOf(
            Regex("""(?i)\b(?:coupon|promo(?:\s*code)?|code|offer)\b(?:\s*(?:is|as|of|to|for|use|apply|named|called))?\s*([a-z0-9][a-z0-9_-]{3,})\b"""),
            Regex("""\b([A-Z][A-Z0-9_-]{3,})\b""")
        )
        val excludedTokens = setOf(
            "coupon",
            "coupons",
            "promo",
            "promos",
            "code",
            "offer",
            "offers",
            "deal",
            "deals",
            "discount",
            "discounts",
            "flat",
            "off",
            "save",
            "savings",
            "cashback",
            "free",
            "apply",
            "use"
        )

        codePatterns.forEach { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.let { code ->
                val normalized = code.trim()
                if (normalized.isNotBlank() &&
                    normalized.any(Char::isLetter) &&
                    normalized.lowercase(Locale.US) !in excludedTokens
                ) {
                    return normalized.uppercase(Locale.US)
                }
            }
        }

        return null
    }

    private fun looksLikeStandaloneCouponCode(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length !in 4..16) return false
        if (trimmed.any(Char::isWhitespace)) return false
        if (!trimmed.any(Char::isLetter)) return false

        val normalized = trimmed.uppercase(Locale.US)
        return Regex("""^[A-Z][A-Z0-9_-]{3,15}$""").matches(normalized) &&
            normalized !in excludedStandaloneTokens
    }

    private val couponCuePattern = Regex("""\b(coupon|promo|code|offer|discount|deal|save)\b""")
    private val excludedStandaloneTokens = setOf(
        "CART",
        "CHECKOUT",
        "PAY",
        "TOTAL",
        "ITEM",
        "ORDER",
        "LOGIN",
        "OTP",
        "CAPTCHA"
    )
}
