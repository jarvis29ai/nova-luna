package com.nova.luna.shopping

class ShoppingDealComparator {
    fun compare(deal1: ShoppingDealOption, deal2: ShoppingDealOption): String {
        return if (deal1.finalPayablePrice < deal2.finalPayablePrice) {
            "${deal1.product.provider} is cheaper than ${deal2.product.provider} by ₹${deal2.finalPayablePrice - deal1.finalPayablePrice}."
        } else {
            "${deal2.product.provider} is cheaper than ${deal1.product.provider} by ₹${deal1.finalPayablePrice - deal2.finalPayablePrice}."
        }
    }
}
