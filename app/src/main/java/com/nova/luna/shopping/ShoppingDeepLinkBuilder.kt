package com.nova.luna.shopping

import android.content.Context
import android.net.Uri

class ShoppingDeepLinkBuilder(private val context: Context) {
    fun buildSearchIntent(provider: ShoppingProvider, query: String): Uri? {
        return when (provider) {
            ShoppingProvider.AMAZON -> Uri.parse("https://www.amazon.in/s?k=${Uri.encode(query)}")
            ShoppingProvider.FLIPKART -> Uri.parse("https://www.flipkart.com/search?q=${Uri.encode(query)}")
            else -> Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        }
    }
}
