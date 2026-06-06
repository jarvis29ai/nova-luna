package com.nova.luna.shopping

class ShoppingSearchEngine {
    fun search(profile: ShoppingRequirementProfile): List<ShoppingProductOption> {
        // Mock search results for now
        return listOf(
            ShoppingProductOption(
                id = "1",
                name = "${profile.preferredBrand ?: "Standard"} ${profile.category}",
                brand = profile.preferredBrand ?: "Generic",
                category = profile.category,
                price = (profile.budget ?: 20000.0) * 0.9,
                provider = ShoppingProvider.AMAZON,
                seller = "Appario Retail",
                rating = 4.5,
                reviewCount = 1200,
                deliveryDate = "Tomorrow",
                warranty = "1 Year",
                returnPolicy = "7 Days Replacement",
                sourceUrl = "https://amazon.in/p/1"
            ),
            ShoppingProductOption(
                id = "2",
                name = "${profile.preferredBrand ?: "Premium"} ${profile.category}",
                brand = profile.preferredBrand ?: "Generic",
                category = profile.category,
                price = (profile.budget ?: 20000.0) * 0.95,
                provider = ShoppingProvider.FLIPKART,
                seller = "RetailNet",
                rating = 4.2,
                reviewCount = 800,
                deliveryDate = "2 Days",
                warranty = "1 Year",
                returnPolicy = "10 Days Return",
                sourceUrl = "https://flipkart.com/p/2"
            )
        )
    }
}
