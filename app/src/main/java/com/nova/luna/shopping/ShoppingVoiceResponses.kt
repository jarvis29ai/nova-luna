package com.nova.luna.shopping

class ShoppingVoiceResponses {
    fun prepareSummary(deals: List<ShoppingDealOption>): ShoppingVoiceResult {
        if (deals.isEmpty()) return ShoppingVoiceResult("I couldn't find any safe deals matching your criteria.", "No deals found.")
        
        val bestDeal = deals.first()
        val popup = "I found ${deals.size} good deals. The best overall is from ${bestDeal.product.provider} at ₹${bestDeal.finalPayablePrice}."
        val voice = "I found ${deals.size} deals. The top recommendation is a ${bestDeal.product.name} on ${bestDeal.product.provider} for ${bestDeal.finalPayablePrice.toInt()} rupees. Which one should I choose?"
        
        return ShoppingVoiceResult(popup, voice)
    }

    fun askFinalConfirmation(summary: ShoppingFinalOrderSummary): ShoppingVoiceResult {
        val popup = "Please confirm: buy ${summary.product.name} from ${summary.providerName} for ₹${summary.finalPrice}?"
        val voice = "Please confirm. Should I buy this ${summary.product.name} from ${summary.providerName} for ${summary.finalPrice.toInt()} rupees? Delivery is estimated by ${summary.deliveryDate}."
        return ShoppingVoiceResult(popup, voice)
    }

    data class ShoppingVoiceResult(val popupText: String, val voiceText: String)
}
