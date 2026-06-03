package com.nova.luna.cab

import java.util.Locale

class CabFareComparator {
    private val farePattern = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    private val etaPattern = Regex("""\b(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

    fun normalize(option: CabFareOption): CabFareOption {
        val visibleAmount = option.visibleFareAmount ?: extractFareAmount(option.visibleFareText)
        val normalizedFinalText = option.finalFareText ?: option.visibleFareText
        val finalAmount = option.finalFareAmount ?: extractFareAmount(normalizedFinalText)
        val etaMinutes = option.etaMinutes ?: extractEtaMinutes(option.etaText)

        return option.copy(
            visibleFareAmount = visibleAmount,
            finalFareAmount = finalAmount,
            etaMinutes = etaMinutes
        )
    }

    fun sortLowestToHighest(options: List<CabFareOption>): List<CabFareOption> {
        return options
            .map { normalize(it) }
            .sortedWith(
                compareBy<CabFareOption> { it.finalFareAmount ?: it.visibleFareAmount ?: Long.MAX_VALUE }
                    .thenBy { it.etaMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.provider.name.lowercase(Locale.US) }
            )
    }

    fun extractFareAmount(text: String?): Long? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        val normalized = value.lowercase(Locale.US).replace(",", "")
        val amounts = farePattern.findAll(normalized)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.toLong()
            }
            .toList()

        if (amounts.isEmpty()) {
            return null
        }

        val discountMarkers = listOf("discount", "coupon", "after", "final", "now", "save")
        return if (discountMarkers.any { normalized.contains(it) } && amounts.size > 1) {
            amounts.minOrNull()
        } else {
            amounts.first()
        }
    }

    fun extractEtaMinutes(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        return etaPattern.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
