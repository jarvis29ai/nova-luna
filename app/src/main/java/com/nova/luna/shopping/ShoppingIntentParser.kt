package com.nova.luna.shopping

import java.util.Locale

class ShoppingIntentParser {

    fun parse(text: String): ShoppingRequest {
        val lowerText = text.lowercase(Locale.ROOT)
        
        val commandType = when {
            lowerText.contains("cancel") || lowerText.contains("stop") -> ShoppingCommandType.CANCEL
            lowerText.contains("buy") || lowerText.contains("order") || lowerText.contains("get") -> ShoppingCommandType.BUY_PRODUCT
            lowerText.contains("compare") -> ShoppingCommandType.COMPARE_PRODUCTS
            lowerText.contains("add to cart") -> ShoppingCommandType.ADD_TO_CART
            lowerText.contains("apply coupon") || lowerText.contains("use coupon") -> ShoppingCommandType.APPLY_COUPON
            lowerText.contains("confirm") || lowerText.contains("yes") -> ShoppingCommandType.CONFIRM_PURCHASE
            lowerText.contains("search") || lowerText.contains("find") -> ShoppingCommandType.SEARCH_PRODUCT
            lowerText.contains("choose") || lowerText.contains("select") || lowerText.contains("pick") -> ShoppingCommandType.OPEN_SELECTED_DEAL
            lowerText.contains("pay") || lowerText.contains("upi") || lowerText.contains("card") || lowerText.contains("wallet") || lowerText.contains("cod") -> ShoppingCommandType.ASK_PAYMENT_METHOD
            else -> ShoppingCommandType.SEARCH_PRODUCT
        }

        val category = when {
            lowerText.contains("phone") || lowerText.contains("mobile") -> ShoppingProductCategory.PHONE
            lowerText.contains("laptop") || lowerText.contains("computer") -> ShoppingProductCategory.LAPTOP
            lowerText.contains("headphones") || lowerText.contains("earphones") -> ShoppingProductCategory.HEADPHONES
            lowerText.contains("tv") || lowerText.contains("television") -> ShoppingProductCategory.TELEVISION
            lowerText.contains("watch") || lowerText.contains("smartwatch") -> ShoppingProductCategory.WATCH
            else -> ShoppingProductCategory.UNKNOWN
        }

        val budget = extractBudget(lowerText)
        val purpose = extractPurpose(lowerText)
        val brand = extractBrand(lowerText)
        val website = extractWebsite(lowerText)

        return ShoppingRequest(
            commandType = commandType,
            rawText = text,
            productName = extractProductName(lowerText, category),
            category = category,
            budget = budget,
            purpose = purpose,
            brand = brand,
            website = website,
            comparisonIntent = lowerText.contains("compare"),
            buyIntent = lowerText.contains("buy") || lowerText.contains("order")
        )
    }

    private fun extractBudget(text: String): Double? {
        val regex = Regex("""(?:under|below|for|around|budget|rs\.?|inr|₹)\s*(\d+(?:,\d+)*)""")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    private fun extractPurpose(text: String): ShoppingPurpose {
        return when {
            text.contains("gaming") -> ShoppingPurpose.GAMING
            text.contains("work") || text.contains("office") -> ShoppingPurpose.WORK
            text.contains("photo") || text.contains("camera") -> ShoppingPurpose.PHOTOGRAPHY
            text.contains("study") || text.contains("school") || text.contains("college") -> ShoppingPurpose.STUDY
            text.contains("battery") || text.contains("backup") -> ShoppingPurpose.BATTERY
            else -> ShoppingPurpose.UNKNOWN
        }
    }

    private fun extractBrand(text: String): String? {
        val brands = listOf("apple", "iphone", "samsung", "oneplus", "google", "pixel", "sony", "dell", "hp", "asus", "lenovo", "mi", "xiaomi", "realme", "oppo", "vivo")
        return brands.firstOrNull { text.contains(it) }
    }

    private fun extractWebsite(text: String): String? {
        val websites = listOf("amazon", "flipkart", "croma", "reliance", "official")
        return websites.firstOrNull { text.contains(it) }
    }

    private fun extractProductName(text: String, category: ShoppingProductCategory): String? {
        // Simple extraction for now
        if (category == ShoppingProductCategory.PHONE && text.contains("iphone")) return "iPhone"
        if (category == ShoppingProductCategory.PHONE && text.contains("samsung")) return "Samsung Phone"
        return null
    }
}
