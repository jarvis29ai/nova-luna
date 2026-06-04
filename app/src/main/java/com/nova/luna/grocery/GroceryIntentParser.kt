package com.nova.luna.grocery

import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class GroceryIntentParser {
    private val groceryProviderPatterns = mapOf(
        GroceryProvider.BLINKIT to listOf("blinkit", "grofers"),
        GroceryProvider.JIOMART to listOf("jiomart", "jio mart"),
        GroceryProvider.INSTAMART to listOf("instamart", "swiggy instamart", "swiggy")
    )

    private val groceryItemKeywords = listOf(
        "sugar",
        "bread",
        "milk",
        "atta",
        "flour",
        "rice",
        "dal",
        "oil",
        "ghee",
        "butter",
        "curd",
        "curd",
        "eggs",
        "egg",
        "tea",
        "coffee",
        "biscuits",
        "snacks",
        "soap",
        "shampoo",
        "detergent",
        "fruits",
        "vegetables",
        "veg",
        "onions",
        "tomatoes",
        "potatoes"
    )

    private val foodDeliveryKeywords = listOf(
        "restaurant",
        "restaurants",
        "meal",
        "meals",
        "pizza",
        "burger",
        "biryani",
        "lunch",
        "dinner",
        "breakfast",
        "order food",
        "food delivery",
        "cuisine",
        "recipe"
    )

    private val groceryBrands = listOf(
        "amul",
        "britannia",
        "aashirvaad",
        "fortune",
        "patanjali",
        "mother dairy",
        "dabur",
        "parle",
        "nestle",
        "sunfeast",
        "maggi",
        "saffola",
        "mdh",
        "everest",
        "tata",
        "surf excel",
        "colgate",
        "vim",
        "borosil"
    )

    private val quantityPattern = Regex(
        """^(?:(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|l|litre|liter|ml|pack|packet|packets|bottle|bottles|piece|pieces|pcs|dozen)\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val quantitySuffixPattern = Regex(
        """^\s*(\d+(?:\.\d+)?)\s+(.+?)\s+(kg|g|gm|gram|grams|l|litre|liter|ml|pack|packet|packets|bottle|bottles|piece|pieces|pcs|dozen)\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parseInitialGroceryRequest(rawText: String): GroceryIntentParseResult? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        val groceryCueDetected = containsGroceryCue(normalized)
        if (containsFoodOrderingCue(normalized) && !containsGroceryCue(normalized)) {
            return null
        }

        val providerPreference = parseProviderPreference(normalized)
        val compareRequested = containsPhrase(normalized, "compare") ||
            containsPhrase(normalized, "compare prices") ||
            containsPhrase(normalized, "compare this basket")
        val wantsCheapest = containsPhrase(normalized, "cheapest") ||
            containsPhrase(normalized, "lowest price") ||
            containsPhrase(normalized, "best price")
        val wantsFirstOne = containsPhrase(normalized, "first one") ||
            containsPhrase(normalized, "first available")
        val applyCouponRequested = containsPhrase(normalized, "apply coupon") ||
            containsPhrase(normalized, "coupon")
        val couponCode = extractCouponCode(trimmed)
        val basket = parseBasket(trimmed)
        val requiresBrandQuestion = basket.items.isNotEmpty() && basket.items.any { it.brand.isNullOrBlank() }
        val requiresQuantityQuestion = false
        val hasGrocerySignal = groceryCueDetected ||
            providerPreference != null ||
            compareRequested ||
            wantsCheapest ||
            wantsFirstOne ||
            applyCouponRequested ||
            couponCode != null
        val isGroceryBooking = (basket.items.isNotEmpty() && hasGrocerySignal) ||
            providerPreference != null ||
            compareRequested ||
            wantsCheapest ||
            wantsFirstOne ||
            applyCouponRequested ||
            groceryCueDetected

        if (!isGroceryBooking) return null

        return GroceryIntentParseResult(
            rawText = rawText,
            isGroceryBooking = true,
            basket = basket,
            providerPreference = providerPreference,
            wantsCheapest = wantsCheapest,
            wantsFirstOne = wantsFirstOne,
            compareRequested = compareRequested,
            applyCouponRequested = applyCouponRequested,
            couponCode = couponCode,
            brandPreference = parseBrandPreference(normalized),
            requiresBrandQuestion = requiresBrandQuestion,
            requiresQuantityQuestion = requiresQuantityQuestion
        )
    }

    fun parseFollowUpCommand(rawText: String): GroceryFollowUpCommand? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        if (isCancel(normalized)) {
            return GroceryFollowUpCommand(rawText = rawText, type = GroceryFollowUpType.CANCEL)
        }

        if (isConfirm(normalized)) {
            return GroceryFollowUpCommand(rawText = rawText, type = GroceryFollowUpType.CONFIRM, finalUserConfirmed = true)
        }

        parseAddItem(normalized, trimmed)?.let { item ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.ADD_ITEM,
                item = item,
                itemQuery = item.displayText()
            )
        }

        parseRemoveItem(normalized, trimmed)?.let { query ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REMOVE_ITEM,
                itemQuery = query
            )
        }

        parseProviderPreference(normalized)?.let { provider ->
            val wantsCheapest = containsPhrase(normalized, "cheapest")
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.BOOK_PROVIDER,
                providerPreference = provider,
                wantsCheapest = wantsCheapest
            )
        }

        if (containsPhrase(normalized, "book cheapest") ||
            containsPhrase(normalized, "cheapest option") ||
            containsPhrase(normalized, "use the cheapest") ||
            containsPhrase(normalized, "cheapest platform")
        ) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.BOOK_CHEAPEST,
                wantsCheapest = true
            )
        }

        if (containsPhrase(normalized, "compare") || containsPhrase(normalized, "compare prices")) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.COMPARE,
                compareRequested = true
            )
        }

        extractCouponCode(trimmed)?.let { code ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.APPLY_COUPON,
                couponCode = code
            )
        }

        if (containsPhrase(normalized, "regular is fine") ||
            containsPhrase(normalized, "regular options") ||
            containsPhrase(normalized, "no brand") ||
            containsPhrase(normalized, "generic options") ||
            containsPhrase(normalized, "best matching regular options")
        ) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REGULAR,
                brandPreference = "regular"
            )
        }

        parseBrandPreference(normalized)?.let { brand ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.BRAND_PREFERENCE,
                brandPreference = brand
            )
        }

        return null
    }

    private fun parseBasket(rawText: String): GroceryBasket {
        val cleanedText = sanitizeBasketText(rawText)
        val segments = cleanedText
            .split(Regex("""\s*(?:,|&|\band\b|\bplus\b|\+)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val items = mutableListOf<GroceryItem>()
        for (segment in segments) {
            parseItem(segment)?.let { items.add(it) }
        }

        return GroceryBasket(items)
    }

    private fun sanitizeBasketText(rawText: String): String {
        var text = AssistantTextNormalizer.stripWakeWords(rawText).trim()
        if (text.isBlank()) return text

        val leadingPrefixes = listOf(
            "i want ",
            "i need ",
            "need ",
            "want ",
            "please ",
            "order ",
            "buy ",
            "book ",
            "compare ",
            "add ",
            "get "
        )

        val lower = text.lowercase(Locale.US)
        val prefix = leadingPrefixes.firstOrNull { lower.startsWith(it) }
        if (prefix != null) {
            text = text.substring(prefix.length).trimStart(',', ':', ' ')
        }

        val providerClauseMarkers = listOf(" on ", " from ", " via ")
        val marker = providerClauseMarkers.firstOrNull { lower.contains(it) }
        if (marker != null && containsGroceryCue(lower)) {
            text = text.substringBefore(marker, text).trim()
        }

        return text
    }

    private fun parseItem(segment: String): GroceryItem? {
        val cleaned = cleanSegment(segment)
        if (cleaned.isBlank()) return null
        if (containsFoodOrderingCue(normalize(cleaned)) && !containsGroceryCue(normalize(cleaned))) return null

        var quantityValue: Double? = null
        var unit: String? = null
        var remainder = cleaned

        quantitySuffixPattern.matchEntire(cleaned)?.let { match ->
            quantityValue = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            remainder = match.groupValues.getOrNull(2)?.trim().orEmpty()
            unit = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.lowercase(Locale.US)
        } ?: quantityPattern.matchEntire(cleaned)?.let { match ->
            quantityValue = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            unit = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.lowercase(Locale.US)
            remainder = match.groupValues.getOrNull(3)?.trim().orEmpty().ifBlank { cleaned }
        }

        val brand = extractBrand(remainder)
        if (brand != null) {
            remainder = remainder.removePrefix(brand).trim()
        }

        val itemName = remainder
            .trim()
            .trimStart('-', ':')
            .trim()
            .ifBlank { cleaned }

        return GroceryItem(
            name = itemName,
            quantityText = buildQuantityText(quantityValue, unit),
            quantityValue = quantityValue,
            unit = unit,
            brand = brand,
            rawText = cleaned
        )
    }

    private fun parseAddItem(normalized: String, rawText: String): GroceryItem? {
        val prefixes = listOf("add ", "add item ", "add grocery ", "add groceries ")
        val matched = prefixes.firstOrNull { normalized.startsWith(it) } ?: return null
        val lower = rawText.lowercase(Locale.US)
        val query = rawText.substring(lower.indexOf(matched) + matched.length).trim()
        return parseItem(query)
    }

    private fun parseRemoveItem(normalized: String, rawText: String): String? {
        val prefixes = listOf("remove ", "remove item ", "delete ", "drop ")
        val matched = prefixes.firstOrNull { normalized.startsWith(it) } ?: return null
        val lower = rawText.lowercase(Locale.US)
        val query = rawText.substring(lower.indexOf(matched) + matched.length).trim()
        return query.takeIf { it.isNotBlank() }
    }

    private fun parseProviderPreference(normalized: String): GroceryProvider? {
        groceryProviderPatterns.forEach { (provider, patterns) ->
            if (patterns.any { containsPhrase(normalized, it) }) {
                return provider
            }
        }
        return null
    }

    private fun parseBrandPreference(normalized: String): String? {
        if (containsPhrase(normalized, "regular is fine") ||
            containsPhrase(normalized, "no brand") ||
            containsPhrase(normalized, "generic options")
        ) {
            return "regular"
        }

        groceryBrands.firstOrNull { brand ->
            containsPhrase(normalized, brand)
        }?.let { return it }

        return null
    }

    private fun extractCouponCode(rawText: String): String? {
        val pattern = Regex("""\b(?:coupon|code|promo)\s*[:=]?\s*([A-Z0-9][A-Z0-9_-]{2,})\b""", RegexOption.IGNORE_CASE)
        pattern.find(rawText)?.groupValues?.getOrNull(1)?.let { return it.trim() }

        val standalone = Regex("""\b([A-Z]{3,}[A-Z0-9_-]*)\b""")
        standalone.find(rawText)?.groupValues?.getOrNull(1)?.let { token ->
            if (!containsFoodOrderingCue(normalize(rawText)) && containsGroceryCue(normalize(rawText))) {
                return token.trim()
            }
        }

        return null
    }

    private fun containsGroceryCue(normalized: String): Boolean {
        return groceryItemKeywords.any { containsPhrase(normalized, it) } ||
            groceryProviderPatterns.values.flatten().any { containsPhrase(normalized, it) } ||
            containsPhrase(normalized, "grocery") ||
            containsPhrase(normalized, "groceries") ||
            containsPhrase(normalized, "basket") ||
            containsPhrase(normalized, "cart") ||
            containsPhrase(normalized, "coupon") ||
            containsPhrase(normalized, "offers") ||
            containsPhrase(normalized, "milk") ||
            containsPhrase(normalized, "bread")
    }

    private fun containsFoodOrderingCue(normalized: String): Boolean {
        return foodDeliveryKeywords.any { containsPhrase(normalized, it) }
    }

    private fun cleanSegment(segment: String): String {
        return segment.trim()
            .trimEnd('.', ',', '!', '?')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildQuantityText(quantityValue: Double?, unit: String?): String? {
        if (quantityValue == null && unit.isNullOrBlank()) return null
        return buildString {
            quantityValue?.let {
                append(if (it % 1.0 == 0.0) it.toInt().toString() else it.toString())
                append(' ')
            }
            unit?.takeIf { it.isNotBlank() }?.let { append(it) }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun extractBrand(remainder: String): String? {
        val lower = normalize(remainder)
        groceryBrands
            .sortedByDescending { it.length }
            .firstOrNull { brand -> containsPhrase(lower, brand) }
            ?.let { matched ->
                val index = lower.indexOf(normalize(matched))
                if (index == 0 || index in 1..3) {
                    return matched.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    }
                }
            }

        val tokens = remainder.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size >= 2) {
            val first = tokens.first()
            val looksLikeBrand = first.firstOrNull()?.isUpperCase() == true &&
                first.lowercase(Locale.US) !in groceryItemKeywords
            if (looksLikeBrand) {
                return first
            }
        }

        return null
    }

    private fun isCancel(normalized: String): Boolean {
        return listOf(
            "cancel",
            "stop",
            "stop grocery",
            "cancel grocery",
            "never mind",
            "nevermind",
            "abort"
        ).any { containsPhrase(normalized, it) }
    }

    private fun isConfirm(normalized: String): Boolean {
        return listOf(
            "confirm",
            "yes",
            "yep",
            "yeah",
            "proceed",
            "proceed with this one",
            "book it",
            "okay",
            "ok",
            "sure"
        ).any { containsPhrase(normalized, it) }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
    }
}
