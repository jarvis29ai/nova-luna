package com.nova.luna.shopping

class ShoppingProductMatcher {
    fun isMatch(product: ShoppingProductOption, profile: ShoppingRequirementProfile): Boolean {
        if (profile.category != ShoppingProductCategory.UNKNOWN && product.category != profile.category) return false
        if (profile.budget != null && product.price > profile.budget * 1.2) return false
        return true
    }
}
