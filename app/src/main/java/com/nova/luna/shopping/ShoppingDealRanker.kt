package com.nova.luna.shopping

class ShoppingDealRanker {
    fun rank(results: List<ShoppingProductOption>, profile: ShoppingRequirementProfile): List<ShoppingDealOption> {
        return results.map { product ->
            val finalPrice = product.price // Simplified
            val totalSavings = (profile.budget ?: (product.price * 1.1)) - finalPrice
            val vfmScore = calculateVfmScore(product, profile)
            
            ShoppingDealOption(
                product = product,
                finalPayablePrice = finalPrice,
                totalSavings = totalSavings,
                valueForMoneyScore = vfmScore,
                rankingReason = "Matches your ${profile.purpose} needs with good rating."
            )
        }.sortedByDescending { it.valueForMoneyScore }
    }

    private fun calculateVfmScore(product: ShoppingProductOption, profile: ShoppingRequirementProfile): Double {
        var score = product.rating * 10
        if (product.deliveryDate == "Tomorrow") score += 5
        if (product.provider == ShoppingProvider.AMAZON) score += 2
        return score
    }
}
