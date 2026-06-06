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
    private val packSizePattern = Regex(
        """\b(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|l|litre|liter|ml|pack|packet|packets|packs|bottle|bottles|piece|pieces|pcs|dozen)\b""",
        RegexOption.IGNORE_CASE
    )
    private val ratingPattern = Regex(
        """(?:rating|rated|score|★|⭐)?\s*([0-5](?:\.\d+)?)\s*(?:/5|out of 5|stars?)?""",
        RegexOption.IGNORE_CASE
    )

    fun normalize(candidate: GroceryCartCandidate): GroceryCartCandidate {
        val summary = candidate.summary
        val sourceText = buildString {
            summary.sourceText?.takeIf { it.isNotBlank() }?.let { append(it) }
            candidate.productOptions.joinToString(separator = " | ") { option ->
                buildString {
                    append(option.title)
                    option.priceText?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    option.packSizeText?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    option.brand?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    option.ratingText?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    option.deliveryTimeText?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                }
            }.takeIf { it.isNotBlank() }?.let {
                if (isNotBlank()) {
                    append(" | ")
                }
                append(it)
            }
        }

        val parsedPackSize = summary.packSizeText
            ?: candidate.productOptions.firstOrNull { !it.packSizeText.isNullOrBlank() }?.packSizeText
            ?: extractPackSize(sourceText)
        val parsedRatingText = summary.ratingText
            ?: candidate.productOptions.firstOrNull { !it.ratingText.isNullOrBlank() }?.ratingText
            ?: extractRatingText(sourceText)
        val parsedRatingValue = summary.ratingValue
            ?: candidate.productOptions.firstOrNull { it.ratingValue != null }?.ratingValue
            ?: extractRating(sourceText)

        val subtotal = summary.itemSubtotal ?: extractAmount(summary.sourceText)
        val deliveryFee = summary.deliveryFee ?: extractDeliveryFee(sourceText)
        val handlingFee = summary.handlingFee ?: extractAmount(extractContext(sourceText, listOf("handling", "platform")))
        val couponDiscount = summary.couponDiscount ?: extractCouponSaving(sourceText)
        val etaMinutes = summary.etaMinutes ?: extractEtaMinutes(summary.etaText ?: sourceText)
        val finalPayableValue = summary.finalPayableValue
            ?: extractFinalPayable(sourceText)
            ?: calculateFinalPayable(subtotal, deliveryFee, handlingFee, couponDiscount)

        return candidate.copy(
            summary = summary.copy(
                itemSubtotal = subtotal,
                deliveryFee = deliveryFee,
                handlingFee = handlingFee,
                couponDiscount = couponDiscount,
                finalPayableValue = finalPayableValue,
                etaText = summary.etaText ?: extractEtaText(sourceText),
                etaMinutes = etaMinutes,
                packSizeText = parsedPackSize,
                ratingText = parsedRatingText,
                ratingValue = parsedRatingValue,
                partial = summary.partial || finalPayableValue == null || candidate.productOptions.isEmpty(),
                blocked = summary.blocked || candidate.manualActionReason != null || candidate.providerResult?.blocked == true,
                blockReason = summary.blockReason ?: candidate.providerResult?.blockReason
            )
        )
    }

    fun sortBestToWorst(candidates: List<GroceryCartCandidate>): List<GroceryCartCandidate> {
        return compare(candidates).candidates
    }

    fun compare(
        candidates: List<GroceryCartCandidate>,
        requirementProfile: GroceryRequirementProfile? = null
    ): GroceryComparisonResult {
        if (candidates.isEmpty()) {
            return GroceryComparisonResult(candidates = emptyList())
        }

        val normalized = candidates.map { normalize(it) }
        val validCandidates = normalized.filterNot { isBlocked(it) }
        val rankable = if (validCandidates.isNotEmpty()) validCandidates else normalized

        val scored = rankable.map { scoreCandidate(it, requirementProfile) }
        val ranked = scored.sortedBy { it.overallScore }.map { it.candidate.copy(rankingReason = it.recommendedReason) }

        val allScores = normalized.associateBy({ it.provider }, { scoreCandidate(it, requirementProfile) })

        val cheapestCandidate = rankable.minByOrNull { scoreCandidate(it, requirementProfile).cheapScore }
        val fastestCandidate = rankable.minByOrNull { scoreCandidate(it, requirementProfile).fastScore }
        val bestQualityCandidate = rankable.minByOrNull { scoreCandidate(it, requirementProfile).qualityScore }
        val bestOverallCandidate = rankable.minByOrNull { scoreCandidate(it, requirementProfile).overallScore }
        val cheapestCompleteCandidate = rankable
            .filter { it.summary.unavailableItems.isEmpty() && !isBlocked(it) }
            .minByOrNull { scoreCandidate(it, requirementProfile).cheapScore }

        val recommendedCandidate = when {
            requirementProfile?.providerPreference != null -> {
                normalized.firstOrNull { it.provider == requirementProfile.providerPreference && !isBlocked(it) }
                    ?: requirementProfile.providerPreference.let { pref ->
                        normalized.firstOrNull { it.provider == pref }
                    }
            }
            requirementProfile?.budgetPreference == GroceryBudgetPreference.CHEAPEST -> cheapestCandidate
            requirementProfile?.budgetPreference == GroceryBudgetPreference.FAST_DELIVERY -> fastestCandidate
            requirementProfile?.budgetPreference == GroceryBudgetPreference.BEST_QUALITY -> bestQualityCandidate
            requirementProfile?.budgetPreference == GroceryBudgetPreference.BEST_OVERALL -> bestOverallCandidate
            else -> bestOverallCandidate ?: ranked.firstOrNull()
        }

        return GroceryComparisonResult(
            candidates = ranked,
            recommendedCandidate = recommendedCandidate,
            cheapestCompleteCandidate = cheapestCompleteCandidate,
            fastestCandidate = fastestCandidate,
            bestQualityCandidate = bestQualityCandidate,
            bestOverallCandidate = bestOverallCandidate,
            providerResults = normalized.map { candidate ->
                candidate.providerResult ?: GroceryProviderResult(
                    provider = candidate.provider,
                    productOptions = candidate.productOptions,
                    summary = candidate.summary,
                    blocked = isBlocked(candidate),
                    partial = candidate.summary.partial,
                    blockReason = candidate.summary.blockReason,
                    manualActionReason = candidate.manualActionReason,
                    searchQueries = candidate.searchQueries
                )
            },
            rankingReasons = ranked.mapNotNull { it.rankingReason }
        )
    }

    fun compare(candidates: List<GroceryCartCandidate>): GroceryComparisonResult {
        return compare(candidates, null)
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

    fun extractDeliveryFee(text: String?): Long? {
        val normalized = normalizeText(text)
        if (!containsAny(normalized, listOf("delivery", "shipping", "delivery fee", "delivery charges"))) return null
        return extractAmount(text)
    }

    fun extractFinalPayable(text: String?): Long? {
        val normalized = normalizeText(text)
        if (!containsAny(normalized, listOf("final", "payable", "grand total", "total", "amount to pay", "checkout", "pay"))) {
            return null
        }
        return extractAmount(text)
    }

    fun extractCouponSaving(text: String?): Long? {
        val normalized = normalizeText(text)
        if (!containsAny(normalized, listOf("save", "savings", "saved", "coupon", "discount", "offer", "promo"))) return null
        return extractAmount(text)
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

    fun extractPackSize(text: String?): String? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        return packSizePattern.find(value)
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun extractRating(text: String?): Double? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return null

        return ratingPattern.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.takeIf { it in 0.0..5.0 }
    }

    fun extractRatingText(text: String?): String? {
        val rating = extractRating(text)
        return rating?.let { "⭐ $it" }
    }

    fun extractEtaText(text: String?): String? {
        val minutes = extractEtaMinutes(text)
        return minutes?.let { "$it min" }
    }

    private data class CandidateScore(
        val candidate: GroceryCartCandidate,
        val cheapScore: Long,
        val fastScore: Long,
        val qualityScore: Long,
        val overallScore: Long,
        val recommendedReason: GroceryRankingReason
    )

    private fun scoreCandidate(
        candidate: GroceryCartCandidate,
        requirementProfile: GroceryRequirementProfile?
    ): CandidateScore {
        val summary = candidate.summary
        val blocked = isBlocked(candidate)
        val finalPrice = summary.finalPayableValue ?: summary.itemSubtotal
        val etaMinutes = summary.etaMinutes ?: Int.MAX_VALUE / 4
        val unavailablePenalty = (summary.unavailableItems.size + candidate.productOptions.count { !it.available }) * 75_000L
        val replacementPenalty = summary.replacementItems.size * 30_000L
        val partialPenalty = if (summary.partial) 100_000L else 0L
        val blockedPenalty = if (blocked) 5_000_000_000L else 0L
        val missingPricePenalty = if (finalPrice == null) 500_000L else 0L
        val missingEtaPenalty = if (summary.etaMinutes == null) 100_000L else 0L
        val couponBonus = summary.couponDiscount ?: 0L
        val providerBonus = if (requirementProfile?.providerPreference == candidate.provider) 30_000L else 0L
        val brandMatches = countBrandMatches(requirementProfile, candidate)
        val itemMatches = countItemMatches(requirementProfile, candidate)
        val quantityMatches = countQuantityMatches(requirementProfile, candidate)
        val ratingValue = summary.ratingValue ?: candidate.productOptions.maxOfOrNull { it.ratingValue ?: 0.0 } ?: 0.0
        val ratingPenalty = ((5.0 - ratingValue.coerceIn(0.0, 5.0)) * 100_000.0).toLong()
        val itemMismatchPenalty = requirementProfile?.items?.size?.minus(itemMatches)?.coerceAtLeast(0)?.times(25_000L) ?: 0L
        val brandMismatchPenalty = requirementProfile?.items
            ?.count { !it.preferredBrand.isNullOrBlank() && !candidateMatchesBrand(candidate, it.preferredBrand) }
            ?.times(30_000L)
            ?: 0L
        val quantityMismatchPenalty = requirementProfile?.items
            ?.count { !it.quantityText.isNullOrBlank() && !candidateMatchesQuantity(candidate, it.quantityText.orEmpty(), it.unit) }
            ?.times(10_000L)
            ?: 0L

        val cheapScore = (finalPrice ?: Long.MAX_VALUE / 8) +
            unavailablePenalty +
            replacementPenalty +
            partialPenalty +
            blockedPenalty +
            missingPricePenalty -
            couponBonus

        val fastScore = (etaMinutes.toLong() * 1_000L) +
            unavailablePenalty +
            replacementPenalty +
            partialPenalty +
            blockedPenalty +
            missingEtaPenalty +
            ((finalPrice ?: 0L) / 10L)

        val qualityScore = ratingPenalty +
            itemMismatchPenalty +
            brandMismatchPenalty +
            quantityMismatchPenalty +
            unavailablePenalty +
            replacementPenalty +
            partialPenalty +
            blockedPenalty +
            missingPricePenalty -
            providerBonus -
            (brandMatches * 15_000L) -
            (itemMatches * 10_000L) -
            (quantityMatches * 5_000L)

        val overallScore = cheapScore +
            (fastScore / 2L) +
            (qualityScore / 2L) +
            missingEtaPenalty +
            missingPricePenalty -
            providerBonus -
            couponBonus

        val details = buildList {
            add("₹${finalPrice ?: "?"}")
            if (!summary.etaText.isNullOrBlank() || summary.etaMinutes != null) {
                add("ETA ${summary.etaText ?: "${summary.etaMinutes} min"}")
            }
            if (summary.couponDiscount != null) {
                add("coupon -₹${summary.couponDiscount}")
            }
            if (brandMatches > 0) {
                add("$brandMatches brand matches")
            }
            if (itemMatches > 0) {
                add("$itemMatches item matches")
            }
            if (quantityMatches > 0) {
                add("$quantityMatches quantity matches")
            }
            if (summary.unavailableItems.isNotEmpty()) {
                add("unavailable ${summary.unavailableItems.joinToString(separator = ", ")}")
            }
            if (summary.replacementItems.isNotEmpty()) {
                add("replacements ${summary.replacementItems.joinToString(separator = ", ")}")
            }
        }

        val warnings = buildList {
            if (blocked) add("provider blocked")
            if (summary.partial) add("partial cart")
            if (summary.finalPayableValue == null && summary.itemSubtotal == null) add("missing price")
            if (summary.etaMinutes == null && summary.etaText.isNullOrBlank()) add("missing ETA")
        }

        val recommendation = when {
            blocked -> "Blocked provider kept behind readable carts."
            requirementProfile?.budgetPreference == GroceryBudgetPreference.CHEAPEST -> "Cheapest cart is preferred."
            requirementProfile?.budgetPreference == GroceryBudgetPreference.FAST_DELIVERY -> "Fastest cart is preferred."
            requirementProfile?.budgetPreference == GroceryBudgetPreference.BEST_QUALITY -> "Highest quality cart is preferred."
            requirementProfile?.budgetPreference == GroceryBudgetPreference.BEST_OVERALL -> "Best overall cart is preferred."
            else -> "Balanced score used for recommendation."
        }

        return CandidateScore(
            candidate = candidate.copy(
                rankingReason = GroceryRankingReason(
                    category = when {
                        requirementProfile?.budgetPreference == GroceryBudgetPreference.CHEAPEST -> GroceryRankingCategory.CHEAPEST
                        requirementProfile?.budgetPreference == GroceryBudgetPreference.FAST_DELIVERY -> GroceryRankingCategory.FASTEST
                        requirementProfile?.budgetPreference == GroceryBudgetPreference.BEST_QUALITY -> GroceryRankingCategory.BEST_QUALITY
                        requirementProfile?.budgetPreference == GroceryBudgetPreference.BEST_OVERALL -> GroceryRankingCategory.BEST_OVERALL
                        else -> GroceryRankingCategory.RECOMMENDED
                    },
                    summary = recommendation,
                    details = details,
                    warnings = warnings,
                    score = overallScore.toDouble()
                )
            ),
            cheapScore = cheapScore,
            fastScore = fastScore,
            qualityScore = qualityScore,
            overallScore = overallScore,
            recommendedReason = GroceryRankingReason(
                category = GroceryRankingCategory.RECOMMENDED,
                summary = recommendation,
                details = details,
                warnings = warnings,
                score = overallScore.toDouble()
            )
        )
    }

    private fun candidateMatchesBrand(candidate: GroceryCartCandidate, brand: String): Boolean {
        val normalizedBrand = brand.lowercase(Locale.US).trim()
        return candidate.productOptions.any { option ->
            option.brand?.lowercase(Locale.US)?.contains(normalizedBrand) == true ||
                option.title.lowercase(Locale.US).contains(normalizedBrand)
        } || candidate.summary.sourceText.orEmpty().lowercase(Locale.US).contains(normalizedBrand)
    }

    private fun candidateMatchesQuantity(candidate: GroceryCartCandidate, quantityText: String, unit: String?): Boolean {
        val normalizedQuantity = buildString {
            append(quantityText.lowercase(Locale.US).trim())
            unit?.takeIf { it.isNotBlank() }?.let {
                append(' ')
                append(it.lowercase(Locale.US).trim())
            }
        }.trim()

        return candidate.productOptions.any { option ->
            val pack = option.packSizeText?.lowercase(Locale.US).orEmpty()
            pack.contains(normalizedQuantity) || option.title.lowercase(Locale.US).contains(normalizedQuantity)
        } || candidate.summary.packSizeText?.lowercase(Locale.US)?.contains(normalizedQuantity) == true
    }

    private fun countBrandMatches(profile: GroceryRequirementProfile?, candidate: GroceryCartCandidate): Int {
        if (profile == null) return 0
        return profile.items.count { item ->
            !item.preferredBrand.isNullOrBlank() && candidateMatchesBrand(candidate, item.preferredBrand!!)
        }
    }

    private fun countItemMatches(profile: GroceryRequirementProfile?, candidate: GroceryCartCandidate): Int {
        if (profile == null) return 0
        return profile.items.count { item ->
            candidate.productOptions.any { option ->
                option.itemName.contains(item.name, ignoreCase = true) ||
                    option.title.contains(item.name, ignoreCase = true) ||
                    candidate.summary.sourceText.orEmpty().contains(item.name, ignoreCase = true)
            } || candidate.summary.sourceText.orEmpty().contains(item.name, ignoreCase = true)
        }
    }

    private fun countQuantityMatches(profile: GroceryRequirementProfile?, candidate: GroceryCartCandidate): Int {
        if (profile == null) return 0
        return profile.items.count { item ->
            !item.quantityText.isNullOrBlank() && candidateMatchesQuantity(candidate, item.quantityText, item.unit)
        }
    }

    private fun isBlocked(candidate: GroceryCartCandidate): Boolean {
        return candidate.summary.blocked || candidate.manualActionReason != null || candidate.providerResult?.blocked == true
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

    private fun normalizeText(text: String?): String {
        return text.orEmpty()
            .lowercase(Locale.US)
            .replace(",", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractContext(text: String?, keywords: List<String>): String? {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return null

        val index = keywords.firstNotNullOfOrNull { keyword ->
            val matchIndex = normalized.indexOf(keyword.lowercase(Locale.US))
            if (matchIndex >= 0) matchIndex else null
        } ?: return text

        return normalized.substring(index)
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

    private fun containsAny(normalized: String, keywords: List<String>): Boolean {
        return keywords.any { normalized.contains(it.lowercase(Locale.US)) }
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
