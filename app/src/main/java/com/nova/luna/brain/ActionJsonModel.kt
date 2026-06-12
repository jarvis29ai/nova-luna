package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.food.FoodBookingVoiceResponses
import com.nova.luna.food.FoodIntentParser
import com.nova.luna.grocery.GroceryBookingVoiceResponses
import com.nova.luna.grocery.GroceryIntentParseResult
import com.nova.luna.grocery.GroceryIntentParser
import com.nova.luna.content.ContentCreationCommandType
import com.nova.luna.content.ContentCreationIntentParser
import com.nova.luna.content.ContentCreationVoiceResponses
import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class ActionJsonModel(
    private val cabInterpreter: LocalBrainInterpreter = LocalBrainInterpreter(),
    private val foodParser: FoodIntentParser = FoodIntentParser(),
    private val groceryParser: GroceryIntentParser = GroceryIntentParser(),
    private val contentParser: ContentCreationIntentParser = ContentCreationIntentParser(),
    private val contentVoiceResponses: ContentCreationVoiceResponses = ContentCreationVoiceResponses(),
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
        val contentRequest = contentParser.parse(request.rawText)
        val candidateAction = when {
            request.activeCabSession || isCabOrMessagePlanning(normalized) -> prepareDraftableAction(
                cabInterpreter.interpret(request)
            )
            request.activeGrocerySession -> groceryPlanningAction(request, groceryRequest, activeSession = true)
            groceryRequest != null -> groceryPlanningAction(request, groceryRequest, activeSession = false)
            foodRequest != null -> foodOrderingAction(request, foodRequest)
            isContentCreationRequest(contentRequest) -> contentCreationAction(request, contentRequest)
            isFoodPlanning(normalized) -> foodPlanningAction(request)
            isTaskPlanning(normalized) -> taskPlanningAction(request)
            else -> generalPlanningAction(request)
        }

        if (!validator.isAcceptable(candidateAction)) {
            return BrainModelResult.available(
                role = role,
                candidateAction = candidateAction.copy(
                    intent = "human_only",
                    reply = "I encountered an issue while processing your request. Please try again or use manual control.",
                    assistantReply = "I encountered an issue while processing your request. Please try again or use manual control."
                ),
                rawResponse = codec.encode(candidateAction),
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
            realInference = false,
            nativeGenerationAvailable = false,
            jsonParseAttempted = false,
            jsonParseSuccess = false,
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

        val replyText = if (activeSession) {
            "Continuing the grocery flow."
        } else {
            "I can prepare the grocery flow and keep the final step manual."
        }
        
        return BrainAction(
            schemaVersion = 1,
            source = BrainActionSource.MODEL,
            rawCommand = request.rawText,
            normalizedCommand = AssistantTextNormalizer.normalize(request.rawText),
            intent = intent,
            reply = replyText,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            confidence = 0.9,
            assistantReply = replyText,
            reason = nextQuestion ?: "Grocery planning action."
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

        val replyText = "I can prepare the food order and keep the final step manual."
        return BrainAction(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = request.rawText,
            normalizedCommand = normalize(request.rawText),
            intent = "food_order",
            reply = replyText,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            confidence = 0.9,
            assistantReply = replyText,
            reason = nextQuestion
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

        val replyText = "I can help plan the food request and keep the final step manual."
        return BrainAction(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = request.rawText,
            normalizedCommand = normalized,
            intent = "food_planning",
            reply = replyText,
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = params,
            confidence = 0.8,
            assistantReply = replyText,
            reason = "What food or restaurant details should I capture?"
        )
    }

    private fun contentCreationAction(
        request: BrainRequest,
        parsed: com.nova.luna.content.ContentCreationRequest
    ): BrainAction {
        val intent = when (parsed.commandType) {
            ContentCreationCommandType.FINALIZE_OUTPUT,
            ContentCreationCommandType.EXPORT_FILE,
            ContentCreationCommandType.SHARE_FILE,
            ContentCreationCommandType.EDIT_DRAFT,
            ContentCreationCommandType.REGENERATE_DRAFT,
            ContentCreationCommandType.DETECT_BEST_FORMAT,
            ContentCreationCommandType.CREATE_PPT,
            ContentCreationCommandType.CREATE_IMAGE,
            ContentCreationCommandType.CREATE_VIDEO,
            ContentCreationCommandType.CREATE_DOCUMENT,
            ContentCreationCommandType.CREATE_EXCEL,
            ContentCreationCommandType.CREATE_PDF,
            ContentCreationCommandType.CREATE_OTHER -> "content_creation"

            else -> "content_creation"
        }

        val commandTypeLabel = parsed.commandType.name.lowercase(Locale.US)
        val outputTypeLabel = parsed.outputType.name.lowercase(Locale.US)
        val replyText = when (parsed.commandType) {
            ContentCreationCommandType.CREATE_PPT -> "I can create a PPT and keep the final step manual."
            ContentCreationCommandType.CREATE_IMAGE -> "I can create an image and keep the final step manual."
            ContentCreationCommandType.CREATE_VIDEO -> "I can create a video draft and keep the final step manual."
            ContentCreationCommandType.CREATE_DOCUMENT -> "I can create a document draft and keep the final step manual."
            ContentCreationCommandType.CREATE_EXCEL -> "I can create a spreadsheet draft and keep the final step manual."
            ContentCreationCommandType.CREATE_PDF -> "I can create a PDF draft and keep the final step manual."
            ContentCreationCommandType.EDIT_DRAFT -> "I can help refine the draft."
            ContentCreationCommandType.REGENERATE_DRAFT -> "I can regenerate the draft safely."
            ContentCreationCommandType.DETECT_BEST_FORMAT -> "I can help choose the best format."
            ContentCreationCommandType.FINALIZE_OUTPUT,
            ContentCreationCommandType.EXPORT_FILE,
            ContentCreationCommandType.SHARE_FILE -> "I can prepare the final content step, but I will stop before anything irreversible."

            else -> "I can help create the content."
        }

        val requiresConfirmation = parsed.commandType in setOf(
            ContentCreationCommandType.FINALIZE_OUTPUT,
            ContentCreationCommandType.EXPORT_FILE,
            ContentCreationCommandType.SHARE_FILE
        )

        val nextQuestion = when {
            requiresConfirmation -> "Say yes if you want me to continue."
            parsed.requirements.topic.isNullOrBlank() &&
                parsed.commandType in setOf(
                    ContentCreationCommandType.CREATE_PPT,
                    ContentCreationCommandType.CREATE_IMAGE,
                    ContentCreationCommandType.CREATE_VIDEO,
                    ContentCreationCommandType.CREATE_DOCUMENT,
                    ContentCreationCommandType.CREATE_EXCEL,
                    ContentCreationCommandType.CREATE_PDF,
                    ContentCreationCommandType.CREATE_OTHER,
                    ContentCreationCommandType.DETECT_BEST_FORMAT
                ) -> contentVoiceResponses.getMissingDetailQuestion("topic")

            else -> "What would you like me to include?"
        }

        val params = buildMap {
            put("rawText", request.rawText)
            put("commandType", commandTypeLabel)
            put("outputType", outputTypeLabel)
            parsed.requirements.topic?.let { put("topic", it) }
            put("purpose", parsed.requirements.purpose.name.lowercase(Locale.US))
            parsed.requirements.audience?.let { put("audience", it) }
            put("style", parsed.requirements.style.name.lowercase(Locale.US))
            parsed.requirements.length?.let { put("length", it) }
            parsed.requirements.language?.let { put("language", it) }
            put("qualityLevel", parsed.requirements.qualityLevel.name.lowercase(Locale.US))
            parsed.requirements.preferredTool?.let { put("preferredTool", it.name.lowercase(Locale.US)) }
            parsed.requirements.exportFormat?.let { put("exportFormat", it) }
            parsed.requirements.shareTarget?.let { put("shareTarget", it) }
        }

        return BrainAction(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = request.rawText,
            normalizedCommand = normalize(request.rawText),
            intent = intent,
            reply = replyText,
            actionType = BrainActionType.CREATE_CONTENT,
            riskLevel = if (requiresConfirmation) BrainRiskLevel.MEDIUM else BrainRiskLevel.LOW,
            requiresConfirmation = requiresConfirmation,
            params = params,
            confidence = 0.9,
            assistantReply = replyText,
            reason = nextQuestion
        )
    }

    private fun taskPlanningAction(request: BrainRequest): BrainAction {
        val replyText = "I can organize that task into a safe local plan."
        return BrainAction(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = request.rawText,
            normalizedCommand = normalize(request.rawText),
            intent = "task_planning",
            reply = replyText,
            actionType = BrainActionType.ASK_QUESTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false,
            params = mapOf("rawText" to request.rawText),
            confidence = 0.7,
            assistantReply = replyText,
            reason = "Task planning action."
        )
    }

    private fun generalPlanningAction(request: BrainRequest): BrainAction {
        val replyText = "I can structure that request into a safe local plan."
        return BrainAction(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = request.rawText,
            normalizedCommand = normalize(request.rawText),
            intent = "plan_request",
            reply = replyText,
            actionType = BrainActionType.ASK_QUESTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = false,
            params = mapOf("rawText" to request.rawText),
            confidence = 0.5,
            assistantReply = replyText,
            reason = "General planning action."
        )
    }

    private fun isContentCreationRequest(parsed: com.nova.luna.content.ContentCreationRequest): Boolean {
        return parsed.commandType in setOf(
            ContentCreationCommandType.CREATE_PPT,
            ContentCreationCommandType.CREATE_IMAGE,
            ContentCreationCommandType.CREATE_VIDEO,
            ContentCreationCommandType.CREATE_DOCUMENT,
            ContentCreationCommandType.CREATE_EXCEL,
            ContentCreationCommandType.CREATE_PDF,
            ContentCreationCommandType.CREATE_OTHER,
            ContentCreationCommandType.DETECT_BEST_FORMAT,
            ContentCreationCommandType.EDIT_DRAFT,
            ContentCreationCommandType.REGENERATE_DRAFT,
            ContentCreationCommandType.FINALIZE_OUTPUT,
            ContentCreationCommandType.EXPORT_FILE,
            ContentCreationCommandType.SHARE_FILE
        )
    }

    private fun prepareDraftableAction(action: BrainAction): BrainAction {
        return action
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
