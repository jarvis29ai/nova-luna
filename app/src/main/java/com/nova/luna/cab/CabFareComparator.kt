package com.nova.luna.cab

import java.util.Locale

class CabFareComparator {
    private val currencyBeforePattern = Regex(
        """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val currencyAfterPattern = Regex(
        """([0-9][0-9,]*(?:\.[0-9]+)?)\s*(?:rupees|rs\.?|inr)\b""",
        RegexOption.IGNORE_CASE
    )
    private val etaPattern = Regex("""\b(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

    fun normalize(option: CabFareOption): CabFareOption {
        val visibleSourceText = option.visibleFareText ?: option.visibleRawText
        val finalSourceText = option.finalFareText ?: option.visibleRawText ?: option.visibleFareText

        val visibleAmount = option.visibleFareAmount ?: extractFareAmount(visibleSourceText)
        val originalAmount = option.originalFareAmount ?: visibleAmount
        val finalAmount = option.finalFareAmount ?: extractFareAmount(finalSourceText)
        val etaMinutes = option.etaMinutes ?: extractEtaMinutes(option.etaText)

        return option.copy(
            visibleFareAmount = visibleAmount,
            originalFareAmount = originalAmount,
            finalFareAmount = finalAmount,
            etaMinutes = etaMinutes
        )
    }

    fun sortLowestToHighest(options: List<CabFareOption>): List<CabFareOption> {
        return options
            .map { normalize(it) }
            .sortedWith(
                compareBy<CabFareOption> { comparisonFareAmount(it) == Long.MAX_VALUE }
                    .thenBy { comparisonFareAmount(it) }
                    .thenBy { it.etaMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.provider.name.lowercase(Locale.US) }
            )
    }

    fun extractFareAmount(text: String?): Long? {
        val amounts = extractFareAmounts(text)
        if (amounts.isEmpty()) return null

        val normalized = normalizeText(text)
        if (amounts.size == 1) {
            if (isSavingsOnlyText(normalized)) return null
            return amounts.first()
        }

        return if (hasDiscountContext(normalized)) {
            amounts.minOrNull()
        } else {
            amounts.first()
        }
    }

    fun extractFareAmounts(text: String?): List<Long> {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return emptyList()

        val seen = LinkedHashSet<Long>()
        currencyBeforePattern.findAll(normalized).forEach { match ->
            match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.toLong()?.let(seen::add)
        }
        currencyAfterPattern.findAll(normalized).forEach { match ->
            match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.toLong()?.let(seen::add)
        }

        return seen.toList()
    }

    fun extractEtaMinutes(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        return etaPattern.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun comparisonFareAmount(option: CabFareOption): Long {
        return option.finalFareAmount
            ?: option.originalFareAmount
            ?: option.visibleFareAmount
            ?: Long.MAX_VALUE
    }

    private fun normalizeText(text: String?): String {
        return text.orEmpty()
            .lowercase(Locale.US)
            .replace(",", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun hasDiscountContext(normalized: String): Boolean {
        return listOf(
            "discount",
            "coupon",
            "coupon applied",
            "after coupon",
            "after discount",
            "after",
            "final",
            "now",
            "save",
            "offer",
            "promo",
            "off",
            "payable",
            "final fare",
            "net fare",
            "total fare",
            "new fare",
            "discounted"
        ).any { normalized.contains(it) }
    }

    private fun isSavingsOnlyText(normalized: String): Boolean {
        if (!normalized.startsWith("save ")) return false
        val containsFareWords = listOf(
            "fare",
            "ride",
            "trip",
            "price",
            "cost",
            "pay",
            "total",
            "estimate",
            "estimated",
            "book",
            "now",
            "after"
        ).any { normalized.contains(it) }
        return !containsFareWords
    }
}
