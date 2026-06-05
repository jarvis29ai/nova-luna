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

        val rideTime = parseRideTimeReply(trimmed)
        val preference = parsePreferenceReply(trimmed)
        val passengerMode = parsePassengerModeReply(trimmed)
        val selectionIndex = parseSelectionIndexReply(trimmed)
        val compareOnly = isCompareOnlyRequest(trimmed)
        val manualPickupRequested = isManualPickupRequest(trimmed)
        val changePickupRequest = isChangePickupRequest(trimmed)
        val changeDropRequest = isChangeDropRequest(trimmed)
        val changeRideTypeRequest = isChangeRideTypeRequest(trimmed)
        val tryAnotherAppRequest = isTryAnotherAppRequest(trimmed)
        val safetyNotes = buildList {
            if (passengerMode == CabPassengerMode.SOMEONE_ELSE) {
                add("remote booking may need manual confirmation")
            }
            if (rideTime == CabRideTime.SCHEDULED) {
                add("scheduled cab details should be verified before booking")
            }
            if (compareOnly) {
                add("comparison only, do not auto-book")
            }
        }

        return CabIntentParseResult(
            rawText = rawText,
            isCabBooking = true,
            pickupText = pickupText,
            pickupMode = pickupMode,
            dropText = extractDropFromCommand(trimmed),
            rideType = parseRideTypeFromBookingCommand(trimmed),
            providerPreference = parseProviderChoiceReply(trimmed),
            rideTime = rideTime,
            scheduledTimeText = extractScheduledTime(trimmed),
            preference = preference,
            passengerMode = passengerMode,
            manualPickupRequested = manualPickupRequested,
            compareOnly = compareOnly,
            bookNow = isBookNowRequest(trimmed),
            scheduleLater = isScheduleLaterRequest(trimmed),
            safetyNotes = safetyNotes,
            selectionIndex = selectionIndex,
            wantsCheapest = isCheapestChoice(trimmed),
            wantsFastest = isFastestChoice(trimmed),
            wantsComfortable = isComfortableChoice(trimmed),
            wantsFirstOne = isFirstChoice(trimmed) || selectionIndex == 1,
            changePickupRequest = changePickupRequest,
            changeDropRequest = changeDropRequest,
            changeRideTypeRequest = changeRideTypeRequest,
            tryAnotherAppRequest = tryAnotherAppRequest
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
        if (isCheapestChoice(trimmed) || isFastestChoice(trimmed) || isComfortableChoice(trimmed)) return null
        if (isFirstChoice(trimmed) || parseSelectionIndexReply(trimmed) != null) return null
        if (isCompareOnlyRequest(trimmed) || isBookNowRequest(trimmed) || isScheduleLaterRequest(trimmed)) return null
        if (isManualPickupRequest(trimmed) || isChangePickupRequest(trimmed) || isChangeDropRequest(trimmed)) return null
        if (isChangeRideTypeRequest(trimmed) || isTryAnotherAppRequest(trimmed)) return null

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

    fun parseRideTimeReply(rawText: String): CabRideTime? {
        val normalized = normalize(rawText)
        return when {
            containsPhrase(normalized, "schedule later") ||
                containsPhrase(normalized, "book later") ||
                containsPhrase(normalized, "later") ||
                containsPhrase(normalized, "schedule cab") ||
                containsPhrase(normalized, "for later") ||
                containsPhrase(normalized, "tomorrow") ||
                containsPhrase(normalized, "tonight") -> CabRideTime.SCHEDULED

            containsPhrase(normalized, "book now") ||
                containsPhrase(normalized, "right now") ||
                containsPhrase(normalized, "now") ||
                containsPhrase(normalized, "use now") -> CabRideTime.NOW

            else -> null
        }
    }

    fun parsePreferenceReply(rawText: String): CabRidePreference? {
        val normalized = normalize(rawText)
        return when {
            isCheapestChoice(rawText) || containsPhrase(normalized, "cheapest") ||
                containsPhrase(normalized, "lowest price") ||
                containsPhrase(normalized, "lowest fare") -> CabRidePreference.CHEAPEST

            isFastestChoice(rawText) || containsPhrase(normalized, "fastest") ||
                containsPhrase(normalized, "quickest") ||
                containsPhrase(normalized, "fastest available") -> CabRidePreference.FASTEST

            containsPhrase(normalized, "preferred app") ||
                containsPhrase(normalized, "preferred provider") ||
                containsPhrase(normalized, "preferred cab app") -> CabRidePreference.PROVIDER_SPECIFIC

            isComfortableChoice(rawText) || containsPhrase(normalized, "comfortable") ||
                containsPhrase(normalized, "comfort") -> CabRidePreference.COMFORTABLE

            parseProviderChoiceReply(rawText) != null -> CabRidePreference.PROVIDER_SPECIFIC

            else -> null
        }
    }

    fun parsePassengerModeReply(rawText: String): CabPassengerMode {
        val normalized = normalize(rawText)
        return if (containsPhrase(normalized, "someone else") ||
            containsPhrase(normalized, "for someone else") ||
            containsPhrase(normalized, "for my friend") ||
            containsPhrase(normalized, "for another person") ||
            containsPhrase(normalized, "different location")
        ) {
            CabPassengerMode.SOMEONE_ELSE
        } else {
            CabPassengerMode.SELF
        }
    }

    fun parseSelectionIndexReply(rawText: String): Int? {
        val normalized = normalize(rawText)
        return when {
            containsPhrase(normalized, "first one") ||
                containsPhrase(normalized, "first option") ||
                containsPhrase(normalized, "option one") ||
                containsPhrase(normalized, "choose first") ||
                containsPhrase(normalized, "pick first") -> 1

            containsPhrase(normalized, "second one") ||
                containsPhrase(normalized, "second option") ||
                containsPhrase(normalized, "option two") ||
                containsPhrase(normalized, "choose second") ||
                containsPhrase(normalized, "pick second") -> 2

            containsPhrase(normalized, "third one") ||
                containsPhrase(normalized, "third option") ||
                containsPhrase(normalized, "option three") ||
                containsPhrase(normalized, "choose third") ||
                containsPhrase(normalized, "pick third") -> 3

            else -> null
        }
    }

    fun parseProviderChoiceReply(rawText: String): CabProvider? {
        val normalized = normalize(rawText)
        val candidates = listOfNotNull(
            matchProviderMention(normalized, CabProvider.UBER, listOf("uber")),
            matchProviderMention(normalized, CabProvider.OLA, listOf("ola")),
            matchProviderMention(normalized, CabProvider.RAPIDO, listOf("rapido")),
            matchProviderMention(normalized, CabProvider.INDRIVE, listOf("in drive", "indrive"))
        )

        return candidates.minByOrNull { it.index }?.provider
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

    fun extractScheduledTime(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        if (!isScheduleLaterRequest(trimmed) && !looksLikeScheduledTimeText(trimmed)) {
            return null
        }

        val scheduledPattern = Regex(
            """(?:at|on|around|by)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        scheduledPattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
            val sanitized = candidate.trim().trimEnd('.', ',', '!', '?')
            if (sanitized.isNotBlank() && looksLikeScheduledTimeText(sanitized)) return sanitized
        }

        return when {
            isScheduleLaterRequest(trimmed) -> "later"
            isBookNowRequest(trimmed) -> "now"
            else -> null
        }
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

    fun isFastestChoice(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "fastest",
            "fastest available",
            "fastest ride",
            "quickest",
            "quickest available",
            "book fastest",
            "book the fastest"
        ).any { containsPhrase(normalized, it) }
    }

    fun isComfortableChoice(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return listOf(
            "comfortable",
            "comfort",
            "best comfort",
            "more comfortable"
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

    fun isChangeRideTypeRequest(rawText: String): Boolean {
        return isChangeRideRequest(rawText) ||
            containsPhrase(normalize(rawText), "change cab type") ||
            containsPhrase(normalize(rawText), "change vehicle type")
    }

    fun isChangeCabTypeRequest(rawText: String): Boolean {
        return isChangeRideTypeRequest(rawText)
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

    fun isManualPickupRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsPhrase(normalized, "manual pickup") ||
            containsPhrase(normalized, "use manual pickup") ||
            containsPhrase(normalized, "manual location") ||
            containsPhrase(normalized, "type pickup") ||
            containsPhrase(normalized, "enter pickup manually")
    }

    fun isCompareOnlyRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsPhrase(normalized, "compare only") ||
            containsPhrase(normalized, "compare") ||
            containsPhrase(normalized, "just compare") ||
            containsPhrase(normalized, "compare fares only") ||
            containsPhrase(normalized, "show comparison") ||
            containsPhrase(normalized, "no booking yet") ||
            containsPhrase(normalized, "compare available options")
    }

    fun isBookNowRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsPhrase(normalized, "book now") ||
            containsPhrase(normalized, "book it now") ||
            containsPhrase(normalized, "book today") ||
            containsPhrase(normalized, "confirm now")
    }

    fun isScheduleLaterRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsPhrase(normalized, "schedule later") ||
            containsPhrase(normalized, "book later") ||
            containsPhrase(normalized, "later") ||
            containsPhrase(normalized, "schedule cab") ||
            containsPhrase(normalized, "for later")
    }

    fun isTryAnotherAppRequest(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return containsPhrase(normalized, "try another app") ||
            containsPhrase(normalized, "another app") ||
            containsPhrase(normalized, "switch app") ||
            containsPhrase(normalized, "try the next app")
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
            "compare fares",
            "search again",
            "try another app",
            "try the next app"
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
            "compare",
            "compare fares",
            "compare rides",
            "find cheapest",
            "find fastest",
            "search fares",
            "search cab",
            "show fares",
            "book the cheapest",
            "book cheapest",
            "book first one",
            "book now",
            "schedule cab",
            "manual pickup",
            "use manual pickup",
            "choose first",
            "choose second",
            "choose third",
            "choose cheapest",
            "choose fastest",
            "choose comfortable",
            "change pickup",
            "change destination",
            "change cab type",
            "try another app",
            "current location",
            "use current location"
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
        if (isCheapestChoice(trimmed) || isFastestChoice(trimmed) || isComfortableChoice(trimmed)) return false
        if (isFirstChoice(trimmed) || parseSelectionIndexReply(trimmed) != null) return false
        if (parseRideTypeReply(trimmed) != null) return false
        if (isCompareOnlyRequest(trimmed) || isBookNowRequest(trimmed) || isScheduleLaterRequest(trimmed)) return false
        if (isManualPickupRequest(trimmed) || isChangePickupRequest(trimmed) || isChangeDropRequest(trimmed)) return false
        if (isChangeRideTypeRequest(trimmed) || isTryAnotherAppRequest(trimmed)) return false

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

    private fun looksLikeScheduledTimeText(value: String): Boolean {
        val normalized = normalize(value)
        return Regex("""\b\d{1,2}(:\d{2})?\s*(?:am|pm)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            listOf("today", "tomorrow", "tonight", "morning", "afternoon", "evening", "night", "later", "now").any {
                containsPhrase(normalized, it)
            }
    }

    private fun containsWord(normalized: String, word: String): Boolean {
        return Regex("""\b${Regex.escape(word.lowercase(Locale.US))}\b""").containsMatchIn(normalized)
    }

    private fun matchProviderMention(
        normalized: String,
        provider: CabProvider,
        tokens: List<String>
    ): ProviderMention? {
        val matches = tokens.mapNotNull { token ->
            val pattern = Regex("""\b${Regex.escape(token.lowercase(Locale.US))}\b""")
            pattern.find(normalized)?.range?.first
        }

        val index = matches.minOrNull() ?: return null
        return ProviderMention(provider, index)
    }

    private data class ProviderMention(
        val provider: CabProvider,
        val index: Int
    )

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
