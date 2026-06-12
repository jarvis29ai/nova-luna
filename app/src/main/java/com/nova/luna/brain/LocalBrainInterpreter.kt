package com.nova.luna.brain

import com.nova.luna.cab.CabIntentParser
import com.nova.luna.cab.CabProvider
import com.nova.luna.cab.RideType
import com.nova.luna.cab.PickupMode
import com.nova.luna.grocery.GroceryBookingVoiceResponses
import com.nova.luna.grocery.GroceryIntentParseResult
import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.food.toEntities
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
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
    private val foodIntentParser = com.nova.luna.food.FoodIntentParser()

    fun interpret(request: BrainRequest): BrainAction {
        val rawText = request.rawText.trim()
        if (rawText.isBlank()) {
            return buildUnknownAction(rawText)
        }

        // 1. Safety blocked actions
        parseBlockedAction(rawText)?.let { return it }

        // 2. Active session continuations
        if (request.activeCabSession) {
            return brainAction(
                request.rawText,
                intent = "cab_session",
                reply = "Continuing the cab flow.",
                type = BrainActionType.EXTERNAL_ACTION,
                risk = BrainRiskLevel.LOW,
                confirm = false,
                finalAllowed = false
            )
        }

        if (request.activeGrocerySession) {
            return brainAction(
                request.rawText,
                intent = "grocery_session",
                reply = "Continuing the grocery flow.",
                type = BrainActionType.EXTERNAL_ACTION,
                risk = BrainRiskLevel.LOW,
                confirm = false,
                finalAllowed = false,
                params = mapOf("activeGrocerySession" to "true")
            )
        }

        if (request.activeFoodSession) {
            return brainAction(
                request.rawText,
                intent = "food_session",
                reply = "Continuing the food flow.",
                type = BrainActionType.EXTERNAL_ACTION,
                risk = BrainRiskLevel.LOW,
                confirm = false,
                finalAllowed = false,
                params = mapOf("activeFoodSession" to "true")
            )
        }

        // 3. Domain Scoring for Initial Routing
        val normalized = normalize(rawText)
        val foodScore = calculateFoodScore(normalized)
        val cabScore = calculateCabScore(normalized)
        val groceryScore = calculateGroceryScore(normalized)

        val maxScore = maxOf(foodScore, cabScore, groceryScore)

        if (maxScore > 0) {
            // Prioritize the clear winner
            if (foodScore == maxScore && foodScore > cabScore && foodScore > groceryScore) {
                parseFoodBooking(rawText)?.let { return it }
            }
            if (cabScore == maxScore && cabScore > foodScore && cabScore > groceryScore) {
                parseCabComparison(rawText)?.let { return it }
                parseCabBooking(rawText)?.let { return it }
            }
            if (groceryScore == maxScore && groceryScore > foodScore && groceryScore > cabScore) {
                parseGroceryBooking(rawText)?.let { return it }
            }

            // If tie or primary failed, try in order of scores
            val candidates = mutableListOf<Pair<String, Int>>()
            candidates.add("food" to foodScore)
            candidates.add("cab" to cabScore)
            candidates.add("grocery" to groceryScore)
            candidates.sortByDescending { it.second }

            for (candidate in candidates) {
                if (candidate.second == 0) break
                when (candidate.first) {
                    "food" -> parseFoodBooking(rawText)?.let { return it }
                    "cab" -> {
                        parseCabComparison(rawText)?.let { return it }
                        parseCabBooking(rawText)?.let { return it }
                    }
                    "grocery" -> parseGroceryBooking(rawText)?.let { return it }
                }
            }
        }

        // 4. Fallbacks
        parsePreparedMessage(rawText)?.let { return it }
        parseLegacyCommand(rawText)?.let { return it }

        return buildUnknownAction(rawText)
    }

    private fun calculateFoodScore(normalized: String): Int {
        var score = 0
        val foodItems = listOf(
            "pizza", "burger", "biryani", "dosa", "meal", "chicken", "rice", "pasta", "noodles",
            "sandwich", "salad", "soup", "taco", "sushi", "cake", "dessert", "ice cream",
            "juice", "shake", "pancakes", "waffle", "paratha", "thali", "momo", "curry", "paneer"
        )
        val foodProviders = listOf("swiggy", "zomato", "toings", "domino", "pizza hut", "kfc", "burger king", "starbucks", "subway")
        
        foodItems.forEach { if (containsPhrase(normalized, it)) score += 2 }
        foodProviders.forEach { if (containsPhrase(normalized, it)) score += 2 }
        if (containsPhrase(normalized, "food") || containsPhrase(normalized, "restaurant")) score += 2
        return score
    }

    private fun calculateCabScore(normalized: String): Int {
        var score = 0
        val cabItems = listOf("cab", "ride", "taxi", "auto", "bike", "rickshaw", "fare")
        val cabProviders = listOf("uber", "ola", "rapido", "indrive")
        
        cabItems.forEach { if (containsPhrase(normalized, it)) score += 2 }
        cabProviders.forEach { if (containsPhrase(normalized, it)) score += 2 }
        if (extractDestination(normalized) != null) score += 2
        return score
    }

    private fun calculateGroceryScore(normalized: String): Int {
        var score = 0
        val groceryItems = listOf("milk", "bread", "sugar", "atta", "dal", "oil", "eggs", "grocery", "groceries", "cart", "basket")
        val groceryProviders = listOf("blinkit", "zepto", "instamart", "jiomart", "bigbasket")
        
        groceryItems.forEach { if (containsPhrase(normalized, it)) score += 2 }
        groceryProviders.forEach { if (containsPhrase(normalized, it)) score += 2 }
        return score
    }

    private fun parseFoodBooking(rawText: String): BrainAction? {
        val parsed = foodIntentParser.parse(rawText) ?: return null
        val params = buildMap {
            putAll(parsed.toEntities())
            put("rawText", rawText)
        }

        val compareRequested = parsed.requestedProviders.size > 1 || rawText.lowercase(Locale.US).contains("compare")

        val reply = when {
            parsed.foodItem.isNullOrBlank() && parsed.restaurantName.isNullOrBlank() ->
                "I can help with food, but I need to know what you want to eat or from which restaurant."
            compareRequested -> "I can compare food prices across Swiggy and Zomato for you."
            else -> "I can help you order ${parsed.foodItem ?: "food"} from ${parsed.restaurantName ?: "a restaurant"}."
        }

        return brainAction(
            rawText = rawText,
            intent = "food_order",
            reply = reply,
            type = BrainActionType.EXTERNAL_ACTION,
            risk = BrainRiskLevel.LOW,
            confirm = false,
            finalAllowed = false,
            params = params
        )
    }

    private fun parseBlockedAction(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        val blockedPatterns = listOf(
            "send money", "payment", "pay now", "pay with", "complete payment",
            "banking", "bank transfer", "upi", "password", "otp", "one time password",
            "enter otp", "captcha", "solve captcha", "login", "sign in", "bypass login",
            "place order without", "delete", "erase", "remove account"
        )

        if (Regex("""\bpay\b\s+\d+""").containsMatchIn(normalized) || blockedPatterns.any { containsPhrase(normalized, it) }) {
            return brainAction(
                rawText = rawText,
                intent = "human_only",
                reply = "That needs to stay manual for your safety. Please handle it yourself in the app.",
                type = BrainActionType.HUMAN_ONLY,
                risk = BrainRiskLevel.HUMAN_ONLY,
                confirm = true,
                finalAllowed = false,
                params = mapOf("reason" to "sensitive_action")
            )
        }

        return null
    }

    private fun parseCabComparison(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        if (!normalized.contains("compare")) return null

        val providers = CabProvider.values().filter { provider ->
            normalized.contains(provider.name.lowercase(Locale.US))
        }

        val destination = extractDestination(rawText)
        if (providers.isEmpty() && destination.isNullOrBlank()) return null

        val providerNames = providers.joinToString(separator = " and ") { provider ->
            provider.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }

        val reply = when {
            destination != null && providerNames.isNotBlank() ->
                "I'll compare $providerNames cab options to $destination for you."
            destination != null ->
                "I'll compare cab options to $destination for you."
            providerNames.isNotBlank() ->
                "I'll compare $providerNames cab options for you."
            else ->
                "I can compare cab options across available providers."
        }

        return brainAction(
            rawText = rawText,
            intent = "cab_compare",
            reply = reply,
            type = BrainActionType.EXTERNAL_ACTION,
            risk = BrainRiskLevel.LOW,
            confirm = false,
            finalAllowed = false,
            params = mapOf(
                "destination" to (destination ?: ""),
                "providers" to providers.joinToString(separator = ",") { it.name }
            ),
            nextQuestion = "Would you like me to book the cheapest one?"
        )
    }

    private fun parseCabBooking(rawText: String): BrainAction? {
        val parsed = cabIntentParser.parseInitialCabRequest(rawText) ?: return null
        val rideType = parsed.rideType ?: RideType.ANY

        val reply = if (parsed.dropText != null) {
            if (parsed.pickupMode == PickupMode.UNKNOWN) {
                "I can help you book a ${rideTypeToDisplay(rideType)} cab to ${parsed.dropText}. I still need your pickup location."
            } else {
                "I can help you book a ${rideTypeToDisplay(rideType)} cab to ${parsed.dropText}."
            }
        } else {
            "I can help you book a ${rideTypeToDisplay(rideType)} cab."
        }

        return brainAction(
            rawText = rawText,
            intent = "cab_booking",
            reply = reply,
            type = BrainActionType.EXTERNAL_ACTION,
            risk = BrainRiskLevel.LOW,
            confirm = false,
            finalAllowed = false,
            params = mapOf(
                "dropLocation" to (parsed.dropText ?: ""),
                "destination" to (parsed.dropText ?: ""),
                "rideType" to rideType.name,
                "pickupMode" to parsed.pickupMode.name,
                "wantsCheapest" to parsed.wantsCheapest.toString()
            ),
            nextQuestion = "Where should I pick you up from?"
        )
    }

    private fun parseGroceryBooking(rawText: String): BrainAction? {
        val parsed = groceryIntentParser.parseInitialGroceryRequest(rawText) ?: return null
        
        val items = parsed.basket.displayText()
        val reply = if (items.isNotBlank()) {
            "I can help you order $items from grocery stores."
        } else {
            "I can help you with your grocery order."
        }

        return brainAction(
            rawText = rawText,
            intent = "grocery_booking",
            reply = reply,
            type = BrainActionType.EXTERNAL_ACTION,
            risk = BrainRiskLevel.LOW,
            confirm = false,
            finalAllowed = false,
            params = mapOf(
                "items" to items,
                "rawText" to rawText
            )
        )
    }

    private fun parsePreparedMessage(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        val hasMessagePlanningCue = listOf(
            "prepare message", "compose message", "draft message",
            "message to", "reply to", "send message"
        ).any { containsPhrase(normalized, it) }

        val directSpeakCue = (normalized.startsWith("tell ") || normalized.startsWith("say ")) &&
            normalized.contains(" to ")

        if (!hasMessagePlanningCue && !directSpeakCue) {
            return null
        }

        val appName = extractMessagingAppName(normalized)
        val contact = extractMessageContact(rawText)
        val displayContact = contact?.replaceFirstChar { it.titlecase(Locale.US) }

        val reply = when {
            appName != null && displayContact != null ->
                "I can prepare a ${appName.replaceFirstChar { it.titlecase(Locale.US) }} message to $displayContact."
            appName != null ->
                "I can prepare a ${appName.replaceFirstChar { it.titlecase(Locale.US) }} message."
            displayContact != null ->
                "I can prepare a message to $displayContact."
            else ->
                "I can prepare the message."
        }

        return brainAction(
            rawText = rawText,
            intent = "prepare_message",
            reply = reply,
            type = BrainActionType.PREPARE,
            risk = BrainRiskLevel.MEDIUM,
            confirm = false,
            finalAllowed = false,
            params = buildMap {
                appName?.let { put("appName", it.lowercase(Locale.US)) }
                contact?.let { put("contact", it.lowercase(Locale.US)) }
            },
            nextQuestion = "What should I say to ${displayContact?.replaceFirstChar { it.titlecase(Locale.US) } ?: "them"}?"
        )
    }

    private fun parseLegacyCommand(rawText: String): BrainAction {
        val intent = legacyParser.parse(rawText)
        val reply = when (intent.actionType) {
            ActionType.LAUNCH_APP -> "Opening ${intent.entities["appName"]}."
            ActionType.GO_HOME -> "Going home."
            ActionType.GO_BACK -> "Going back."
            ActionType.OPEN_RECENTS -> "Opening recent apps."
            ActionType.OPEN_NOTIFICATIONS -> "Opening notifications."
            ActionType.SCROLL_FORWARD -> "Scrolling down."
            ActionType.SCROLL_BACKWARD -> "Scrolling up."
            ActionType.CLICK_TEXT -> "Tapping on ${intent.entities["text"]}."
            ActionType.TYPE_TEXT -> "Typing ${intent.entities["text"]}."
            ActionType.READ_NOTIFICATIONS -> "Reading your notifications."
            ActionType.CALL_CONTACT -> "Calling ${intent.entities["value"]}."
            ActionType.STOP_SERVICE -> "Stopping now."
            ActionType.BLOCKED -> "I cannot perform that sensitive action for your safety."
            ActionType.UNKNOWN -> "I'm not sure how to help with that yet."
            else -> "I'll handle that for you."
        }

        val risk = when (intent.actionType) {
            ActionType.BLOCKED -> BrainRiskLevel.HUMAN_ONLY
            ActionType.UNKNOWN -> BrainRiskLevel.UNKNOWN
            else -> BrainRiskLevel.LOW
        }

        return brainAction(
            rawText = rawText,
            intent = if (intent.actionType == ActionType.UNKNOWN) "unknown" else intent.intentType.name.lowercase(Locale.US),
            reply = reply,
            type = when (intent.actionType) {
                ActionType.BLOCKED -> BrainActionType.HUMAN_ONLY
                ActionType.LAUNCH_APP -> BrainActionType.OPEN_APP
                ActionType.CALL_CONTACT -> BrainActionType.MAKE_CALL_DRAFT
                else -> BrainActionType.UNKNOWN
            },
            risk = risk,
            confirm = risk == BrainRiskLevel.MEDIUM || risk == BrainRiskLevel.HUMAN_ONLY,
            params = intent.entities
        )
    }

    private fun buildUnknownAction(rawText: String): BrainAction {
        return brainAction(
            rawText = rawText,
            intent = "unknown",
            reply = "I'm not sure how to help with that yet.",
            type = BrainActionType.UNKNOWN,
            risk = BrainRiskLevel.UNKNOWN,
            confirm = false
        )
    }

    private fun brainAction(
        rawText: String,
        intent: String,
        reply: String,
        type: BrainActionType,
        risk: BrainRiskLevel,
        confirm: Boolean,
        params: Map<String, String> = emptyMap(),
        nextQuestion: String? = null,
        finalAllowed: Boolean = !confirm
    ): BrainAction {
        return BrainAction(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = rawText,
            normalizedCommand = normalize(rawText),
            intent = intent,
            reply = reply,
            actionType = type,
            riskLevel = risk,
            requiresConfirmation = confirm,
            params = params,
            confidence = 0.9,
            assistantReply = reply,
            reason = nextQuestion ?: "Local interpreter rule match.",
            nextQuestion = nextQuestion,
            finalActionAllowed = finalAllowed
        )
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun extractMessagingAppName(normalized: String): String? {
        return when {
            containsPhrase(normalized, "whatsapp") -> "whatsapp"
            containsPhrase(normalized, "sms") -> "sms"
            containsPhrase(normalized, "telegram") -> "telegram"
            containsPhrase(normalized, "gmail") -> "gmail"
            containsPhrase(normalized, "email") -> "email"
            containsPhrase(normalized, "messenger") -> "messenger"
            containsPhrase(normalized, "signal") -> "signal"
            else -> null
        }
    }

    private fun extractMessageContact(rawText: String): String? {
        val normalized = normalize(rawText)
        val patterns = listOf(
            Regex("""\b(?:to|for)\s+([a-z0-9][a-z0-9\s.-]*)$""", RegexOption.IGNORE_CASE),
            Regex("""\bmessage\s+to\s+([a-z0-9][a-z0-9\s.-]*)$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return null
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
    }

    private fun extractDestination(rawText: String): String? {
        val normalized = normalize(rawText)
        val patterns = listOf(
            Regex("""\bto\s+(.+)$"""),
            Regex("""\bto\s+(.+?)\s+on\b"""),
            Regex("""\bto\s+(.+?)\s+using\b""")
        )

        for (pattern in patterns) {
            pattern.find(normalized)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun rideTypeToDisplay(rideType: RideType): String {
        return rideType.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
    }
}
