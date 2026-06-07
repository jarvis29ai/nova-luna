package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.food.FoodBookingVoiceResponses
import com.nova.luna.food.FoodIntentParser
import com.nova.luna.grocery.GroceryBookingVoiceResponses
import com.nova.luna.grocery.GroceryIntentParseResult
import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class ActionJsonModel(
    private val cabInterpreter: LocalBrainInterpreter = LocalBrainInterpreter(),
    private val foodParser: FoodIntentParser = FoodIntentParser(),
    private val groceryParser: GroceryIntentParser = GroceryIntentParser(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec(),
    private val validator: BrainActionValidator = BrainActionValidator()
) : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.ACTION_JSON
    override val available: Boolean = true

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        return generate(request, routeDecision, reasoningHint = null)
    }

    fun generate(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        reasoningHint: String?
    ): BrainModelResult {
        val normalized = normalize(request.rawText)
        val foodRequest = foodParser.parse(request.rawText)
        val groceryRequest = groceryParser.parseInitialGroceryRequest(request.rawText)
        val candidateAction = when {
            request.activeCabSession || isCabOrMessagePlanning(normalized) -> cabInterpreter.interpret(request)
            request.activeGrocerySession -> groceryPlanningAction(request, groceryRequest, activeSession = true)
            groceryRequest != null -> groceryPlanningAction(request, groceryRequest, activeSession = false)
            foodRequest != null -> foodOrderingAction(request, foodRequest)
            isFoodPlanning(normalized) -> foodPlanningAction(request)
            isTaskPlanning(normalized) -> taskPlanningAction(request)
            else -> generalPlanningAction(request)
        }

        if (!validator.isAcceptable(candidateAction)) {
            return BrainModelResult.unavailable(
                role = role,
                reason = "ActionJsonModel produced a candidate rejected by BrainActionValidator.",
                safetyNotes = routeDecision.safetyNotes + listOf(
                    "ActionJsonModel only emits safe BrainAction JSON.",
                    "Gemma reasoning input, if supplied later, remains advisory only.",
                    "Dangerous final actions stay blocked before routing."
                )
            )
        }

        return BrainModelResult.available(
            role = role,
            candidateAction = candidateAction,
            rawResponse = codec.encode(candidateAction),
            reason = "Structured action JSON candidate produced locally.",
            safetyNotes = routeDecision.safetyNotes + buildSafetyNotes(reasoningHint)
        )
    }

    private fun isCabOrMessagePlanning(normalized: String): Boolean {
        val cabKeywords = listOf("cab", "ride", "taxi", "uber", "ola", "rapido")
        val messageKeywords = listOf(
            "prepare message",
            "compose message",
            "draft message",
            "message to",
            "reply to",
            "send message",
            "text message",
            "whatsapp"
        )
        val bookCabPattern = normalized.contains("book") &&
            listOf("auto", "cab", "ride", "taxi", "uber", "ola", "rapido").any { containsKeyword(normalized, it) }

        return cabKeywords.any { containsKeyword(normalized, it) } ||
            bookCabPattern ||
            messageKeywords.any { normalized.contains(normalize(it)) }
    }

    private fun isFoodPlanning(normalized: String): Boolean {
        val foodKeywords = listOf(
            "food",
            "meal",
            "dinner",
            "lunch",
            "breakfast",
            "restaurant",
            "delivery",
            "order food",
            "plan food",
            "find food"
        )
        return foodKeywords.any { normalized.contains(normalize(it)) }
    }

    private fun isTaskPlanning(normalized: String): Boolean {
        val taskKeywords = listOf(
            "task",
            "todo",
            "to do",
            "plan",
            "schedule",
            "organize",
            "remind",
            "reminder"
        )
        return taskKeywords.any { containsKeyword(normalized, it) }
    }

    private fun groceryPlanningAction(
        request: BrainRequest,
        parsed: GroceryIntentParseResult?,
        activeSession: Boolean
    ): BrainAction {
        val groceryResult = parsed ?: groceryParser.parseInitialGroceryRequest(request.rawText)
        val compareRequested = groceryResult?.compareRequested == true ||
            groceryResult?.wantsCheapest == true ||
            groceryResult?.wantsFirstOne == true
        val intent = when {
            activeSession -> "grocery_session"
            compareRequested -> "grocery_compare"
            else -> "grocery_booking"
        }
        val params = buildMap {
            put("rawText", request.rawText)
            groceryResult?.let { putAll(it.toEntities()) }
            groceryResult?.providerPreference?.let { provider ->
                val providerLabel = provider.name.lowercase(Locale.US)
                put("preferredProvider", providerLabel)
                put("providerPreference", providerLabel)
            }
            put("activeGrocerySession", activeSession.toString())
        }
        val nextQuestion = when {
            activeSession -> null
            groceryResult?.basket?.items?.isEmpty() == true -> GroceryBookingVoiceResponses.askItems()
            groceryResult?.requiresBrandQuestion == true -> GroceryBookingVoiceResponses.askBrandPreference()
            compareRequested -> "I can compare grocery providers locally and stop before checkout."
            else -> "I can prepare the grocery flow locally and stop before checkout."
        }

        return BrainAction(
            intent = intent,
            reply = if (activeSession) {
                "Continuing the grocery flow."
            } else {
                "I can prepare the grocery flow and keep the final step manual."
            },
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            nextQuestion = nextQuestion
        )
    }

    private fun foodOrderingAction(
        request: BrainRequest,
        parsed: com.nova.luna.food.FoodBookingRequest
    ): BrainAction {
        val params = buildMap {
            put("rawText", request.rawText)
            putAll(parsed.toEntities())
        }

        val nextQuestion = when {
            parsed.foodItem.isNullOrBlank() -> FoodBookingVoiceResponses.askFoodItem()
            parsed.restaurantName.isNullOrBlank() -> FoodBookingVoiceResponses.askRestaurant()
            parsed.requestedProviders.isNotEmpty() -> "Say yes and I will compare food providers."
            else -> "I will stop before checkout."
        }

        return BrainAction(
            intent = "food_order",
            reply = "I can prepare the food order and keep the final step manual.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            nextQuestion = nextQuestion
        )
    }

    private fun foodPlanningAction(request: BrainRequest): BrainAction {
        val normalized = normalize(request.rawText)
        val cuisineHint = when {
            normalized.contains("pizza") -> "pizza"
            normalized.contains("burger") -> "burger"
            normalized.contains("biryani") -> "biryani"
            normalized.contains("noodles") -> "noodles"
            else -> null
        }

        val params = buildMap {
            put("rawText", request.rawText)
            cuisineHint?.let { put("foodHint", it) }
        }

        return BrainAction(
            intent = "food_planning",
            reply = "I can help plan the food request and keep the final step manual.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = params,
            nextQuestion = "What food or restaurant details should I capture?"
        )
    }

    private fun taskPlanningAction(request: BrainRequest): BrainAction {
        val params = mapOf(
            "rawText" to request.rawText
        )

        return BrainAction(
            intent = "task_planning",
            reply = "I can organize that task into a safe local plan.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            nextQuestion = "What details should I capture?"
        )
    }

    private fun generalPlanningAction(request: BrainRequest): BrainAction {
        return BrainAction(
            intent = "plan_request",
            reply = "I can structure that request into a safe local plan.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf("rawText" to request.rawText),
            nextQuestion = "What details should I capture?"
        )
    }

    private fun containsKeyword(normalized: String, keyword: String): Boolean {
        val target = normalize(keyword)
        if (target.isBlank()) return false
        return Regex("""\b${Regex.escape(target)}\b""").containsMatchIn(normalized)
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun com.nova.luna.food.FoodBookingRequest.toEntities(): Map<String, String> {
        return buildMap {
            put("rawText", rawText)
            foodItem?.takeIf { it.isNotBlank() }?.let { put("foodItem", it) }
            restaurantName?.takeIf { it.isNotBlank() }?.let { put("restaurantName", it) }
            quantity?.let { put("quantity", it.toString()) }
            preferredProvider?.let { put("preferredProvider", it.name) }
            if (requestedProviders.isNotEmpty()) {
                put("requestedProviders", requestedProviders.joinToString(separator = ",") { it.name })
            }
            deliveryLocation?.takeIf { it.isNotBlank() }?.let { put("deliveryLocation", it) }
            couponPreference?.takeIf { it.isNotBlank() }?.let { put("couponPreference", it) }
            put("finalUserConfirmed", finalUserConfirmed.toString())
        }
    }

    private fun buildSafetyNotes(reasoningHint: String?): List<String> {
        val notes = mutableListOf(
            "ActionJsonModel only emits safe BrainAction JSON.",
            "It must pass BrainActionValidator before any later routing step.",
            "Gemma reasoning input, if supplied later, remains advisory only."
        )
        if (!reasoningHint.isNullOrBlank()) {
            notes += "Gemma reasoning input was observed but not used to weaken safety."
        }
        return notes
    }
}
