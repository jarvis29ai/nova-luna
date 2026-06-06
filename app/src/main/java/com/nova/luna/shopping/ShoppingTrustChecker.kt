package com.nova.luna.shopping

class ShoppingTrustChecker {
    fun check(url: String?, seller: String?): ShoppingWebsiteTrustResult {
        if (url == null) return ShoppingWebsiteTrustResult(ShoppingTrustLevel.CAUTION, listOf("No URL provided"))
        
        val isMarketplace = url.contains("amazon.in") || url.contains("flipkart.com") || 
                           url.contains("croma.com") || url.contains("reliancedigital.in")
        
        if (isMarketplace) {
            return ShoppingWebsiteTrustResult(ShoppingTrustLevel.SAFE, listOf("Trusted marketplace"))
        }

        if (!url.startsWith("https://")) {
            return ShoppingWebsiteTrustResult(ShoppingTrustLevel.RISKY, listOf("Insecure connection"), "This website uses an insecure connection.")
        }

        // Basic mock logic for risky domains
        if (url.contains("free-phones.com") || url.contains("scam-deal.in")) {
            return ShoppingWebsiteTrustResult(ShoppingTrustLevel.RISKY, listOf("Known scam domain"), "This website is flagged as risky.")
        }

        return ShoppingWebsiteTrustResult(ShoppingTrustLevel.SAFE, listOf("Basic checks passed"))
    }
}
