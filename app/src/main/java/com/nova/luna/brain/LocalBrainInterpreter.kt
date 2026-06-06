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

        if (request.activeFoodSession) {
            return BrainAction(
                intent = "food_session",
                reply = "Continuing the food flow.",
                actionType = BrainActionType.EXTERNAL_ACTION,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf("rawText" to rawText, "activeFoodSession" to "true")
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

        val nextQuestion = when {
            parsed.foodItem.isNullOrBlank() && parsed.restaurantName.isNullOrBlank() ->
                "What would you like to order?"
            parsed.requestedProviders.isEmpty() -> "Which app should I use, Swiggy or Zomato?"
            else -> "Say yes and I will prepare the food flow."
        }

        return BrainAction(
            intent = "food_order",
            reply = reply,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            nextQuestion = nextQuestion
        )
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
            "complete payment",
            "banking",
            "bank transfer",
            "upi",
            "password",
            "otp",
            "one time password",
            "enter otp",
            "captcha",
            "solve captcha",
            "login",
            "sign in",
            "bypass login",
            "place final order",
            "place order without",
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

    private fun parseCabComparison(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        if (!normalized.contains("compare")) return null

        val providers = CabProvider.values().filter { provider ->
            normalized.contains(provider.name.lowercase(Locale.US))
        }

        val destination = extractDestination(rawText)
        if (providers.isEmpty() && destination.isNullOrBlank()) return null

        val reply = if (destination != null) {
            "I'll compare cab options to $destination for you."
        } else {
            "I can compare cab options across available providers."
        }

        return BrainAction(
            intent = "cab_compare",
            reply = reply,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to rawText,
                "destination" to (destination ?: ""),
                "providers" to providers.joinToString { it.name }
            )
        )
    }

    private fun parseCabBooking(rawText: String): BrainAction? {
        val parsed = cabIntentParser.parseInitialCabRequest(rawText) ?: return null
        val rideType = parsed.rideType ?: RideType.ANY

        val reply = if (parsed.dropText != null) {
            "I can help you book a ${rideTypeToDisplay(rideType)} cab to ${parsed.dropText}."
        } else {
            "I can help you book a ${rideTypeToDisplay(rideType)} cab."
        }

        val nextQuestion = when {
            parsed.dropText == null -> "Where would you like to go?"
            parsed.pickupMode == PickupMode.UNKNOWN -> "I need your pickup location. Should I use your current location or a manual pickup?"
            else -> "Should I check available cabs for you?"
        }

        return BrainAction(
            intent = "cab_booking",
            reply = reply,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to rawText,
                "destination" to (parsed.dropText ?: ""),
                "rideType" to rideType.name,
                "pickupMode" to parsed.pickupMode.name
            ),
            nextQuestion = nextQuestion
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

        val nextQuestion = if (items.isBlank()) {
            "What items would you like to add to your cart?"
        } else {
            "Should I start searching for these items?"
        }

        return BrainAction(
            intent = "grocery_booking",
            reply = reply,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to rawText,
                "items" to items
            ),
            nextQuestion = nextQuestion
        )
    }

    private fun parsePreparedMessage(rawText: String): BrainAction? {
        val normalized = normalize(rawText)
        if (normalized.startsWith("tell ") || normalized.startsWith("say ")) {
            val message = rawText.substringAfter(" ").trim()
            return BrainAction(
                intent = "prepared_message",
                reply = "I'll say: $message",
                actionType = BrainActionType.READ_ONLY,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = mapOf("message" to message)
            )
        }
        return null
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

        return BrainAction(
            intent = intent.intentType.name.lowercase(Locale.US),
            reply = reply,
            actionType = when (intent.actionType) {
                ActionType.BLOCKED -> BrainActionType.HUMAN_ONLY
                ActionType.UNKNOWN -> BrainActionType.NONE
                else -> BrainActionType.EXTERNAL_ACTION
            },
            riskLevel = if (intent.actionType == ActionType.BLOCKED) BrainRiskLevel.BLOCKED else BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = intent.entities + mapOf("rawText" to rawText)
        )
    }

    private fun buildUnknownAction(rawText: String): BrainAction {
        return BrainAction(
            intent = "unknown",
            reply = "I'm not sure how to help with that yet.",
            actionType = BrainActionType.NONE,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to rawText)
        )
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
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
        return when (rideType) {
            RideType.AUTO -> "Auto"
            RideType.BIKE -> "Bike"
            RideType.MINI -> "Mini"
            RideType.SEDAN -> "Sedan"
            RideType.SUV -> "SUV"
            RideType.ANY -> "Any"
        }
    }
}
