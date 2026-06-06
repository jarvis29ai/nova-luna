package com.nova.luna.shopping

import android.content.Context
import java.util.UUID

class ShoppingOrchestrator(
    private val context: Context,
    private val parser: ShoppingIntentParser = ShoppingIntentParser(),
    private val requirementBuilder: ShoppingRequirementBuilder = ShoppingRequirementBuilder(),
    private val searchEngine: ShoppingSearchEngine = ShoppingSearchEngine(),
    private val trustChecker: ShoppingTrustChecker = ShoppingTrustChecker(),
    private val dealRanker: ShoppingDealRanker = ShoppingDealRanker(),
    private val cartController: ShoppingCartController = ShoppingCartController(),
    private val paymentController: ShoppingPaymentController = ShoppingPaymentController(),
    private val safetyDetector: ShoppingSafetyDetector = ShoppingSafetyDetector(),
    private val voiceResponses: ShoppingVoiceResponses = ShoppingVoiceResponses()
) {
    private var activeSession: ShoppingSession? = null

    fun handleRequest(rawText: String): ShoppingResult {
        val session = activeSession ?: createNewSession()
        activeSession = session

        val request = parser.parse(rawText)
        
        return when (request.commandType) {
            ShoppingCommandType.CANCEL -> {
                activeSession = null
                ShoppingResult(ShoppingStatus.CANCELLED, "Shopping cancelled.", "Shopping cancelled.")
            }
            ShoppingCommandType.CONFIRM_PURCHASE -> handleConfirmation(session)
            ShoppingCommandType.OPEN_SELECTED_DEAL -> handleDealSelection(session, rawText)
            else -> processFlow(session, request)
        }
    }

    private fun createNewSession(): ShoppingSession {
        return ShoppingSession(id = UUID.randomUUID().toString(), state = ShoppingFlowState.IDLE)
    }

    private fun processFlow(session: ShoppingSession, request: ShoppingRequest): ShoppingResult {
        var currentSession = session.copy(state = ShoppingFlowState.PARSING_REQUEST)

        // 1. Requirement Profile
        val profile = requirementBuilder.updateProfile(currentSession.requirementProfile, request)
        currentSession = currentSession.copy(requirementProfile = profile)

        if (!requirementBuilder.hasRequiredDetails(profile)) {
            val missingDetail = requirementBuilder.getMissingDetailQuestion(profile)
            return ShoppingResult(
                ShoppingStatus.NEEDS_USER_INPUT,
                missingDetail,
                missingDetail,
                currentSession.copy(state = ShoppingFlowState.ASKING_MISSING_DETAILS)
            )
        }

        // 2. Search
        currentSession = currentSession.copy(state = ShoppingFlowState.SEARCHING_TRUSTED_MARKETPLACES)
        val results = searchEngine.search(profile)
        
        // 3. Trust Check & Filter
        val safeResults = results.filter { trustChecker.check(it.sourceUrl, it.seller).level != ShoppingTrustLevel.RISKY }
        currentSession = currentSession.copy(searchResults = safeResults)

        // 4. Deal Ranking
        currentSession = currentSession.copy(state = ShoppingFlowState.SELECTING_TOP_DEALS)
        val topDeals = dealRanker.rank(safeResults, profile)
        currentSession = currentSession.copy(topDeals = topDeals)

        // 5. User Summary
        val summary = voiceResponses.prepareSummary(topDeals)
        activeSession = currentSession.copy(state = ShoppingFlowState.WAITING_FOR_DEAL_SELECTION)
        
        return ShoppingResult(
            ShoppingStatus.SUCCESS,
            summary.popupText,
            summary.voiceText,
            activeSession
        )
    }

    private fun handleDealSelection(session: ShoppingSession, rawText: String): ShoppingResult {
        val dealIndex = extractIndex(rawText) ?: 0
        if (dealIndex < 0 || dealIndex >= session.topDeals.size) {
            return ShoppingResult(ShoppingStatus.FAILED, "Invalid selection.", "Invalid selection.", session)
        }

        val selectedDeal = session.topDeals[dealIndex]
        val updatedSession = session.copy(
            selectedDeal = selectedDeal,
            state = ShoppingFlowState.ADDING_PRODUCT_TO_CART
        )

        // Add to cart logic
        val cartResult = cartController.addToCart(selectedDeal.product)
        if (!cartResult) {
            return ShoppingResult(ShoppingStatus.FAILED, "Failed to add to cart.", "Failed to add to cart.", updatedSession)
        }

        val orderSummary = ShoppingFinalOrderSummary(
            product = selectedDeal.product,
            providerName = selectedDeal.product.provider.name,
            sellerName = selectedDeal.product.seller,
            finalPrice = selectedDeal.finalPayablePrice,
            totalSavings = selectedDeal.totalSavings,
            deliveryDate = selectedDeal.product.deliveryDate,
            returnWarrantyPolicy = "${selectedDeal.product.returnPolicy} / ${selectedDeal.product.warranty}",
            paymentOptions = listOf(ShoppingPaymentMethod.UPI, ShoppingPaymentMethod.CARD, ShoppingPaymentMethod.COD)
        )

        val finalSession = updatedSession.copy(
            finalOrderSummary = orderSummary,
            state = ShoppingFlowState.WAITING_FOR_FINAL_CONFIRMATION
        )
        activeSession = finalSession

        val confirmationMsg = voiceResponses.askFinalConfirmation(orderSummary)
        return ShoppingResult(
            ShoppingStatus.NEEDS_CONFIRMATION,
            confirmationMsg.popupText,
            confirmationMsg.voiceText,
            finalSession
        )
    }

    private fun handleConfirmation(session: ShoppingSession): ShoppingResult {
        if (session.state != ShoppingFlowState.WAITING_FOR_FINAL_CONFIRMATION) {
            return ShoppingResult(ShoppingStatus.FAILED, "Nothing to confirm.", "Nothing to confirm.", session)
        }

        activeSession = session.copy(state = ShoppingFlowState.ASKING_PAYMENT_METHOD)
        val paymentMsg = "Which payment method would you like to use: UPI, Card, or COD?"
        return ShoppingResult(ShoppingStatus.NEEDS_USER_INPUT, paymentMsg, paymentMsg, activeSession)
    }

    private fun extractIndex(text: String): Int? {
        if (text.contains("first") || text.contains("1")) return 0
        if (text.contains("second") || text.contains("2")) return 1
        if (text.contains("third") || text.contains("3")) return 2
        return null
    }

    fun isActive(): Boolean = activeSession != null
}
