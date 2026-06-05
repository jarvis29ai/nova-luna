package com.nova.luna.grocery

import java.util.Locale

class GroceryPriceComparator {
    private val currencyBeforePattern = Regex(
        """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val currencyAfterPattern = Regex(
        """([0-9][0-9,]*(?:\.[0-9]+)?)\s*(?:rupees|rs\.?|inr)\b""",
        RegexOption.IGNORE_CASE
    )
    private val etaPattern = Regex("""\b(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

    fun normalize(candidate: GroceryCartCandidate): GroceryCartCandidate {
        val summary = candidate.summary
        val sourceText = summary.sourceText.orEmpty()

        val subtotal = summary.itemSubtotal ?: extractAmount(summary.itemSubtotal?.toString())
            ?: extractAmount(sourceText)
        val deliveryFee = summary.deliveryFee ?: extractAmount(summary.deliveryFee?.toString())
        val handlingFee = summary.handlingFee ?: extractAmount(summary.handlingFee?.toString())
        val couponDiscount = summary.couponDiscount
        val etaMinutes = summary.etaMinutes ?: extractEtaMinutes(summary.etaText ?: sourceText)
        val finalPayableValue = summary.finalPayableValue
            ?: calculateFinalPayable(subtotal, deliveryFee, handlingFee, couponDiscount)
            ?: extractAmount(summary.finalPayableValue?.toString())
            ?: extractAmount(sourceText)

        return candidate.copy(
            summary = summary.copy(
                itemSubtotal = subtotal,
                deliveryFee = deliveryFee,
                handlingFee = handlingFee,
                couponDiscount = couponDiscount,
                finalPayableValue = finalPayableValue,
                etaMinutes = etaMinutes
            )
        )
    }

    fun sortBestToWorst(candidates: List<GroceryCartCandidate>): List<GroceryCartCandidate> {
        return candidates
            .map { normalize(it) }
            .sortedWith(
                compareBy<GroceryCartCandidate> { it.summary.unavailableItems.isNotEmpty() }
                    .thenBy { rankingCost(it) }
                    .thenBy { it.summary.etaMinutes ?: Int.MAX_VALUE }
                    .thenBy { !it.summary.couponApplied }
                    .thenBy { it.provider.name.lowercase(Locale.US) }
            )
    }

    fun compare(candidates: List<GroceryCartCandidate>): GroceryComparisonResult {
        val sorted = sortBestToWorst(candidates)
        val completeCandidates = sorted.filter { it.summary.unavailableItems.isEmpty() }
        val cheapestCompleteCandidate = completeCandidates.minByOrNull { rankingCost(it) }
        val recommended = cheapestCompleteCandidate ?: sorted.firstOrNull()
        val fastestCandidate = sorted.minByOrNull { it.summary.etaMinutes ?: Int.MAX_VALUE }

        return GroceryComparisonResult(
            candidates = sorted,
            recommendedCandidate = recommended,
            cheapestCompleteCandidate = cheapestCompleteCandidate,
            fastestCandidate = fastestCandidate
        )
    }

    fun extractAmount(text: String?): Long? {
        val amounts = extractAmounts(text)
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

    fun extractAmounts(text: String?): List<Long> {
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

    private fun calculateFinalPayable(
        subtotal: Long?,
        deliveryFee: Long?,
        handlingFee: Long?,
        couponDiscount: Long?
    ): Long? {
        val values = listOfNotNull(subtotal, deliveryFee, handlingFee)
        if (values.isEmpty()) return null

        val total = values.sum() - (couponDiscount ?: 0L)
        return total.coerceAtLeast(0L)
    }

    private fun rankingCost(candidate: GroceryCartCandidate): Long {
        val summary = candidate.summary
        val base = summary.finalPayableValue
            ?: calculateFinalPayable(summary.itemSubtotal, summary.deliveryFee, summary.handlingFee, summary.couponDiscount)
            ?: Long.MAX_VALUE / 2

        val unavailablePenalty = if (summary.unavailableItems.isEmpty()) 0L else 1_000_000L
        return base + unavailablePenalty
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
            "after coupon",
            "after discount",
            "final",
            "payable",
            "total",
            "save",
            "offer",
            "promo",
            "discounted"
        ).any { normalized.contains(it) }
    }

    private fun isSavingsOnlyText(normalized: String): Boolean {
        if (!normalized.startsWith("save ")) return false
        val containsPriceWords = listOf(
            "total",
            "price",
            "pay",
            "amount",
            "bill",
            "final"
        ).any { normalized.contains(it) }
        return !containsPriceWords
    }
}
