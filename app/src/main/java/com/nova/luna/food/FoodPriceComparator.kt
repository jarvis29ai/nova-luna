package com.nova.luna.food

import java.util.Locale

class FoodPriceComparator {
    private val currencyAmountPattern = Regex("""(?:\u20B9|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    private val bareAmountPattern = Regex("""(?<!\d)([0-9][0-9,]*(?:\.[0-9]+)?)(?!\d)""")
    private val etaRangePattern = Regex("""\b(\d+)\s*-\s*(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)
    private val etaPattern = Regex("""\b(?:eta|arrives?\s+in|in|around|about)?\s*(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

    fun normalize(quote: FoodPlatformQuote): FoodPlatformQuote {
        val visibleAmount = quote.visiblePriceAmount ?: extractAmount(quote.visiblePriceText)
        val normalizedFinalText = quote.finalPayableText ?: quote.visiblePriceText
        val finalAmount = quote.finalPayableAmount ?: extractAmount(normalizedFinalText)
        val deliveryFeeAmount = quote.deliveryFeeAmount ?: extractAmount(quote.deliveryFeeText)
        val taxAmount = quote.taxAmount ?: extractAmount(quote.taxText)
        val discountAmount = quote.discountAmount ?: extractAmount(quote.discountText)
        val etaMinutes = quote.etaMinutes ?: extractEtaMinutes(quote.etaText)

        return quote.copy(
            visiblePriceAmount = visibleAmount,
            finalPayableAmount = finalAmount,
            deliveryFeeAmount = deliveryFeeAmount,
            taxAmount = taxAmount,
            discountAmount = discountAmount,
            etaMinutes = etaMinutes
        )
    }

    fun sortLowestToHighest(quotes: List<FoodPlatformQuote>): List<FoodPlatformQuote> {
        return quotes
            .map { normalize(it) }
            .sortedWith(
                compareBy<FoodPlatformQuote> { it.finalPayableAmount ?: it.visiblePriceAmount ?: Long.MAX_VALUE }
                    .thenBy { it.etaMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.provider.name.lowercase(Locale.US) }
            )
    }

    fun extractAmount(text: String?): Long? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        val normalized = value.lowercase(Locale.US).replace(",", "")
        val currencyAmounts = currencyAmountPattern.findAll(normalized)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.toLong()
            }
            .toList()

        val values = if (currencyAmounts.isNotEmpty()) {
            currencyAmounts
        } else if (containsMoneyCue(normalized)) {
            bareAmountPattern.findAll(normalized)
                .mapNotNull { match ->
                    match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.toLong()
                }
                .toList()
        } else {
            emptyList()
        }

        if (values.isEmpty()) {
            return null
        }

        return when {
            isDiscountContext(normalized) && !isTotalContext(normalized) -> values.minOrNull()
            isTotalContext(normalized) -> values.maxOrNull() ?: values.first()
            else -> values.first()
        }
    }

    fun extractEtaMinutes(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        etaRangePattern.find(value)?.let { match ->
            val start = match.groupValues.getOrNull(1)?.toIntOrNull()
            val end = match.groupValues.getOrNull(2)?.toIntOrNull()
            if (start != null && end != null) {
                return maxOf(start, end)
            }
        }

        return etaPattern.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun containsMoneyCue(normalized: String): Boolean {
        return listOf(
            "total",
            "payable",
            "amount to pay",
            "to pay",
            "grand total",
            "subtotal",
            "discount",
            "coupon",
            "offer",
            "save",
            "cashback",
            "delivery",
            "fee",
            "tax",
            "gst",
            "service charge",
            "price",
            "final",
            "after",
            "payment"
        ).any { cue -> normalized.contains(cue) }
    }

    private fun isDiscountContext(normalized: String): Boolean {
        return listOf("discount", "coupon", "offer", "save", "cashback", "after", "now", "off").any {
            normalized.contains(it)
        }
    }

    private fun isTotalContext(normalized: String): Boolean {
        return listOf("total", "payable", "amount to pay", "to pay", "grand total", "subtotal", "final").any {
            normalized.contains(it)
        }
    }
}
