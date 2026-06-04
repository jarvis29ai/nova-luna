package com.nova.luna.food

import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class FoodIntentParser {
    fun parse(rawText: String): FoodBookingRequest? {
        val trimmed = clean(rawText)
        if (trimmed.isBlank()) return null
        if (!isFoodOrderCommand(trimmed)) return null

        val foodItem = extractFoodItem(trimmed)
        val restaurantName = extractRestaurantName(trimmed)
        val quantity = extractQuantity(trimmed)
        val requestedProviders = extractPlatformPreferences(trimmed)
        val deliveryLocation = extractDeliveryLocation(trimmed)
        val couponPreference = extractCouponPreference(trimmed)

        if (isReplyOnlyChoice(foodItem, restaurantName, quantity, requestedProviders, deliveryLocation, couponPreference)) {
            return null
        }

        if (
            foodItem.isNullOrBlank() &&
            restaurantName.isNullOrBlank() &&
            requestedProviders.isEmpty() &&
            deliveryLocation.isNullOrBlank() &&
            couponPreference.isNullOrBlank()
        ) {
            return null
        }

        return FoodBookingRequest(
            rawText = rawText,
            foodItem = foodItem?.takeIf { it.isNotBlank() },
            restaurantName = restaurantName?.takeIf { it.isNotBlank() },
            quantity = quantity,
            preferredProvider = requestedProviders.singleOrNull(),
            requestedProviders = requestedProviders,
            deliveryLocation = deliveryLocation?.takeIf { it.isNotBlank() },
            couponPreference = couponPreference?.takeIf { it.isNotBlank() }
        )
    }

    fun isFoodOrderCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return false

        return listOf(
            Regex("""^(?:i\s+)?want\s+to\s+eat\b""", RegexOption.IGNORE_CASE),
            Regex("""^(?:i\s+)?want\s+to\s+order\b""", RegexOption.IGNORE_CASE),
            Regex("""^(?:i\s+)?(?:order|get|buy|book|eat|compare|find|search(?:\s+for)?|bring|deliver)\b""", RegexOption.IGNORE_CASE)
        ).any { it.containsMatchIn(normalized) }
    }

    fun extractFoodItem(rawText: String): String? {
        val trimmed = clean(rawText)
        if (trimmed.isBlank()) return null

        var candidate = trimmed
        leadPatterns.forEach { pattern ->
            pattern.matchEntire(candidate)?.let { match ->
                candidate = match.groupValues[1]
                return@forEach
            }
        }

        candidate = stripTrailingClauses(candidate)
        candidate = stripLeadingQuantity(candidate)
        candidate = candidate.replace(Regex("""^(?:the|a|an)\s+""", RegexOption.IGNORE_CASE), "")
        candidate = sanitize(candidate)
        return candidate.takeIf { it.isNotBlank() }
    }

    fun extractRestaurantName(rawText: String): String? {
        val trimmed = clean(rawText)
        if (trimmed.isBlank()) return null

        val pattern = Regex(
            """(?i)\bfrom\s+(.+?)(?=(?:\s+(?:on|to|with|using|via|coupon|promo|code|offer)\b|[,.!?]|$))"""
        )
        return pattern.find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::sanitize)
            ?.takeIf { it.isNotBlank() }
    }

    fun extractQuantity(rawText: String): Int? {
        val trimmed = clean(rawText)
        if (trimmed.isBlank()) return null

        val leadQuantityPattern = Regex(
            """(?i)^(?:i\s+)?(?:want\s+to\s+eat|want\s+to\s+order|want\s+to\s+have|want\s+to|order(?:\s+me)?|book|get(?:\s+me)?|buy|eat|find|search(?:\s+for)?|compare|bring(?:\s+me)?|deliver(?:\s+me)?|need)\s+(?:me\s+)?(?:a|an|the)?\s*(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\b"""
        )
        leadQuantityPattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { value ->
            return quantityWordToInt(value)
        }

        val nakedQuantityPattern = Regex("""(?i)^(?:a|an|the)?\s*(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\b""")
        nakedQuantityPattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { value ->
            return quantityWordToInt(value)
        }

        return null
    }

    fun extractPlatformPreference(rawText: String): FoodProvider? {
        return extractPlatformPreferences(rawText).singleOrNull()
    }

    fun extractPlatformPreferences(rawText: String): List<FoodProvider> {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return emptyList()

        val providers = mutableListOf<FoodProvider>()
        providerPattern.findAll(normalized).forEach { match ->
            val value = match.groupValues.getOrNull(1).orEmpty()
            FoodProvider.values().firstOrNull { it.name.equals(value, ignoreCase = true) }?.let { provider ->
                if (provider !in providers) {
                    providers.add(provider)
                }
            }
        }
        return providers
    }

    fun extractDeliveryLocation(rawText: String): String? {
        val trimmed = clean(rawText)
        if (trimmed.isBlank()) return null

        val patterns = listOf(
            Regex(
                """(?i)\b(?:deliver(?:y|ed)?|send|ship|bring|get delivered)\s+(?:it\s+)?to\s+(.+?)(?=(?:\s+(?:with|using|via|coupon|promo|code|offer|from|on)\b|[,.!?]|$))"""
            ),
            Regex(
                """(?i)\b(?:deliver(?:y|ed)?|send|ship|bring)\s+at\s+(.+?)(?=(?:\s+(?:with|using|via|coupon|promo|code|offer|from|on)\b|[,.!?]|$))"""
            )
        )

        patterns.forEach { pattern ->
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let(::sanitize)?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }

        return null
    }

    fun extractCouponPreference(rawText: String): String? {
        val trimmed = clean(rawText)
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        if (!couponCuePattern.containsMatchIn(normalized)) return null

        if (normalized.contains("no coupon") || normalized.contains("without coupon")) {
            return "none"
        }

        if (normalized.contains("any coupon") ||
            normalized.contains("any promo") ||
            normalized.contains("best coupon") ||
            normalized.contains("best promo") ||
            normalized.contains("best offer")
        ) {
            return "any"
        }

        couponCodePattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { code ->
            return code.uppercase(Locale.US)
        }

        return "any"
    }

    fun parseProviderChoice(rawText: String): FoodProvider? {
        return extractPlatformPreference(rawText)
    }

    fun isCheapestChoice(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "cheapest",
            "lowest",
            "least expensive",
            "best price",
            "minimum price",
            "minimum cost",
            "cheaper option",
            "lowest price"
        ).any { containsPhrase(normalized, it) }
    }

    fun isAffirmative(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "yes",
            "yeah",
            "yep",
            "yup",
            "sure",
            "ok",
            "okay",
            "confirm",
            "confirm it",
            "proceed",
            "go ahead",
            "book it",
            "do it",
            "yes confirm"
        ).any { containsPhrase(normalized, it) }
    }

    fun isNegative(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "no",
            "nope",
            "cancel",
            "stop",
            "not now",
            "don't",
            "do not",
            "never mind"
        ).any { containsPhrase(normalized, it) }
    }

    private val providerPattern = Regex("""\b(swiggy|zomato|toings)\b""", RegexOption.IGNORE_CASE)

    private val couponCuePattern = Regex("""\b(coupon|promo|code|offer|discount|deal|save)\b""")

    private val couponCodePattern = Regex(
        """(?i)\b(?:coupon|promo(?:\s*code)?|code|offer)\b(?:\s*(?:is|as|of|to|for|use|apply|named|called))?\s*([a-z0-9][a-z0-9_-]{2,})\b"""
    )

    private val leadPatterns = listOf(
        Regex(
            """^(?:i\s+)?(?:want\s+to\s+eat|want\s+to\s+eat|want\s+to\s+order|want\s+to\s+have|want\s+to|order(?:\s+me)?|book|get(?:\s+me)?|buy|eat|find|search(?:\s+for)?|compare|bring(?:\s+me)?|deliver(?:\s+me)?|need)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
    )

    private fun stripTrailingClauses(value: String): String {
        val lowered = value.lowercase(Locale.US)
        val markers = listOf(
            " from ",
            " on ",
            " with ",
            " using ",
            " via ",
            " coupon ",
            " promo ",
            " code ",
            " offer ",
            " deliver to ",
            " delivery to "
        )

        var endIndex = value.length
        markers.forEach { marker ->
            val index = lowered.indexOf(marker)
            if (index >= 0 && index < endIndex) {
                endIndex = index
            }
        }

        return value.substring(0, endIndex)
    }

    private fun stripLeadingQuantity(value: String): String {
        val words = mapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5,
            "six" to 6,
            "seven" to 7,
            "eight" to 8,
            "nine" to 9,
            "ten" to 10
        )

        val normalized = normalize(value)
        val numericPrefix = Regex("""^(?:\d+|one|two|three|four|five|six|seven|eight|nine|ten)(?:\s*x)?\s+(.+)$""")
        numericPrefix.matchEntire(normalized)?.groupValues?.getOrNull(1)?.let { remainder ->
            return remainder
        }

        words.entries.firstOrNull { (word, _) ->
            normalized.startsWith("$word ")
        }?.let { (_, quantity) ->
            return normalized
                .removePrefix("${quantityToWord(quantity)} ")
                .trim()
        }

        return value
    }

    private fun quantityToWord(quantity: Int): String {
        return when (quantity) {
            1 -> "one"
            2 -> "two"
            3 -> "three"
            4 -> "four"
            5 -> "five"
            6 -> "six"
            7 -> "seven"
            8 -> "eight"
            9 -> "nine"
            10 -> "ten"
            else -> quantity.toString()
        }
    }

    private fun quantityWordToInt(value: String): Int? {
        return when (value.lowercase(Locale.US)) {
            "one" -> 1
            "two" -> 2
            "three" -> 3
            "four" -> 4
            "five" -> 5
            "six" -> 6
            "seven" -> 7
            "eight" -> 8
            "nine" -> 9
            "ten" -> 10
            else -> value.toIntOrNull()
        }?.takeIf { it > 0 }
    }

    private fun sanitize(value: String?): String {
        return value.orEmpty()
            .trim()
            .trimEnd('.', ',', '!', '?')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalized.contains(Regex("""\b${Regex.escape(normalizedPhrase)}\b"""))
    }

    private fun isReplyOnlyChoice(
        foodItem: String?,
        restaurantName: String?,
        quantity: Int?,
        requestedProviders: List<FoodProvider>,
        deliveryLocation: String?,
        couponPreference: String?
    ): Boolean {
        if (restaurantName != null ||
            quantity != null ||
            requestedProviders.isNotEmpty() ||
            deliveryLocation != null ||
            couponPreference != null
        ) {
            return false
        }

        val normalizedFoodItem = normalize(foodItem.orEmpty())
        return normalizedFoodItem in setOf(
            "cheapest",
            "cheapest option",
            "lowest",
            "lowest price",
            "best",
            "best price",
            "cheaper option",
            "least expensive",
            "swiggy",
            "zomato",
            "toings",
            "confirm",
            "yes",
            "no",
            "okay",
            "ok"
        )
    }

    private fun clean(value: String?): String {
        return sanitize(AssistantTextNormalizer.stripWakeWords(value.orEmpty()))
    }
}
