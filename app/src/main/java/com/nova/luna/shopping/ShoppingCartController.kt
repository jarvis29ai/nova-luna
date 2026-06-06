package com.nova.luna.shopping

class ShoppingCartController {
    fun addToCart(product: ShoppingProductOption): Boolean {
        // In a real app, this would use AccessibilityService to tap 'Add to Cart'
        // For now, we mock success
        return true
    }
}
