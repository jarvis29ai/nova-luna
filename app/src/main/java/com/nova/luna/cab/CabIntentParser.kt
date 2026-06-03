package com.nova.luna.cab

import java.util.Locale

class CabIntentParser {
    fun parse(rawText: String): CabIntentParseResult? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        if (!containsBookingCue(normalized)) return null

        val pickupText = extractPickupFromCommand(trimmed)
        val dropText = extractDropFromCommand(trimmed)
        val rideType = extractRideType(trimmed)
        val providerPreference = parseProviderChoice(trimmed)
        val wantsCheapest = isCheapestChoice(trimmed)

        return CabIntentParseResult(
            rawText = rawText,
            isCabBooking = true,
            pickupText = pickupText,
            dropText = dropText,
            rideType = rideType,
            providerPreference = providerPreference,
            wantsCheapest = wantsCheapest
        )
    }

    fun isCabBookingCommand(rawText: String): Boolean {
        return parse(rawText)?.isCabBooking == true
    }

    fun extractPickupLocation(rawText: String): String? {
        val fromCommand = extractPickupFromCommand(rawText)
        if (fromCommand != null) return fromCommand

        if (!isLikelyLocationReply(rawText)) return null
        return sanitizeLocation(rawText)
    }

    fun extractDropLocation(rawText: String): String? {
        val fromCommand = extractDropFromCommand(rawText)
        if (fromCommand != null) return fromCommand

        if (!isLikelyLocationReply(rawText)) return null
        return sanitizeLocation(rawText)
    }

    fun extractRideType(rawText: String): RideType? {
        val normalized = normalize(rawText)
        return when {
            containsWord(normalized, "auto") -> RideType.AUTO
            containsWord(normalized, "bike") -> RideType.BIKE
            containsWord(normalized, "mini") -> RideType.MINI
            containsWord(normalized, "sedan") -> RideType.SEDAN
            containsWord(normalized, "suv") -> RideType.SUV
            containsWord(normalized, "any") || containsPhrase(normalized, "any ride type") -> RideType.ANY
            else -> null
        }
    }

    fun parseProviderChoice(rawText: String): CabProvider? {
        val normalized = normalize(rawText)
        return when {
            containsWord(normalized, "uber") -> CabProvider.UBER
            containsWord(normalized, "ola") -> CabProvider.OLA
            containsWord(normalized, "rapido") -> CabProvider.RAPIDO
            containsPhrase(normalized, "in drive") || containsWord(normalized, "indrive") -> CabProvider.INDRIVE
            else -> null
        }
    }

    fun isCheapestChoice(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "cheapest",
            "cheapest available",
            "lowest",
            "lowest fare",
            "lowest price",
            "least expensive",
            "best price",
            "cheaper option",
            "cheapest cab"
        ).any { containsPhrase(normalized, it) }
    }

    fun isFirstChoice(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "first one",
            "first option",
            "first ride",
            "option one",
            "pick the first",
            "use the first",
            "book the first"
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
            "yes book"
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

    fun isPauseCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "wait",
            "wait a moment",
            "hold on",
            "one moment",
            "not yet",
            "later"
        ).any { containsPhrase(normalized, it) }
    }

    fun isChangeRideRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "change ride",
            "change the ride",
            "change vehicle",
            "change type",
            "switch ride"
        ).any { containsPhrase(normalized, it) }
    }

    fun isChangeDropRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "change drop",
            "change destination",
            "change the drop",
            "change where to",
            "change where i am going"
        ).any { containsPhrase(normalized, it) }
    }

    fun isChangePickupRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "change pickup",
            "change the pickup",
            "change pickup location",
            "change from location"
        ).any { containsPhrase(normalized, it) }
    }

    fun isCurrentLocationRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsPhrase(normalized, "current location") ||
            containsPhrase(normalized, "my current location") ||
            containsPhrase(normalized, "use current location") ||
            containsPhrase(normalized, "use my location") ||
            containsPhrase(normalized, "current gps location")
    }

    fun isCancelCommand(rawText: String): Boolean {
        return isNegative(rawText)
    }

    private fun extractPickupFromCommand(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val currentLocationPattern = Regex(
            """(?:from|pickup\s+from|pickup\s+at|pickup\s+is|pickup\s+location\s+is)\s+(?:my\s+)?current\s+location(?:\s+to\s+.+)?""",
            RegexOption.IGNORE_CASE
        )
        if (currentLocationPattern.containsMatchIn(trimmed)) {
            return "current location"
        }

        val fromToPattern = Regex(
            """(?:from|pickup\s+from|pickup\s+at|pickup\s+is|pickup\s+location\s+is)\s+(.+?)(?=\s+\bto\b|$)""",
            RegexOption.IGNORE_CASE
        )
        fromToPattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
            return normalizeCurrentLocation(candidate) ?: sanitizeLocation(candidate)
        }

        return null
    }

    private fun extractDropFromCommand(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val explicitFromToPattern = Regex(
            """(?:from|pickup\s+from|pickup\s+at|pickup\s+is|pickup\s+location\s+is)\s+(.+?)\s+to\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        explicitFromToPattern.find(trimmed)?.groupValues?.getOrNull(2)?.let { candidate ->
            return sanitizeLocation(candidate)
        }

        val fromToPattern = Regex(
            """(?:to|towards|destination\s+is|destination|drop\s+to|drop\s+at)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        fromToPattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
            return sanitizeLocation(candidate)
        }

        return null
    }

    private fun containsBookingCue(normalized: String): Boolean {
        if (normalized.isBlank()) return false

        val cuePhrases = listOf(
            "book",
            "book cab",
            "book ride",
            "book auto",
            "book bike",
            "book mini",
            "book sedan",
            "book suv",
            "book uber",
            "book ola",
            "book rapido",
            "book indrive",
            "get me a ride",
            "get me ride",
            "need a cab",
            "i need a cab",
            "need a ride",
            "cab from",
            "cab to",
            "ride from",
            "ride to"
        )

        return cuePhrases.any { normalized.contains(it) }
    }

    private fun isLikelyLocationReply(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return false

        val normalized = normalize(trimmed)
        if (containsBookingCue(normalized)) return false
        if (isAffirmative(trimmed) || isNegative(trimmed) || isPauseCommand(trimmed)) return false
        if (parseProviderChoice(trimmed) != null) return false
        if (isCheapestChoice(trimmed) || isFirstChoice(trimmed)) return false
        if (extractRideType(trimmed) != null) return false

        return true
    }

    private fun normalizeCurrentLocation(value: String): String? {
        val normalized = normalize(value)
        return if (normalized == "current location" || normalized == "my current location" || normalized == "current") {
            "current location"
        } else {
            null
        }
    }

    private fun sanitizeLocation(value: String?): String? {
        val cleaned = value?.trim()
            ?.trimEnd('.', ',', '!', '?')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()

        if (cleaned.isBlank()) return null

        val lowered = cleaned.lowercase(Locale.US)
        val leadIns = listOf(
            "pickup is ",
            "pickup location is ",
            "pickup from ",
            "pickup at ",
            "drop is ",
            "drop location is ",
            "destination is ",
            "destination ",
            "from ",
            "to ",
            "at ",
            "use ",
            "my "
        )

        for (leadIn in leadIns) {
            if (lowered.startsWith(leadIn)) {
                return cleaned.substring(leadIn.length).trim().ifBlank { null }
            }
        }

        return cleaned
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun matchesReply(normalized: String, value: String): Boolean {
        val escaped = Regex.escape(value)
        val replyPattern = Regex(
            """^(?:book\s+(?:the\s+)?|choose\s+|pick\s+|select\s+|use\s+|go\s+with\s+|say\s+|book\s+the\s+)?$escaped(?:\s+(?:please|now|ride|cab|option|one))?$""",
            RegexOption.IGNORE_CASE
        )
        return replyPattern.matches(normalized)
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalized.contains(Regex("""\b${Regex.escape(normalizedPhrase)}\b"""))
    }

    private fun containsWord(normalized: String, word: String): Boolean {
        return Regex("""\b${Regex.escape(word.lowercase(Locale.US))}\b""").containsMatchIn(normalized)
    }
}
