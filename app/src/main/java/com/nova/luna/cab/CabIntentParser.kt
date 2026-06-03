package com.nova.luna.cab

import java.util.Locale

class CabIntentParser {
    fun parse(rawText: String): CabBookingRequest? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val command = extractCommand(trimmed) ?: return null
        val dropLocation = sanitizeLocation(command.dropLocation)

        return CabBookingRequest(
            rawText = rawText,
            dropLocation = dropLocation,
            rideType = command.rideType
        )
    }

    fun isCabBookingCommand(rawText: String): Boolean {
        return parse(rawText) != null
    }

    fun extractDropLocation(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val command = extractCommand(trimmed)
        if (command != null) {
            return sanitizeLocation(command.dropLocation)
        }

        if (extractRideType(trimmed) != null) return null
        if (parseProviderChoice(trimmed) != null) return null
        if (isCheapestChoice(trimmed)) return null
        if (isAffirmative(trimmed)) return null
        if (isNegative(trimmed)) return null

        return sanitizeLocation(trimmed)
    }

    fun extractRideType(rawText: String): RideType? {
        val normalized = normalize(rawText)
        val rideType = when {
            matchesReply(normalized, "auto") -> RideType.AUTO
            matchesReply(normalized, "bike") -> RideType.BIKE
            matchesReply(normalized, "mini") -> RideType.MINI
            matchesReply(normalized, "sedan") -> RideType.SEDAN
            matchesReply(normalized, "suv") -> RideType.SUV
            matchesReply(normalized, "any") -> RideType.ANY
            else -> null
        }

        return rideType
    }

    fun parseProviderChoice(rawText: String): CabProvider? {
        val normalized = normalize(rawText)
        return when {
            matchesReply(normalized, "uber") -> CabProvider.UBER
            matchesReply(normalized, "ola") -> CabProvider.OLA
            matchesReply(normalized, "rapido") -> CabProvider.RAPIDO
            matchesReply(normalized, "in drive") || matchesReply(normalized, "indrive") -> CabProvider.INDRIVE
            else -> null
        }
    }

    fun isCheapestChoice(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "cheapest",
            "lowest",
            "least expensive",
            "best price",
            "minimum fare",
            "cheaper option"
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
            "do it"
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

    private data class ParsedCommand(
        val rideType: RideType? = null,
        val dropLocation: String
    )

    private fun extractCommand(rawText: String): ParsedCommand? {
        val rideTypePattern = Regex(
            """^book\s+(?:a|an)?\s*(auto|bike|mini|sedan|suv)\s+to\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val genericPatterns = listOf(
            Regex("""^book\s+(?:a|an)?\s*(?:cab|ride)\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^get\s+me\s+a\s+ride\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^cab\s+to\s+(.+)$""", RegexOption.IGNORE_CASE)
        )

        rideTypePattern.matchEntire(rawText)?.let { match ->
            val rideType = match.groupValues[1].let { value ->
                RideType.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
            }
            val dropLocation = match.groupValues[2]
            if (dropLocation.isNotBlank()) {
                return ParsedCommand(rideType = rideType, dropLocation = dropLocation)
            }
        }

        genericPatterns.forEach { pattern ->
            pattern.matchEntire(rawText)?.let { match ->
                val dropLocation = match.groupValues[1]
                if (dropLocation.isNotBlank()) {
                    return ParsedCommand(dropLocation = dropLocation)
                }
            }
        }

        return null
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
            "drop is ",
            "drop location is ",
            "destination is ",
            "destination ",
            "from ",
            "to ",
            "at "
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
            """^(?:book\s+(?:the\s+)?|choose\s+|pick\s+|select\s+|use\s+|go\s+with\s+|say\s+)?$escaped(?:\s+(?:please|now|ride|cab|option))?$""",
            RegexOption.IGNORE_CASE
        )
        return replyPattern.matches(normalized)
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalized.contains(Regex("""\b${Regex.escape(normalizedPhrase)}\b"""))
    }
}
