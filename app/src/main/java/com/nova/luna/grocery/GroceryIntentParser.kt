package com.nova.luna.grocery

import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class GroceryIntentParser {
    private val groceryProviderPatterns = mapOf(
        GroceryProvider.BLINKIT to listOf("blinkit", "grofers"),
        GroceryProvider.ZEPTO to listOf("zepto"),
        GroceryProvider.INSTAMART to listOf("instamart", "swiggy instamart", "swiggy"),
        GroceryProvider.JIOMART to listOf("jiomart", "jio mart"),
        GroceryProvider.BIGBASKET to listOf("bigbasket", "big basket")
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
        "yogurt",
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
        "potatoes",
        "salt",
        "spices",
        "paneer",
        "cheese",
        "water",
        "juice",
        "curd",
        "batter"
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
        "borosil",
        "epigamia",
        "lays",
        "kellogg",
        "brooke bond",
        "tetley",
        "fresho",
        "haldiram",
        "mtr"
    )

    private val quantityPattern = Regex(
        """^(?:(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|l|litre|liter|ml|pack|packet|packets|packs|bottle|bottles|piece|pieces|pcs|dozen)\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val quantitySuffixPattern = Regex(
        """^\s*(\d+(?:\.\d+)?)\s+(.+?)\s+(kg|g|gm|gram|grams|l|litre|liter|ml|pack|packet|packets|packs|bottle|bottles|piece|pieces|pcs|dozen)\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parseInitialGroceryRequest(rawText: String): GroceryIntentParseResult? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        if (looksLikeShoppingRequest(normalized)) {
            return null
        }
        if (
            (normalized.startsWith("what ") ||
                normalized.startsWith("how ") ||
                normalized.startsWith("why ") ||
                normalized.startsWith("when ") ||
                normalized.startsWith("where ")) &&
            groceryItemKeywords.none { containsPhrase(normalized, it) } &&
            parseProviderPreference(normalized) == null &&
            parseBudgetPreference(normalized) == null &&
            parseDeliveryUrgency(normalized) == null &&
            parsePaymentPreference(normalized) == null &&
            !containsAnyPhrase(normalized, listOf("grocery", "groceries", "basket", "cart", "compare", "reorder", "coupon"))
        ) {
            return null
        }
        if (isInformationalGroceryInquiry(normalized)) {
            return null
        }
        if (isCommunicationOrContactRequest(normalized)) {
            return null
        }
        if (isNavigationOrSystemRequest(normalized)) {
            return null
        }
        val restaurantName = extractRestaurantName(trimmed)
        if (
            containsFoodOrderingCue(normalized) &&
            (restaurantName != null || containsAnyPhrase(normalized, listOf("pizza", "burger", "biryani", "meal", "lunch", "dinner", "breakfast", "recipe", "cuisine")))
        ) {
            return null
        }
        val groceryCueDetected = containsGroceryCue(normalized)
        if (containsFoodOrderingCue(normalized) && !groceryCueDetected) {
            return null
        }

        val providerPreference = parseProviderPreference(normalized)
        val compareRequested = containsPhrase(normalized, "compare") ||
            containsPhrase(normalized, "compare prices") ||
            containsPhrase(normalized, "compare this basket") ||
            containsPhrase(normalized, "compare grocery apps") ||
            containsPhrase(normalized, "compare blinkit")
        val wantsCheapest = containsAnyPhrase(normalized, listOf("cheapest", "lowest price", "best price", "cheaper"))
        val wantsFirstOne = containsAnyPhrase(normalized, listOf("first one", "first available", "choose first"))
        val budgetPreference = parseBudgetPreference(normalized)
        val deliveryUrgency = parseDeliveryUrgency(normalized)
        val paymentPreference = parsePaymentPreference(normalized)
        val reorderMode = containsAnyPhrase(
            normalized,
            listOf(
                "reorder",
                "buy the same groceries again",
                "same groceries again",
                "same order again",
                "repeat the same groceries",
                "buy from my previous list",
                "reorder previous grocery list",
                "my usual groceries",
                "usual groceries",
                "usual grocery list",
                "previous list"
            )
        )
        val previousListMode = reorderMode || containsAnyPhrase(
            normalized,
            listOf(
                "previous list",
                "previous grocery list",
                "past grocery list",
                "same groceries again",
                "usual groceries"
            )
        )
        val applyCouponRequested = containsAnyPhrase(normalized, listOf("apply coupon", "coupon", "promo", "offer"))
        val couponCode = extractCouponCode(trimmed)
        val basket = parseBasket(trimmed)
        val deliveryLocation = extractDeliveryLocation(trimmed)
        val useCurrentLocation = containsAnyPhrase(normalized, listOf("current location", "current address", "use current location"))
        val allowComparison = compareRequested || providerPreference == null || countProviderMentions(normalized) > 1
        val parsedBrandPreference = parseBrandPreference(normalized)
        val brandPreference = parsedBrandPreference ?: if (basket.items.isNotEmpty()) "regular" else null

        val requiresBrandQuestion = basket.items.isNotEmpty() &&
            basket.items.any { it.brand.isNullOrBlank() } &&
            parsedBrandPreference == null &&
            brandPreference == null
        val requiresQuantityQuestion = basket.items.any { it.quantityText.isNullOrBlank() }
        val requiresBudgetQuestion = basket.items.isNotEmpty() && budgetPreference == null
        val requiresDeliveryUrgencyQuestion = basket.items.isNotEmpty() && deliveryUrgency == null
        val requiresLocationQuestion = basket.items.isNotEmpty() &&
            deliveryLocation.isNullOrBlank() &&
            !useCurrentLocation &&
            !compareRequested

        val isGroceryBooking = basket.items.isNotEmpty() ||
            providerPreference != null ||
            compareRequested ||
            wantsCheapest ||
            wantsFirstOne ||
            budgetPreference != null ||
            deliveryUrgency != null ||
            paymentPreference != null ||
            reorderMode ||
            previousListMode ||
            applyCouponRequested ||
            couponCode != null ||
            groceryCueDetected

        if (!isGroceryBooking) return null

        val requirementProfile = GroceryRequirementProfile(
            items = basket.items.map { it.toItemRequest() },
            budgetPreference = budgetPreference ?: GroceryBudgetPreference.BEST_OVERALL,
            providerPreference = providerPreference,
            deliveryUrgency = deliveryUrgency ?: GroceryDeliveryUrgency.TODAY,
            scheduledTime = null,
            deliveryLocation = deliveryLocation,
            useCurrentLocation = useCurrentLocation,
            allowComparison = allowComparison,
            reorderMode = reorderMode,
            previousListMode = previousListMode,
            requiresFinalConfirmation = true,
            paymentPreference = paymentPreference,
            safetyNotes = null,
            allowSubstitutions = true
        )

        return GroceryIntentParseResult(
            rawText = rawText,
            isGroceryBooking = true,
            basket = basket,
            requirementProfile = requirementProfile,
            providerPreference = providerPreference,
            budgetPreference = budgetPreference,
            deliveryUrgency = deliveryUrgency,
            deliveryLocation = deliveryLocation,
            useCurrentLocation = useCurrentLocation,
            paymentPreference = paymentPreference,
            allowComparison = allowComparison,
            reorderMode = reorderMode,
            previousListMode = previousListMode,
            wantsCheapest = wantsCheapest || budgetPreference == GroceryBudgetPreference.CHEAPEST,
            wantsFirstOne = wantsFirstOne,
            compareRequested = compareRequested,
            applyCouponRequested = applyCouponRequested,
            couponCode = couponCode,
            brandPreference = brandPreference,
            requiresBrandQuestion = requiresBrandQuestion,
            requiresQuantityQuestion = requiresQuantityQuestion,
            requiresBudgetQuestion = requiresBudgetQuestion,
            requiresDeliveryUrgencyQuestion = requiresDeliveryUrgencyQuestion,
            requiresLocationQuestion = requiresLocationQuestion
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
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.CONFIRM,
                finalUserConfirmed = true
            )
        }

        if (containsAnyPhrase(normalized, listOf("search again", "try again", "search once more", "compare again"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.SEARCH_AGAIN,
                searchAgainRequested = true,
                compareRequested = containsPhrase(normalized, "compare")
            )
        }

        if (containsAnyPhrase(normalized, listOf("change item", "change brand", "change quantity", "change budget", "change delivery time", "change provider", "change location"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REFINEMENT,
                refineTarget = when {
                    containsPhrase(normalized, "change item") -> GroceryRefineTarget.ITEM
                    containsPhrase(normalized, "change brand") -> GroceryRefineTarget.BRAND
                    containsPhrase(normalized, "change quantity") -> GroceryRefineTarget.QUANTITY
                    containsPhrase(normalized, "change budget") -> GroceryRefineTarget.BUDGET
                    containsPhrase(normalized, "change delivery time") -> GroceryRefineTarget.DELIVERY_TIME
                    containsPhrase(normalized, "change provider") -> GroceryRefineTarget.PROVIDER
                    containsPhrase(normalized, "change location") -> GroceryRefineTarget.LOCATION
                    else -> GroceryRefineTarget.SEARCH
                }
            )
        }

        if (containsAnyPhrase(normalized, listOf("replace item", "similar item", "same brand", "cheaper substitute", "better quality", "fastest available substitute"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REPLACEMENT,
                replacementRequested = true
            )
        }

        if (containsAnyPhrase(normalized, listOf("remove item", "skip item", "remove it", "no replacement", "do not replace", "don't replace"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REMOVE_ITEM,
                removeRequested = true
            )
        }

        if (containsAnyPhrase(normalized, listOf("compare", "compare prices", "compare apps", "show comparison"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.COMPARE,
                compareRequested = true
            )
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
                itemQuery = query,
                removeRequested = true
            )
        }

        parsePaymentPreference(normalized)?.let { paymentPreference ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.SELECTION,
                paymentPreference = paymentPreference
            )
        }

        parseSelectionPreference(normalized)?.let { selection ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.SELECTION,
                selectionIndex = selection.first,
                selectionPreference = selection.second,
                wantsCheapest = selection.second == GrocerySelectionPreference.CHEAPEST,
                wantsFastest = selection.second == GrocerySelectionPreference.FASTEST,
                wantsBestQuality = selection.second == GrocerySelectionPreference.BEST_QUALITY,
                wantsBestOverall = selection.second == GrocerySelectionPreference.BEST_OVERALL,
                wantsFirstOne = selection.second == GrocerySelectionPreference.FIRST
            )
        }

        parseProviderPreference(normalized)?.let { provider ->
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.BOOK_PROVIDER,
                providerPreference = provider,
                wantsCheapest = containsPhrase(normalized, "cheapest"),
                wantsFastest = containsPhrase(normalized, "fastest")
            )
        }

        if (containsAnyPhrase(normalized, listOf("book cheapest", "cheapest option", "use the cheapest", "cheapest platform"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.BOOK_CHEAPEST,
                wantsCheapest = true,
                selectionPreference = GrocerySelectionPreference.CHEAPEST
            )
        }

        if (containsAnyPhrase(normalized, listOf("first one", "second one", "third one", "first available", "choose first"))) {
            val selection = parseSelectionPreference(normalized)
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.SELECTION,
                selectionIndex = selection?.first,
                selectionPreference = selection?.second ?: GrocerySelectionPreference.FIRST,
                wantsFirstOne = true
            )
        }

        if (containsAnyPhrase(normalized, listOf("regular is fine", "regular options", "no brand", "generic options", "best matching regular options"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.BRAND_PREFERENCE,
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

        if (containsAnyPhrase(normalized, listOf("reorder previous grocery list", "buy from my previous list", "my usual groceries", "usual groceries"))) {
            return GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REORDER,
                reorderRequested = true
            )
        }

        return null
    }

    fun parseProviderPreference(rawText: String): GroceryProvider? {
        val normalized = normalize(rawText)
        groceryProviderPatterns.forEach { (provider, patterns) ->
            if (patterns.any { containsPhrase(normalized, it) }) {
                return provider
            }
        }
        return null
    }

    fun parseBudgetPreference(rawText: String): GroceryBudgetPreference? {
        val normalized = normalize(rawText)
        return when {
            containsAnyPhrase(normalized, listOf("cheapest", "cheaper", "lowest price", "low price", "budget")) -> GroceryBudgetPreference.CHEAPEST
            containsAnyPhrase(normalized, listOf("best quality", "best rated", "top quality", "best brand", "quality first")) -> GroceryBudgetPreference.BEST_QUALITY
            containsAnyPhrase(normalized, listOf("fastest", "fast delivery", "quick delivery", "asap", "urgent")) -> GroceryBudgetPreference.FAST_DELIVERY
            containsAnyPhrase(normalized, listOf("best overall", "overall best", "best option", "balanced")) -> GroceryBudgetPreference.BEST_OVERALL
            else -> null
        }
    }

    fun parseDeliveryUrgency(rawText: String): GroceryDeliveryUrgency? {
        val normalized = normalize(rawText)
        return when {
            containsAnyPhrase(normalized, listOf("now", "asap", "right away", "urgent", "immediately")) -> GroceryDeliveryUrgency.NOW
            containsAnyPhrase(normalized, listOf("today", "tonight", "this evening", "later today")) -> GroceryDeliveryUrgency.TODAY
            containsAnyPhrase(normalized, listOf("scheduled", "later", "tomorrow", "schedule", "book for", "at ")) -> GroceryDeliveryUrgency.SCHEDULED
            else -> null
        }
    }

    fun parsePaymentPreference(rawText: String): GroceryPaymentMethod? {
        val normalized = normalize(rawText)
        return when {
            containsAnyPhrase(normalized, listOf("upi", "gpay", "google pay", "phonepe", "paytm")) -> GroceryPaymentMethod.UPI
            containsAnyPhrase(normalized, listOf("card", "debit card", "credit card")) -> GroceryPaymentMethod.CARD
            containsAnyPhrase(normalized, listOf("net banking", "internet banking", "online banking")) -> GroceryPaymentMethod.NET_BANKING
            containsAnyPhrase(normalized, listOf("wallet", "app wallet")) -> GroceryPaymentMethod.WALLET
            containsAnyPhrase(normalized, listOf("cash on delivery", "cod")) -> GroceryPaymentMethod.COD
            else -> null
        }
    }

    fun parseSelectionPreference(rawText: String): Pair<Int?, GrocerySelectionPreference?>? {
        val normalized = normalize(rawText)
        val selectionPreference = when {
            containsAnyPhrase(normalized, listOf("second one", "second option", "option 2", "2nd", "second")) -> GrocerySelectionPreference.SECOND
            containsAnyPhrase(normalized, listOf("third one", "third option", "option 3", "3rd", "third")) -> GrocerySelectionPreference.THIRD
            containsAnyPhrase(normalized, listOf("first one", "first option", "option 1", "1st", "first", "first available")) -> GrocerySelectionPreference.FIRST
            containsAnyPhrase(normalized, listOf("cheapest", "lowest price", "cheapest cart", "cheapest option")) -> GrocerySelectionPreference.CHEAPEST
            containsAnyPhrase(normalized, listOf("fastest", "fastest delivery")) -> GrocerySelectionPreference.FASTEST
            containsAnyPhrase(normalized, listOf("best quality", "best rated")) -> GrocerySelectionPreference.BEST_QUALITY
            containsAnyPhrase(normalized, listOf("best overall", "recommended", "best option")) -> GrocerySelectionPreference.BEST_OVERALL
            parseProviderPreference(normalized) != null -> GrocerySelectionPreference.PROVIDER
            else -> null
        }

        if (selectionPreference == null) return null
        val selectionIndex = when (selectionPreference) {
            GrocerySelectionPreference.FIRST -> 1
            GrocerySelectionPreference.SECOND -> 2
            GrocerySelectionPreference.THIRD -> 3
            else -> null
        }
        return selectionIndex to selectionPreference
    }

    fun extractPaymentPreference(rawText: String): GroceryPaymentMethod? = parsePaymentPreference(rawText)

    fun extractDeliveryUrgency(rawText: String): GroceryDeliveryUrgency? = parseDeliveryUrgency(rawText)

    fun extractBudgetPreference(rawText: String): GroceryBudgetPreference? = parseBudgetPreference(rawText)

    fun isNegative(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsAnyPhrase(
            normalized,
            listOf("no", "nope", "nah", "cancel", "stop", "not now", "do not", "don't", "dont", "never mind", "nevermind", "go back", "back", "dismiss", "abort")
        )
    }

    fun isPositive(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsAnyPhrase(
            normalized,
            listOf("yes", "yep", "yeah", "confirm", "confirm it", "proceed", "ok", "okay", "sure", "book it", "go ahead", "continue")
        )
    }

    fun isRefineCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsAnyPhrase(
            normalized,
            listOf("change item", "change brand", "change quantity", "change budget", "change delivery time", "search again", "refine", "adjust", "edit")
        )
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

        val normalized = normalize(text)
        if (normalized in setOf(
                "reorder previous grocery list",
                "reorder my last grocery order",
                "buy the same groceries again",
                "buy from my previous list",
                "repeat the same groceries",
                "same groceries again",
                "same order again",
                "my usual groceries",
                "usual groceries",
                "usual grocery list"
            )) {
            return ""
        }

        val leadingPrefixes = listOf(
            "i want ",
            "i need ",
            "need ",
            "want ",
            "please ",
            "order ",
            "apply coupon to ",
            "use wallet balance for ",
            "use wallet balance ",
            "reorder previous grocery list",
            "reorder my last grocery order",
            "buy the same groceries again",
            "buy from my previous list",
            "repeat the same groceries",
            "same groceries again",
            "same order again",
            "buy the same groceries again ",
            "buy from my previous list ",
            "reorder previous grocery list ",
            "reorder my last grocery order ",
            "repeat the same groceries ",
            "same groceries again ",
            "same order again ",
            "compare prices for ",
            "compare price for ",
            "compare the prices for ",
            "compare the price for ",
            "compare ",
            "add ",
            "get ",
            "reorder ",
            "reorder previous ",
            "reorder my ",
            "buy from ",
            "suggest "
        )

        val lower = text.lowercase(Locale.US)
        val prefix = leadingPrefixes.firstOrNull { lower.startsWith(it) }
        if (prefix != null) {
            text = text.substring(prefix.length).trimStart(',', ':', ' ')
        }

        val providerClauseMarkers = listOf(" on ", " from ", " via ", " in ")
        val marker = providerClauseMarkers.firstOrNull { lower.contains(it) }
        if (marker != null && containsGroceryCue(lower)) {
            text = text.substringBefore(marker, text).trim()
        }

        text = text.replace(
            Regex("""\s+(?:but\s+)?stop\s+before\s+payment.*$""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        text = text.replace(
            Regex("""\s+(?:but\s+)?stop\s+at\s+final\s+confirmation(?:/manual payment boundary)?\s*$""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        return text
    }

    private fun parseItem(segment: String): GroceryItem? {
        val cleaned = cleanSegment(segment)
        if (cleaned.isBlank()) return null

        val normalized = normalize(cleaned)
        if (!containsItemCue(normalized) && !containsQuantityCue(cleaned)) return null
        if (isProviderOnlySegment(normalized)) return null
        if (containsFoodOrderingCue(normalized) && !containsGroceryCue(normalized)) return null

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

        if (quantityValue == null) {
            Regex("""^\s*(\d+(?:\.\d+)?)\s+(.+)$""", RegexOption.IGNORE_CASE)
                .matchEntire(cleaned)
                ?.let { match ->
                    val candidateQuantity = match.groupValues.getOrNull(1)?.toDoubleOrNull()
                    val candidateRemainder = match.groupValues.getOrNull(2)?.trim().orEmpty()
                    if (candidateQuantity != null && candidateRemainder.isNotBlank()) {
                        quantityValue = candidateQuantity
                        remainder = candidateRemainder
                    }
                }
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
        val prefixes = listOf("remove ", "remove item ", "delete ", "drop ", "skip ")
        val matched = prefixes.firstOrNull { normalized.startsWith(it) } ?: return null
        val lower = rawText.lowercase(Locale.US)
        val query = rawText.substring(lower.indexOf(matched) + matched.length).trim()
        return query.takeIf { it.isNotBlank() }
    }

    private fun parseBrandPreference(normalized: String): String? {
        if (containsAnyPhrase(normalized, listOf("regular is fine", "no brand", "generic options"))) {
            return "regular"
        }

        groceryBrands
            .sortedByDescending { it.length }
            .firstOrNull { brand -> containsPhrase(normalized, brand) }
            ?.let { matched ->
                return matched.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                    }
                }
            }

        return null
    }

    private fun extractRestaurantName(rawText: String): String? {
        val trimmed = cleanSegment(rawText)
        if (trimmed.isBlank()) return null

        val patterns = listOf(
            Regex("""(?i)\bfrom\s+(.+?)(?=(?:\s+(?:on|to|with|using|via|coupon|promo|code|offer|and|or)\b|[,.!?]|$))"""),
            Regex("""(?i)\bat\s+(.+?)(?=(?:\s+(?:on|to|with|using|via|coupon|promo|code|offer|and|or)\b|[,.!?]|$))""")
        )

        patterns.forEach { pattern ->
            pattern.find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

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

    private fun extractDeliveryLocation(rawText: String): String? {
        val patterns = listOf(
            Regex("""\b(?:deliver(?: it)? to|delivery to|send to|ship to)\s+(.+?)(?:$|,|\.| and | with )""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:address|location)\s*[:=]\s*(.+?)(?:$|,|\.| and | with )""", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { pattern ->
            pattern.find(rawText)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        val normalized = normalize(rawText)
        return when {
            containsAnyPhrase(normalized, listOf("current location", "current address", "use current location")) -> "current location"
            else -> null
        }
    }

    private fun containsGroceryCue(normalized: String): Boolean {
        val hasGroceryWord = groceryItemKeywords.any { containsPhrase(normalized, it) } ||
            groceryProviderPatterns.values.flatten().any { containsPhrase(normalized, it) } ||
            groceryBrands.any { containsPhrase(normalized, it) } ||
            containsAnyPhrase(normalized, listOf("grocery", "groceries", "basket", "cart", "usual groceries", "previous list"))

        val genericVerb = containsAnyPhrase(
            normalized,
            listOf(
                "compare",
                "reorder",
                "cheapest",
                "fastest",
                "best quality",
                "best overall",
                "coupon",
                "offers",
                "offer"
            )
        )

        if (genericVerb) {
            return hasGroceryWord
        }

        return hasGroceryWord
    }

    private fun looksLikeShoppingRequest(normalized: String): Boolean {
        val shoppingKeywords = listOf(
            "phone",
            "mobile",
            "smartphone",
            "laptop",
            "computer",
            "headphones",
            "earphones",
            "tablet",
            "television",
            "smart tv",
            "tv",
            "watch",
            "smartwatch",
            "camera",
            "monitor",
            "console",
            "electronics"
        )

        val shoppingContext = containsAnyPhrase(
            normalized,
            listOf("buy", "buying", "order", "purchase", "compare", "search", "deal", "coupon", "amazon", "flipkart", "croma", "reliance digital")
        )

        if (!shoppingContext) {
            return false
        }

        return shoppingKeywords.any { containsPhrase(normalized, it) }
    }

    private fun containsFoodOrderingCue(normalized: String): Boolean {
        return foodDeliveryKeywords.any { containsPhrase(normalized, it) }
    }

    private fun containsItemCue(normalized: String): Boolean {
        return groceryItemKeywords.any { containsPhrase(normalized, it) } ||
            groceryBrands.any { containsPhrase(normalized, it) } ||
            containsAnyPhrase(
                normalized,
                listOf(
                    "milk",
                    "bread",
                    "rice",
                    "dal",
                    "atta",
                    "sugar",
                    "order",
                    "buy",
                    "add",
                    "replace"
                )
            )
    }

    private fun containsQuantityCue(rawText: String): Boolean {
        val cleaned = cleanSegment(rawText)
        if (quantitySuffixPattern.containsMatchIn(cleaned)) return true
        return Regex("""^\s*\d+(?:\.\d+)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    }

    private fun isProviderOnlySegment(normalized: String): Boolean {
        if (parseProviderPreference(normalized) != null && !containsItemCue(normalized) && !containsQuantityCue(normalized)) {
            return true
        }

        return containsAnyPhrase(
            normalized,
            listOf(
                "compare",
                "cheapest",
                "fastest",
                "best quality",
                "best overall",
                "best rated",
                "first one",
                "second one",
                "third one",
                "search again",
                "change item",
                "change brand",
                "change quantity",
                "change budget",
                "change delivery time",
                "cancel",
                "stop"
            )
        ) && !containsItemCue(normalized)
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
                return matched.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                    }
                }
            }

        return null
    }

    private fun isCancel(normalized: String): Boolean {
        return containsAnyPhrase(
            normalized,
            listOf(
                "cancel",
                "stop",
                "stop grocery",
                "cancel grocery",
                "go back",
                "back",
                "dismiss",
                "never mind",
                "nevermind",
                "abort"
            )
        )
    }

    private fun isConfirm(normalized: String): Boolean {
        return containsAnyPhrase(
            normalized,
            listOf(
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
            )
        )
    }

    private fun isInformationalGroceryInquiry(normalized: String): Boolean {
        val informationalPhrases = listOf(
            "tell me about groceries",
            "tell me about grocery",
            "what is the price of",
            "what's the price of",
            "how much is",
            "how much are",
            "price of",
            "cost of"
        )
        if (!containsAnyPhrase(normalized, informationalPhrases)) return false

        val purchaseIntentCues = listOf(
            "buy",
            "order",
            "compare",
            "cheapest",
            "cheaper",
            "basket",
            "cart",
            "reorder",
            "coupon",
            "promo",
            "offer",
            "blinkit",
            "zepto",
            "instamart",
            "jiomart",
            "bigbasket"
        )

        return purchaseIntentCues.none { containsPhrase(normalized, it) }
    }

    private fun isCommunicationOrContactRequest(normalized: String): Boolean {
        return normalized.startsWith("call ") ||
            normalized.startsWith("text ") ||
            normalized.startsWith("message ") ||
            normalized.startsWith("email ") ||
            containsAnyPhrase(
                normalized,
                listOf(
                    "reply to",
                    "draft reply",
                    "draft email",
                    "send email",
                    "send message",
                    "write professional email",
                    "summarize my messages",
                    "what did i miss"
                )
            )
    }

    private fun isNavigationOrSystemRequest(normalized: String): Boolean {
        return normalized == "go home" ||
            normalized == "home" ||
            normalized == "back to home" ||
            normalized == "go back" ||
            normalized == "go previous" ||
            normalized == "back" ||
            normalized == "previous" ||
            normalized == "show recent apps" ||
            normalized == "show recents" ||
            normalized == "open recents" ||
            normalized == "open recent apps" ||
            normalized == "recent apps" ||
            normalized == "recents" ||
            normalized == "open notifications" ||
            normalized == "show notifications" ||
            normalized == "check notifications" ||
            normalized == "read notifications" ||
            normalized == "scroll down" ||
            normalized == "scroll up" ||
            normalized == "move down" ||
            normalized == "move up" ||
            normalized == "swipe down" ||
            normalized == "swipe up" ||
            normalized.startsWith("open app ") ||
            normalized.startsWith("open ") ||
            normalized.startsWith("launch ") ||
            normalized.startsWith("start ") ||
            normalized.startsWith("tap ") ||
            normalized.startsWith("tap on ") ||
            normalized.startsWith("click ") ||
            normalized.startsWith("click on ") ||
            normalized.startsWith("press ") ||
            normalized.startsWith("press on ") ||
            normalized.startsWith("type ") ||
            normalized.startsWith("write ") ||
            normalized.startsWith("enter ") ||
            normalized.startsWith("input ")
    }

    private fun countProviderMentions(normalized: String): Int {
        return groceryProviderPatterns.values.flatten().count { containsPhrase(normalized, it) }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun containsAnyPhrase(normalized: String, phrases: List<String>): Boolean {
        return phrases.any { containsPhrase(normalized, it) }
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
    }
}
