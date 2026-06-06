package com.nova.luna.shopping

class ShoppingCouponEngine {
    fun findBestCoupon(product: ShoppingProductOption): String? {
        return product.couponOffers.firstOrNull()
    }

    fun calculateDiscount(product: ShoppingProductOption, coupon: String): Double {
        return 0.0 // Placeholder
    }
}
