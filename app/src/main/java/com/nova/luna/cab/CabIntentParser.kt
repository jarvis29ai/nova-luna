package com.nova.luna.cab

import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

enum class CabFinalConfirmationReply {
    CONFIRM,
    DECLINE,
    NONE
}

class CabIntentParser {
    private val rideTypeCorrections = mapOf(
        "app" to RideType.AUTO,
        "otto" to RideType.AUTO,
        "rickshaw" to RideType.AUTO
    )

    fun parse(rawText: String): CabIntentParseResult? {
        return parseInitialCabRequest(rawText)
    }

    fun parseInitialCabRequest(rawText: String): CabIntentParseResult? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        if (!containsBookingCue(normalized)) return null

        val pickupValue = extractPickupValueFromCommand(trimmed)
        val pickupMode = when {
            pickupValue?.isCurrentLocation == true -> PickupMode.CURRENT_LOCATION
            pickupValue != null -> PickupMode.USER_TEXT
            isCurrentLocationRequest(trimmed) -> PickupMode.CURRENT_LOCATION
            else -> PickupMode.UNKNOWN
        }
        val pickupText = when {
            pickupValue != null -> pickupValue.displayText()
            pickupMode == PickupMode.CURRENT_LOCATION -> "Current location"
            else -> null
        }

        return CabIntentParseResult(
            rawText = rawText,
            isCabBooking = true,
            pickupText = pickupText,
            pickupMode = pickupMode,
            dropText = extractDropFromCommand(trimmed),
            rideType = parseRideTypeFromBookingCommand(trimmed),
            providerPreference = parseProviderChoiceReply(trimmed),
            wantsCheapest = isCheapestChoice(trimmed),
            wantsFirstOne = isFirstChoice(trimmed)
        )
    }

    fun parsePickupReply(rawText: String): LocationValue? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        if (isWakeWordOrFillerOnly(trimmed)) {
            return null
        }
        if (isCancel(trimmed) || isAffirmative(trimmed) || isNegative(trimmed) || isPauseCommand(trimmed)) {
            return null
        }
        if (parseRideTypeReply(trimmed) != null || parseProviderChoiceReply(trimmed) != null) {
            return null
        }
        if (isCheapestChoice(trimmed) || isFirstChoice(trimmed)) return null

        if (isCurrentLocationRequest(trimmed)) {
            return LocationValue(
                rawText = trimmed,
                isCurrentLocation = true,
                displayName = "Current location"
            )
        }

        if (!isLikelyLocationReply(trimmed)) return null

        val locationText = sanitizeLocation(trimmed)
        if (locationText.isBlank()) return null

        return LocationValue(
            rawText = locationText,
            isCurrentLocation = false,
            displayName = locationText
        )
    }

    fun parseRideTypeReply(rawText: String): RideType? {
        val normalized = normalize(rawText)
        rideTypeCorrections.entries.firstOrNull { (token, _) ->
            containsWord(normalized, token)
        }?.let { return it.value }
        return when {
            containsWord(normalized, "auto") -> RideType.AUTO
            containsWord(normalized, "bike") -> RideType.BIKE
            containsWord(normalized, "mini") -> RideType.MINI
            containsWord(normalized, "sedan") -> RideType.SEDAN
            containsWord(normalized, "suv") -> RideType.SUV
            containsPhrase(normalized, "any ride") ||
                containsPhrase(normalized, "any cab") ||
                containsPhrase(normalized, "any car") ||
                containsWord(normalized, "any") -> RideType.ANY
            else -> null
        }
    }

    fun parseProviderChoiceReply(rawText: String): CabProvider? {
        val normalized = normalize(rawText)
        return when {
            containsWord(normalized, "uber") -> CabProvider.UBER
            containsWord(normalized, "ola") -> CabProvider.OLA
            containsWord(normalized, "rapido") -> CabProvider.RAPIDO
            containsPhrase(normalized, "in drive") || containsWord(normalized, "indrive") -> CabProvider.INDRIVE
            else -> null
        }
    }

    fun parseFinalConfirmationReply(rawText: String): CabFinalConfirmationReply {
        return when {
            isAffirmative(rawText) -> CabFinalConfirmationReply.CONFIRM
            isCancel(rawText) || isNegative(rawText) -> CabFinalConfirmationReply.DECLINE
            else -> CabFinalConfirmationReply.NONE
        }
    }

    fun isCabBookingCommand(rawText: String): Boolean {
        return parseInitialCabRequest(rawText)?.isCabBooking == true
    }

    fun extractPickupLocation(rawText: String): String? {
        return parsePickupReply(rawText)?.displayText()
    }

    fun extractDropLocation(rawText: String): String? {
        val fromCommand = extractDropFromCommand(rawText)
        if (fromCommand != null) return fromCommand

        if (!isLikelyLocationReply(rawText)) return null
        return sanitizeLocation(rawText)
    }

    fun extractRideType(rawText: String): RideType? {
        return parseRideTypeReply(rawText)
    }

    fun parseProviderChoice(rawText: String): CabProvider? {
        return parseProviderChoiceReply(rawText)
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
            "cheapest cab",
            "book cheapest",
            "book the cheapest"
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
            "book the first",
            "book first one"
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
            "yes book",
            "please do",
            "please book"
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
            "never mind",
            "later"
        ).any { containsPhrase(normalized, it) }
    }

    fun isPauseCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "wait",
            "wait a moment",
            "hold on",
            "one moment",
            "not yet"
        ).any { containsPhrase(normalized, it) }
    }

    fun isChangeRideRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "change ride",
            "change the ride",
            "change vehicle",
            "change type",
            "switch ride",
            "switch to bike",
            "switch to auto",
            "switch to mini",
            "switch to sedan",
            "switch to suv"
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
            containsPhrase(normalized, "current gps location") ||
            containsPhrase(normalized, "my location") ||
            containsPhrase(normalized, "from here") ||
            containsPhrase(normalized, "here")
    }

    fun isCancel(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "cancel",
            "stop",
            "abort",
            "no",
            "nope",
            "not now",
            "never mind",
            "quit",
            "stop booking",
            "cancel booking",
            "cancel ride"
        ).any { containsPhrase(normalized, it) }
    }

    fun isCancelCommand(rawText: String): Boolean {
        return isCancel(rawText)
    }

    fun isFareResumeRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "i have selected destination",
            "selected destination",
            "destination selected",
            "done",
            "continue",
            "compare now",
            "check fare",
            "check fares",
            "resume fare",
            "resume fares",
            "compare fares"
        ).any { containsPhrase(normalized, it) }
    }

    private fun extractPickupValueFromCommand(rawText: String): LocationValue? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val currentLocationPattern = Regex(
            """(?:from|pickup\s+from|pickup\s+at|pickup\s+is|pickup\s+location\s+is|source\s+is|source)\s+(?:my\s+)?(?:current\s+)?location(?:\s+to\s+.+)?""",
            RegexOption.IGNORE_CASE
        )
        if (currentLocationPattern.containsMatchIn(trimmed) || isCurrentLocationRequest(trimmed)) {
            return LocationValue(
                rawText = "current location",
                isCurrentLocation = true,
                displayName = "Current location"
            )
        }

        val fromToPattern = Regex(
            """(?:from|pickup\s+from|pickup\s+at|pickup\s+is|pickup\s+location\s+is|source\s+is|source)\s+(.+?)(?=\s+\bto\b|$)""",
            RegexOption.IGNORE_CASE
        )
        fromToPattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
            val sanitized = sanitizeLocation(candidate)
            if (sanitized.isBlank()) return null
            return LocationValue(
                rawText = sanitized,
                isCurrentLocation = false,
                displayName = sanitized
            )
        }

        return null
    }

    private fun extractDropFromCommand(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val explicitFromToPattern = Regex(
            """(?:from|pickup\s+from|pickup\s+at|pickup\s+is|pickup\s+location\s+is|source\s+is|source)\s+(.+?)\s+to\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        explicitFromToPattern.find(trimmed)?.groupValues?.getOrNull(2)?.let { candidate ->
            return sanitizeLocation(candidate)
        }

        val dropPatterns = listOf(
            Regex("""(?:to|towards|destination\s+is|destination|drop\s+to|drop\s+at|drop\s+is)\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?:where\s+to|going\s+to)\s+(.+)$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in dropPatterns) {
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
                val sanitized = sanitizeLocation(candidate)
                if (sanitized.isNotBlank()) return sanitized
            }
        }

        return null
    }

    private fun parseRideTypeFromBookingCommand(rawText: String): RideType? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val beforeDestination = normalize(trimmed)
            .split(Regex("""\bto\b"""), limit = 2)
            .firstOrNull()
            .orEmpty()

        if (beforeDestination.isBlank()) return null

        return parseRideTypeReply(beforeDestination)
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
            "ride to",
            "hail a cab",
            "book the cheapest",
            "book cheapest",
            "book first one"
        )

        return cuePhrases.any { normalized.contains(it) }
    }

    private fun isLikelyLocationReply(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return false
        if (isWakeWordOrFillerOnly(trimmed)) return false

        val normalized = normalize(trimmed)
        if (containsBookingCue(normalized)) return false
        if (isAffirmative(trimmed) || isNegative(trimmed) || isPauseCommand(trimmed)) return false
        if (parseProviderChoiceReply(trimmed) != null) return false
        if (isCheapestChoice(trimmed) || isFirstChoice(trimmed)) return false
        if (parseRideTypeReply(trimmed) != null) return false

        return true
    }

    private fun sanitizeLocation(value: String?): String {
        val cleaned = value?.trim()
            ?.trimEnd('.', ',', '!', '?')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()

        if (cleaned.isBlank()) return ""

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
                return cleaned.substring(leadIn.length).trim()
            }
        }

        return cleaned
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalized.contains(Regex("""\b${Regex.escape(normalizedPhrase)}\b"""))
    }

    private fun containsWord(normalized: String, word: String): Boolean {
        return Regex("""\b${Regex.escape(word.lowercase(Locale.US))}\b""").containsMatchIn(normalized)
    }

    private fun isWakeWordOrFillerOnly(rawText: String): Boolean {
        val normalized = rawText.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return false

        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        val fillers = setOf(
            "luna",
            "nova",
            "hey",
            "hello",
            "hi",
            "okay",
            "ok"
        )

        return tokens.all { it in fillers }
    }
}
