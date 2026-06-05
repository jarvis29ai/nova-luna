package com.nova.luna.cab

import java.util.Locale

class CabFareComparator {
    private data class RankedCabOption(
        val option: CabFareOption,
        val score: Int,
        val reasons: List<CabRankingReason>
    )

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

    fun extractTravelTimeMinutes(text: String?): Int? {
        return extractEtaMinutes(text)
    }

    fun rankTopOptions(
        options: List<CabFareOption>,
        profile: CabRequirementProfile? = null,
        skippedProviders: Map<CabProvider, String> = emptyMap(),
        providerFailures: Map<CabProvider, String> = emptyMap()
    ): CabComparisonResult {
        val normalized = options.map { normalize(it) }
        val minFare = normalized.mapNotNull { fareAmountOrNull(it) }.minOrNull()
        val minEta = normalized.mapNotNull { it.etaMinutes }.minOrNull()

        val ranked = normalized.map { option ->
            scoreOption(
                option = option,
                profile = profile,
                minFare = minFare,
                minEta = minEta
            )
        }.sortedWith(
            compareByDescending<RankedCabOption> { it.score }
                .thenBy { comparisonFareAmount(it.option) }
                .thenBy { it.option.etaMinutes ?: Int.MAX_VALUE }
                .thenBy { it.option.provider.name.lowercase(Locale.US) }
        )

        return CabComparisonResult(
            options = normalized,
            rankedTop3 = ranked.take(3).map { it.option },
            recommendedOption = ranked.firstOrNull()?.option,
            skippedProviders = skippedProviders,
            providerFailures = providerFailures,
            rankingReasons = ranked.associate { it.option.provider to it.reasons },
            userPreference = profile?.preference,
            comparisonNotes = buildList {
                profile?.preference?.let {
                    add("ranked by ${it.name.lowercase(Locale.US)} preference")
                }
                profile?.preferredProvider?.let {
                    add("preferred provider ${it.displayName()} was considered")
                }
            }
        )
    }

    private fun comparisonFareAmount(option: CabFareOption): Long {
        return option.finalFareAmount
            ?: option.originalFareAmount
            ?: option.visibleFareAmount
            ?: Long.MAX_VALUE
    }

    private fun fareAmountOrNull(option: CabFareOption): Long? {
        val amount = comparisonFareAmount(option)
        return amount.takeIf { it != Long.MAX_VALUE }
    }

    private fun scoreOption(
        option: CabFareOption,
        profile: CabRequirementProfile?,
        minFare: Long?,
        minEta: Int?
    ): RankedCabOption {
        val reasons = mutableListOf<CabRankingReason>()
        var score = 0

        val fare = fareAmountOrNull(option)
        if (fare == null) {
            score -= 250
            reasons.add(CabRankingReason("missing_fare", "fare not visible", -250))
        } else {
            score += 100
            val fareDelta = (fare - (minFare ?: fare)).coerceAtLeast(0)
            val fareBonus = (80 - fareDelta.coerceAtMost(80)).toInt()
            score += fareBonus
            if (fareDelta == 0L) {
                reasons.add(CabRankingReason("best_fare", "lowest fare among visible options", fareBonus))
            } else {
                reasons.add(CabRankingReason("fare_delta", "fare is ₹$fare", fareBonus))
            }
        }

        val eta = option.etaMinutes
        if (eta == null) {
            score -= 80
            reasons.add(CabRankingReason("missing_eta", "pickup ETA not visible", -80))
        } else {
            val etaDelta = (eta - (minEta ?: eta)).coerceAtLeast(0)
            val etaBonus = (60 - etaDelta.coerceAtMost(60)).toInt()
            score += etaBonus
            if (etaDelta == 0) {
                reasons.add(CabRankingReason("best_eta", "fastest pickup ETA among visible options", etaBonus))
            } else {
                reasons.add(CabRankingReason("eta_delta", "pickup ETA is ${eta} min", etaBonus))
            }
        }

        profile?.preferredProvider?.takeIf { option.provider == it }?.let {
            score += 70
            reasons.add(CabRankingReason("preferred_provider", "preferred provider selected", 70))
        }

        profile?.cabType?.takeIf { it != RideType.ANY }?.let { requestedType ->
            if (option.rideType == requestedType) {
                score += 50
                reasons.add(CabRankingReason("matching_ride_type", "matches requested ${requestedType.displayName()} ride", 50))
            } else {
                score -= 10
                reasons.add(CabRankingReason("ride_type_mismatch", "does not match requested ${requestedType.displayName()} ride", -10))
            }
        }

        when (profile?.preference) {
            CabRidePreference.CHEAPEST -> {
                fare?.let {
                    val cheapBonus = (100 - ((it - (minFare ?: it)).coerceAtLeast(0).coerceAtMost(100))).toInt()
                    score += cheapBonus
                    reasons.add(CabRankingReason("cheapest_preference", "prioritized for lowest fare", cheapBonus))
                }
            }
            CabRidePreference.FASTEST -> {
                eta?.let {
                    val fastBonus = (90 - ((it - (minEta ?: it)).coerceAtLeast(0).coerceAtMost(90))).toInt()
                    score += fastBonus
                    reasons.add(CabRankingReason("fastest_preference", "prioritized for shortest ETA", fastBonus))
                }
            }
            CabRidePreference.COMFORTABLE -> {
                val comfortBonus = comfortScore(option.rideType)
                score += comfortBonus
                reasons.add(CabRankingReason("comfortable_preference", "prioritized for more comfortable ride types", comfortBonus))
            }
            CabRidePreference.PROVIDER_SPECIFIC -> {
                profile.preferredProvider?.let {
                    score += 20
                    reasons.add(CabRankingReason("provider_specific", "provider-specific request", 20))
                }
            }
            CabRidePreference.UNKNOWN,
            null -> Unit
        }

        if (hasWarningText(option.visibleRawText) || hasWarningText(option.finalFareText) || hasWarningText(option.visibleFareText)) {
            score -= 40
            reasons.add(CabRankingReason("warning", "surge or cancellation warning visible", -40))
        }

        if (!option.visibleRawText.isNullOrBlank() && option.visibleFareText.isNullOrBlank() && option.finalFareText.isNullOrBlank()) {
            score -= 20
            reasons.add(CabRankingReason("partial_read", "fare information was only partially readable", -20))
        }

        return RankedCabOption(option = option, score = score, reasons = reasons)
    }

    private fun comfortScore(rideType: RideType): Int {
        return when (rideType) {
            RideType.SUV -> 40
            RideType.SEDAN -> 30
            RideType.MINI -> 15
            RideType.AUTO -> 5
            RideType.BIKE -> 0
            RideType.ANY -> 0
        }
    }

    private fun hasWarningText(text: String?): Boolean {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return false

        return listOf(
            "surge",
            "high demand",
            "peak",
            "cancellation",
            "cancel fee",
            "extra charge",
            "toll",
            "busy",
            "delay"
        ).any { normalized.contains(it) }
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
