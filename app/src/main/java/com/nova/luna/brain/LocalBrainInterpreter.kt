package com.nova.luna.brain

import com.nova.luna.cab.CabIntentParser
import com.nova.luna.cab.CabProvider
import com.nova.luna.cab.RideType
import com.nova.luna.grocery.GroceryBookingVoiceResponses
import com.nova.luna.grocery.GroceryIntentParseResult
import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class LocalBrainInterpreter {
    private val legacyParser = RuleBasedCommandParser()
    private val cabIntentParser = CabIntentParser()
    private val groceryIntentParser = GroceryIntentParser()

    fun interpret(request: BrainRequest): BrainAction {
        val rawText = request.rawText.trim()
        if (rawText.isBlank()) {
            return buildUnknownAction(rawText)
        }

        if (request.activeCabSession) {
            return BrainAction(
                intent = "cab_session",
                reply = "Continuing the cab flow.",
                actionType = BrainActionType.READ_ONLY,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf("rawText" to rawText)
            )
        }

        if (request.activeGrocerySession) {
            return BrainAction(
                intent = "grocery_session",
                reply = "Continuing the grocery flow.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf("rawText" to rawText, "activeGrocerySession" to "true")
            )
        }

        parseBlockedAction(rawText)?.let { return it }
        parseCabBooking(rawText)?.let { return it }
        parseCabComparison(rawText)?.let { return it }
        parseGroceryBooking(rawText)?.let { return it }
        parsePreparedMessage(rawText)?.let { return it }
        parseLegacyCommand(rawText)?.let { return it }

        return buildUnknownAction(rawText)
    }

    private fun parseBlockedAction(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        if (Regex("""\bpay\b\s+\d+""").containsMatchIn(normalized)) {
            return BrainAction(
                intent = "human_only",
                reply = "That needs to stay manual for your safety. Please handle it yourself in the app.",
                actionType = BrainActionType.HUMAN_ONLY,
                riskLevel = BrainRiskLevel.BLOCKED,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf(
                    "rawText" to rawText,
                    "reason" to "sensitive_action"
                ),
                nextQuestion = "Please do that manually."
            )
        }

        val blockedPatterns = listOf(
            "send money",
            "payment",
            "pay now",
            "pay with",
            "banking",
            "bank transfer",
            "upi",
            "password",
            "otp",
            "one time password",
            "captcha",
            "login",
            "sign in",
            "delete",
            "erase",
            "remove account"
        )

        if (blockedPatterns.any { containsPhrase(normalized, it) }) {
            return BrainAction(
                intent = "human_only",
                reply = "That needs to stay manual for your safety. Please handle it yourself in the app.",
                actionType = BrainActionType.HUMAN_ONLY,
                riskLevel = BrainRiskLevel.BLOCKED,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf(
                    "rawText" to rawText,
                    "reason" to "sensitive_action"
                ),
                nextQuestion = "Please do that manually."
            )
        }

        return null
    }

    private fun parseCabBooking(rawText: String): BrainAction? {
        val parsed = cabIntentParser.parseInitialCabRequest(rawText) ?: return null
        val params = buildMap {
            putAll(parsed.toEntities())
            put("rawText", rawText)
            parsed.pickupText?.takeIf { it.isNotBlank() }?.let { put("pickupLocation", it) }
            parsed.dropText?.takeIf { it.isNotBlank() }?.let { put("dropLocation", it) }
            parsed.rideType?.let { put("rideType", it.name) }
            parsed.providerPreference?.let { put("preferredProvider", it.name) }
            put("wantsCheapest", parsed.wantsCheapest.toString())
            put("wantsFirstOne", parsed.wantsFirstOne.toString())
        }

        val nextQuestion = when {
            parsed.pickupText.isNullOrBlank() -> "Where should I pick you up from?"
            parsed.dropText.isNullOrBlank() -> "Where do you want to go?"
            parsed.rideType == null -> "Which ride type should I use?"
            else -> "Say yes and I will prepare the cab flow."
        }

        val reply = when {
            parsed.pickupText.isNullOrBlank() -> buildCabReply("I can prepare that cab, but I need your pickup location first.", parsed)
            parsed.dropText.isNullOrBlank() -> buildCabReply("I can prepare that cab, but I need the destination first.", parsed)
            parsed.rideType == null -> buildCabReply("I can prepare that cab, but I need the ride type first.", parsed)
            else -> buildCabReply("I can prepare a cab to ${parsed.dropText}. I will stop before booking or payment.", parsed)
        }

        return BrainAction(
            intent = "cab_booking",
            reply = reply,
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = params,
            nextQuestion = nextQuestion
        )
    }

    private fun parseCabComparison(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        if (!normalized.contains("compare")) return null

        val providers = CabProvider.values().filter { provider ->
            when (provider) {
                CabProvider.INDRIVE -> normalized.contains("indrive") || normalized.contains("in drive")
                else -> normalized.contains(provider.name.lowercase(Locale.US))
            }
        }
        val destination = extractDestination(rawText)
        if (providers.isEmpty() && destination.isNullOrBlank()) return null

        val providerNames = providers.joinToString(separator = ",") { it.name }
        val params = buildMap {
            put("rawText", rawText)
            if (providerNames.isNotBlank()) {
                put("providers", providerNames)
            }
            destination?.takeIf { it.isNotBlank() }?.let { put("destination", it) }
        }

        val providerLabel = if (providers.isNotEmpty()) {
            providers.joinToString(separator = " and ") { it.displayName() }
        } else {
            "the installed providers"
        }

        val reply = if (destination.isNullOrBlank()) {
            "I can compare ${providerLabel} once you tell me the destination. I will stop before booking."
        } else {
            "I can compare ${providerLabel} for $destination. I will stop before booking or payment."
        }

        val nextQuestion = when {
            destination.isNullOrBlank() -> "Where do you want to go?"
            providers.isEmpty() -> "Which ride apps should I compare?"
            else -> "Say yes and I will compare the fares."
        }

        return BrainAction(
            intent = "cab_compare",
            reply = reply,
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = params,
            nextQuestion = nextQuestion
        )
    }

    private fun parsePreparedMessage(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        val messageCue = listOf(
            "prepare message",
            "compose message",
            "draft message",
            "prepare a message",
            "compose a message",
            "draft a message"
        ).any { normalized.contains(it) }
        if (!messageCue) return null

        val appName = extractOpenAppName(rawText)
            ?: if (normalized.contains("whatsapp")) "whatsapp" else null
        val contact = extractMessageContact(rawText)
        val message = extractMessageBody(rawText)

        val params = buildMap {
            put("rawText", rawText)
            appName?.let {
                put("appName", it)
                put("query", it)
            }
            contact?.takeIf { it.isNotBlank() }?.let { put("contact", it) }
            message?.takeIf { it.isNotBlank() }?.let { put("message", it) }
        }

        val appLabel = appName?.replaceFirstChar { it.titlecase(Locale.US) } ?: "the app"
        val contactLabel = contact?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.titlecase(Locale.US) }
            ?: "that contact"
        val reply = when {
            message.isNullOrBlank() -> "I can open $appLabel and get a message ready for $contactLabel. What should I draft?"
            else -> "I can open $appLabel and prepare the message for $contactLabel. I will not send it."
        }

        return BrainAction(
            intent = "prepare_message",
            reply = reply,
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = params,
            nextQuestion = if (message.isNullOrBlank()) {
                "What should I say to $contactLabel?"
            } else {
                "Say yes and I will open the app and prepare the draft."
            }
        )
    }

    private fun parseGroceryBooking(rawText: String): BrainAction? {
        val parsed = groceryIntentParser.parseInitialGroceryRequest(rawText) ?: return null
        val compareRequested = parsed.compareRequested || parsed.wantsCheapest || parsed.wantsFirstOne
        val intent = when {
            compareRequested -> "grocery_compare"
            else -> "grocery_booking"
        }
        val params = buildMap {
            putAll(parsed.toEntities())
            put("rawText", rawText)
        }

        val reply = when {
            parsed.basket.items.isEmpty() -> "I can start the grocery flow and ask you for items."
            compareRequested -> "I can compare grocery providers locally and keep checkout manual."
            else -> "I can prepare the grocery flow and keep the final step manual."
        }

        val nextQuestion = when {
            parsed.basket.items.isEmpty() -> GroceryBookingVoiceResponses.askItems()
            parsed.requiresBrandQuestion -> GroceryBookingVoiceResponses.askBrandPreference()
            compareRequested -> "Say yes and I will compare grocery providers."
            else -> "I will stop before checkout."
        }

        return BrainAction(
            intent = intent,
            reply = reply,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            nextQuestion = nextQuestion
        )
    }

    private fun parseLegacyCommand(rawText: String): BrainAction? {
        val commandIntent = legacyParser.parse(rawText)
        return when (commandIntent.actionType) {
            ActionType.STOP_SERVICE -> BrainAction(
                intent = "stop_service",
                reply = "Stopping listening.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = true,
                params = commandIntent.entities + mapOf("rawText" to rawText)
            )
            ActionType.LAUNCH_APP -> {
                val appName = commandIntent.entities["appName"]
                    ?: commandIntent.entities["query"]
                    ?: rawText
                BrainAction(
                    intent = "open_app",
                    reply = "Opening ${appName.replaceFirstChar { it.titlecase(Locale.US) }}.",
                    actionType = BrainActionType.EXTERNAL_ACTION,
                    riskLevel = BrainRiskLevel.SAFE,
                    requiresConfirmation = false,
                    finalActionAllowed = true,
                    params = commandIntent.entities + mapOf(
                        "rawText" to rawText,
                        "appName" to appName,
                        "query" to appName
                    )
                )
            }

            ActionType.GO_HOME -> navigationBrainAction(rawText, commandIntent, "go_home", "Going home.")
            ActionType.GO_BACK -> navigationBrainAction(rawText, commandIntent, "go_back", "Going back.")
            ActionType.OPEN_RECENTS -> navigationBrainAction(rawText, commandIntent, "open_recents", "Opening recent apps.")
            ActionType.OPEN_NOTIFICATIONS -> navigationBrainAction(rawText, commandIntent, "open_notifications", "Opening notifications.")
            ActionType.READ_NOTIFICATIONS -> navigationBrainAction(rawText, commandIntent, "read_notifications", "Reading notifications.")
            ActionType.SCROLL_FORWARD -> interactionBrainAction(rawText, commandIntent, "scroll_forward", "Scrolling down.")
            ActionType.SCROLL_BACKWARD -> interactionBrainAction(rawText, commandIntent, "scroll_backward", "Scrolling up.")
            ActionType.CLICK_TEXT -> {
                val target = commandIntent.entities["text"].orEmpty()
                if (isDangerousFinalText(target)) {
                    BrainAction(
                        intent = "human_only",
                        reply = "That button looks like a final action, so please tap it yourself.",
                        actionType = BrainActionType.HUMAN_ONLY,
                        riskLevel = BrainRiskLevel.BLOCKED,
                        requiresConfirmation = false,
                        finalActionAllowed = false,
                        params = commandIntent.entities + mapOf("rawText" to rawText, "text" to target),
                        nextQuestion = "Please handle that step manually."
                    )
                } else {
                    interactionBrainAction(rawText, commandIntent, "tap_text", "Tapping $target.")
                }
            }
            ActionType.TYPE_TEXT -> {
                val text = commandIntent.entities["text"].orEmpty()
                if (isDangerousFinalText(text)) {
                    BrainAction(
                        intent = "human_only",
                        reply = "That text looks sensitive, so please enter it yourself.",
                        actionType = BrainActionType.HUMAN_ONLY,
                        riskLevel = BrainRiskLevel.BLOCKED,
                        requiresConfirmation = false,
                        finalActionAllowed = false,
                        params = commandIntent.entities + mapOf("rawText" to rawText, "text" to text),
                        nextQuestion = "Please type that manually."
                    )
                } else {
                    interactionBrainAction(rawText, commandIntent, "type_text", "Typing text.")
                }
            }
            ActionType.OPEN_SETTINGS -> simpleBrainAction(rawText, commandIntent, "open_settings", "Opening settings.", false)
            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> simpleBrainAction(rawText, commandIntent, "open_accessibility_settings", "Opening accessibility settings.", false)
            ActionType.OPEN_USAGE_ACCESS_SETTINGS -> simpleBrainAction(rawText, commandIntent, "open_usage_access_settings", "Opening usage access settings.", false)
            ActionType.CALL_CONTACT -> BrainAction(
                intent = "call_contact",
                reply = "I can prepare a call, but call automation stays manual in this build.",
                actionType = BrainActionType.PREPARE,
                riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
                requiresConfirmation = true,
                finalActionAllowed = false,
                params = commandIntent.entities + mapOf("rawText" to rawText),
                nextQuestion = "Say yes if you want me to prepare the call flow."
            )
            ActionType.TAKE_SCREENSHOT -> BrainAction(
                intent = "take_screenshot",
                reply = "I can try a screenshot, but it needs your confirmation first.",
                actionType = BrainActionType.PREPARE,
                riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
                requiresConfirmation = true,
                finalActionAllowed = false,
                params = commandIntent.entities + mapOf("rawText" to rawText),
                nextQuestion = "Say yes if you want me to continue."
            )
            ActionType.BLOCKED -> BrainAction(
                intent = "human_only",
                reply = "That action must stay manual.",
                actionType = BrainActionType.HUMAN_ONLY,
                riskLevel = BrainRiskLevel.BLOCKED,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = commandIntent.entities + mapOf("rawText" to rawText),
                nextQuestion = "Please handle it yourself."
            )
            else -> null
        }
    }

    private fun navigationBrainAction(
        rawText: String,
        commandIntent: CommandIntent,
        intent: String,
        reply: String
    ): BrainAction {
        return simpleBrainAction(rawText, commandIntent, intent, reply, false)
    }

    private fun interactionBrainAction(
        rawText: String,
        commandIntent: CommandIntent,
        intent: String,
        reply: String
    ): BrainAction {
        return simpleBrainAction(rawText, commandIntent, intent, reply, false)
    }

    private fun simpleBrainAction(
        rawText: String,
        commandIntent: CommandIntent,
        intent: String,
        reply: String,
        confirmationRequired: Boolean
    ): BrainAction {
        return BrainAction(
            intent = intent,
            reply = reply,
            actionType = if (confirmationRequired) BrainActionType.PREPARE else BrainActionType.EXTERNAL_ACTION,
            riskLevel = if (confirmationRequired) BrainRiskLevel.CONFIRMATION_REQUIRED else BrainRiskLevel.SAFE,
            requiresConfirmation = confirmationRequired,
            finalActionAllowed = !confirmationRequired,
            params = commandIntent.entities + mapOf("rawText" to rawText)
        )
    }

    private fun buildUnknownAction(rawText: String): BrainAction {
        return BrainAction(
            intent = "unknown",
            reply = "I did not quite catch that. Please try again.",
            actionType = BrainActionType.NONE,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to rawText),
            nextQuestion = "What would you like me to do?"
        )
    }

    private fun extractOpenAppName(rawText: String): String? {
        val firstClause = rawText.split(Regex("""\band\b""", RegexOption.IGNORE_CASE), limit = 2).firstOrNull().orEmpty()
        val normalizedClause = normalize(firstClause)
        if (!normalizedClause.startsWith("open") &&
            !normalizedClause.startsWith("launch") &&
            !normalizedClause.startsWith("start")
        ) {
            return null
        }

        val appName = normalizedClause
            .removePrefix("open app ")
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .trim()

        return appName.takeIf { it.isNotBlank() }
    }

    private fun extractMessageContact(rawText: String): String? {
        val normalized = normalize(rawText)
        val patterns = listOf(
            Regex("""(?:prepare|compose|draft)\s+message\s+to\s+(.+?)(?=\s+(?:saying|that|with|about|for)\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""message\s+to\s+(.+?)(?=\s+(?:saying|that|with|about|for)\b|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.let { candidate ->
                val cleaned = sanitize(candidate)
                if (cleaned.isNotBlank()) return cleaned
            }
        }

        if (normalized.contains("whatsapp") && normalized.contains("mom")) {
            return "mom"
        }

        return null
    }

    private fun extractMessageBody(rawText: String): String? {
        val quotedPattern = Regex("""["“](.+?)["”]""")
        quotedPattern.find(rawText)?.groupValues?.getOrNull(1)?.let { quoted ->
            val cleaned = sanitize(quoted)
            if (cleaned.isNotBlank()) return cleaned
        }

        val colonIndex = rawText.indexOf(':')
        if (colonIndex >= 0 && colonIndex < rawText.lastIndex) {
            val afterColon = sanitize(rawText.substring(colonIndex + 1))
            if (afterColon.isNotBlank()) return afterColon
        }

        val normalized = normalize(rawText)
        val messageMarkers = listOf("say ", "say that ")
        for (marker in messageMarkers) {
            val index = normalized.indexOf(marker)
            if (index >= 0) {
                val candidate = sanitize(rawText.substring(index + marker.length))
                if (candidate.isNotBlank()) return candidate
            }
        }

        return null
    }

    private fun extractDestination(rawText: String): String? {
        val patterns = listOf(
            Regex("""\bto\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""\bdestination\s+is\s+(.+)$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(rawText)?.groupValues?.getOrNull(1)?.let { candidate ->
                val cleaned = sanitize(candidate)
                if (cleaned.isNotBlank()) return cleaned
            }
        }

        return null
    }

    private fun isDangerousFinalText(text: String): Boolean {
        val normalized = normalize(text)
        val dangerousPatterns = listOf(
            "send money",
            "payment",
            "pay now",
            "pay with",
            "banking",
            "bank transfer",
            "upi",
            "password",
            "otp",
            "captcha",
            "login",
            "sign in",
            "delete",
            "erase",
            "remove account",
            "confirm booking",
            "book now",
            "book ride",
            "submit",
            "request ride",
            "request now",
            "final booking",
            "complete payment"
        )

        return dangerousPatterns.any { containsPhrase(normalized, it) }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun sanitize(value: String): String {
        return value.trim()
            .trimEnd('.', ',', '!', '?')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
    }

    private fun buildCabReply(prefix: String, parsed: com.nova.luna.cab.CabIntentParseResult): String {
        val extras = buildList {
            if (parsed.wantsCheapest) {
                add("I can compare the cheapest options.")
            }
            if (parsed.wantsFirstOne) {
                add("I can take the first available option.")
            }
            parsed.rideType?.let { add("Ride type: ${it.displayName()}.") }
        }
        return (listOf(prefix) + extras).joinToString(separator = " ").trim()
    }

    private fun CabProvider.displayName(): String {
        return when (this) {
            CabProvider.UBER -> "Uber"
            CabProvider.OLA -> "Ola"
            CabProvider.RAPIDO -> "Rapido"
            CabProvider.INDRIVE -> "inDrive"
        }
    }

    private fun RideType.displayName(): String {
        return when (this) {
            RideType.AUTO -> "Auto"
            RideType.BIKE -> "Bike"
            RideType.MINI -> "Mini"
            RideType.SEDAN -> "Sedan"
            RideType.SUV -> "SUV"
            RideType.ANY -> "Any"
        }
    }
}
