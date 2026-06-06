package com.nova.luna.grocery

import android.content.Intent
import com.nova.luna.util.AccessibilityReadiness
import java.util.Locale

class GroceryBookingOrchestrator(
    private val providerRegistry: GroceryProviderRegistry,
    private val deepLinkBuilder: GroceryDeepLinkBuilder,
    private val accessibilityService: GroceryAccessibilityService = GroceryAccessibilityService(),
    private val priceComparator: GroceryPriceComparator = GroceryPriceComparator(),
    private val intentParser: GroceryIntentParser = GroceryIntentParser(),
    private val accessibilityReadyProvider: () -> Boolean = { AccessibilityReadiness.isBound() },
    private val permissionStatusProvider: () -> GroceryPermissionStatus = { GroceryPermissionStatus() },
    private val providerLauncher: GroceryProviderLauncher
) {
    private var session: GroceryBookingSession? = null

    fun isActive(): Boolean {
        return session != null
    }

    fun currentState(): GroceryBookingState {
        return session?.state ?: GroceryBookingState.IDLE
    }

    fun currentSession(): GroceryBookingSession? = session

    fun start(request: GroceryBookingRequest): GroceryBookingResult {
        val activeSession = session
        if (activeSession != null) {
            return handleUserInput(request.rawText, request.finalUserConfirmed)
        }

        val parsed = intentParser.parseInitialGroceryRequest(request.rawText)
        val mergedRequest = mergeRequest(request, parsed)
        session = GroceryBookingSession(
            request = mergedRequest,
            state = GroceryBookingState.PARSING_REQUEST
        )

        GroceryLogger.i(
            "start_grocery",
            mapOf(
                "rawText" to request.rawText,
                "items" to mergedRequest.basket.displayText(),
                "preferredProvider" to mergedRequest.preferredProvider?.name,
                "brandPreference" to mergedRequest.brandPreference,
                "budgetPreference" to mergedRequest.budgetPreference?.name,
                "compareRequested" to mergedRequest.compareRequested
            )
        )

        return advanceFromCurrentRequest(parsed)
    }

    fun handleUserInput(rawText: String, finalUserConfirmed: Boolean = false): GroceryBookingResult {
        val current = session ?: return GroceryBookingResult(
            state = GroceryBookingState.FAILED,
            message = GroceryBookingVoiceResponses.bookingFailed("no active grocery booking session.")
        )

        current.finalUserConfirmed = finalUserConfirmed || current.finalUserConfirmed

        intentParser.parseFollowUpCommand(rawText)?.let { followUp ->
            val handled = handleFollowUp(current, followUp)
            if (handled != null) return handled
        }

        return when (current.state) {
            GroceryBookingState.NEED_ITEMS,
            GroceryBookingState.NEED_PREVIOUS_LIST -> handleItemsInput(current, rawText)

            GroceryBookingState.NEED_QUANTITY -> handleQuantityInput(current, rawText)
            GroceryBookingState.NEED_BRAND -> handleBrandInput(current, rawText)
            GroceryBookingState.NEED_BUDGET_PREFERENCE -> handleBudgetInput(current, rawText)
            GroceryBookingState.NEED_DELIVERY_URGENCY -> handleUrgencyInput(current, rawText)
            GroceryBookingState.NEED_DELIVERY_LOCATION -> handleLocationInput(current, rawText)
            GroceryBookingState.NEED_REPLACEMENT_PREFERENCE -> handleReplacementInput(current, rawText)

            GroceryBookingState.CHECKING_PERMISSIONS,
            GroceryBookingState.PERMISSION_BLOCKED -> {
                if (intentParser.isNegative(rawText)) {
                    cancelSession()
                } else {
                    current.state = GroceryBookingState.PARSING_REQUEST
                    advanceFromCurrentRequest(null)
                }
            }

            GroceryBookingState.SHOWING_COMPARISON,
            GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE -> handleSelectionInput(current, rawText)

            GroceryBookingState.REFINING_SEARCH -> handleRefineInput(current, rawText)

            GroceryBookingState.SHOWING_FINAL_SUMMARY,
            GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION -> handleFinalConfirmationInput(current, rawText)

            GroceryBookingState.ASKING_PAYMENT_METHOD -> handlePaymentMethodInput(current, rawText)

            GroceryBookingState.CHECKING_WALLET_BALANCE,
            GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION -> handleWalletConfirmationInput(current, rawText)

            GroceryBookingState.CHECKING_COD,
            GroceryBookingState.WAITING_FOR_COD_CONFIRMATION -> handleCodConfirmationInput(current, rawText)

            GroceryBookingState.OPENING_PAYMENT_PAGE,
            GroceryBookingState.WAITING_FOR_ORDER_RESPONSE,
            GroceryBookingState.BOOKING -> handleOrderResponseInput(current, rawText)

            GroceryBookingState.HANDLING_UNAVAILABLE_ITEMS -> handleUnavailableInput(current, rawText)

            else -> {
                if (intentParser.isNegative(rawText)) {
                    cancelSession()
                } else {
                    advanceFromCurrentRequest(null)
                }
            }
        }
    }

    fun cancelSession(): GroceryBookingResult {
        val current = session
        if (current == null) {
            return GroceryBookingResult(
                state = GroceryBookingState.CANCELLED,
                message = GroceryBookingVoiceResponses.bookingCancelled()
            )
        }

        current.state = GroceryBookingState.CANCELLED
        GroceryLogger.i("cancel_grocery", mapOf("rawText" to current.request.rawText))
        val result = GroceryBookingResult(
            state = GroceryBookingState.CANCELLED,
            message = GroceryBookingVoiceResponses.bookingCancelled(),
            request = current.toRequest(),
            currentState = GroceryBookingState.CANCELLED
        )
        clearSession()
        return result
    }

    private fun advanceFromCurrentRequest(parsed: GroceryIntentParseResult?): GroceryBookingResult {
        val current = requireSession()
        syncSession(current, parsed)

        when (val missingStep = determineMissingDetailStep(current)) {
            null -> Unit
            else -> return resultFor(current, missingStep.first, missingStep.second)
        }

        if (!checkPermissions(current)) {
            return permissionBlockedResult(current)
        }

        return when {
            current.providerPreference != null &&
                !current.compareRequested &&
                !current.wantsCheapest &&
                !current.wantsFirstOne ->
                prepareSpecificProvider(current)

            else -> compareAcrossProviders(current)
        }
    }

    private fun handleFollowUp(current: GroceryBookingSession, followUp: GroceryFollowUpCommand): GroceryBookingResult? {
        when {
            followUp.type == GroceryFollowUpType.CANCEL -> return cancelSession()
            followUp.type == GroceryFollowUpType.CONFIRM -> {
                if (current.state == GroceryBookingState.SHOWING_FINAL_SUMMARY ||
                    current.state == GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION
                ) {
                    return askPaymentMethod(current)
                }

                if (current.state == GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION) {
                    return handleWalletConfirmationInput(current, "yes")
                }

                if (current.state == GroceryBookingState.WAITING_FOR_COD_CONFIRMATION) {
                    return handleCodConfirmationInput(current, "yes")
                }
            }
            followUp.searchAgainRequested -> return restartSearch(current)
            followUp.compareRequested || followUp.type == GroceryFollowUpType.COMPARE -> return compareAcrossProviders(current)
            followUp.refineTarget != null -> return handleRefineCommand(current, followUp)
            followUp.replacementRequested || followUp.removeRequested -> return handleUnavailableChoice(current, followUp)
            followUp.paymentPreference != null && current.state in setOf(
                GroceryBookingState.ASKING_PAYMENT_METHOD,
                GroceryBookingState.CHECKING_WALLET_BALANCE,
                GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION,
                GroceryBookingState.CHECKING_COD,
                GroceryBookingState.WAITING_FOR_COD_CONFIRMATION
            ) -> return handlePaymentMethodSelection(current, followUp.paymentPreference)
        }

        if (followUp.providerPreference != null ||
            followUp.selectionIndex != null ||
            followUp.selectionPreference != null ||
            followUp.wantsCheapest ||
            followUp.wantsFastest ||
            followUp.wantsBestQuality ||
            followUp.wantsBestOverall
        ) {
            return handleSelectionCommand(current, followUp)
        }

        if (followUp.type == GroceryFollowUpType.BRAND_PREFERENCE || followUp.type == GroceryFollowUpType.REGULAR) {
            return handleBrandPreference(current, followUp.brandPreference ?: "regular")
        }

        if (followUp.type == GroceryFollowUpType.REORDER) {
            current.requirementProfile = current.requirementProfile.copy(reorderMode = true, previousListMode = true)
            current.request = current.toRequest()
            current.state = GroceryBookingState.NEED_PREVIOUS_LIST
            current.lastPrompt = GroceryBookingVoiceResponses.askPreviousList()
            return resultFor(current, GroceryBookingState.NEED_PREVIOUS_LIST, GroceryBookingVoiceResponses.askPreviousList())
        }

        if (followUp.type == GroceryFollowUpType.ADD_ITEM && followUp.item != null) {
            current.basket.add(followUp.item)
            current.request = current.toRequest()
            current.state = GroceryBookingState.PARSING_REQUEST
            return advanceFromCurrentRequest(null)
        }

        if (followUp.type == GroceryFollowUpType.REMOVE_ITEM && followUp.itemQuery != null) {
            val removed = current.basket.removeMatching(followUp.itemQuery)
            current.request = current.toRequest()
            if (removed != null) {
                current.state = GroceryBookingState.PARSING_REQUEST
                return advanceFromCurrentRequest(null)
            }
            return GroceryBookingResult(
                state = current.state,
                message = "I could not find that item in the basket.",
                request = current.toRequest()
            )
        }

        return null
    }

    private fun handleItemsInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val parsed = intentParser.parseInitialGroceryRequest(rawText)
        if (parsed?.basket?.items?.isNotEmpty() == true) {
            current.basket.addAll(parsed.basket.items)
            current.request = current.toRequest()
            current.state = GroceryBookingState.PARSING_REQUEST
            return advanceFromCurrentRequest(parsed)
        }

        val item = rawText.trim().takeIf { it.isNotBlank() }?.let {
            GroceryItem(name = it, rawText = it)
        }
        if (item != null) {
            current.basket.add(item)
            current.request = current.toRequest()
            current.state = GroceryBookingState.PARSING_REQUEST
            return advanceFromCurrentRequest(null)
        }

        val message = if (current.state == GroceryBookingState.NEED_PREVIOUS_LIST) {
            GroceryBookingVoiceResponses.askPreviousList()
        } else {
            GroceryBookingVoiceResponses.askItems()
        }
        current.lastPrompt = message
        return resultFor(current, current.state, message)
    }

    private fun handleQuantityInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val targetIndex = current.pendingItemIndex ?: current.basket.items.indexOfFirst { it.quantityText.isNullOrBlank() }
        val updated = parseQuantityResponse(rawText) ?: return resultFor(current, GroceryBookingState.NEED_QUANTITY, GroceryBookingVoiceResponses.askQuantity(itemNameForIndex(current, targetIndex)))
        if (targetIndex < 0 || targetIndex >= current.basket.items.size) {
            return resultFor(current, GroceryBookingState.NEED_QUANTITY, GroceryBookingVoiceResponses.askQuantity())
        }

        val currentItem = current.basket.items[targetIndex]
        current.basket.items[targetIndex] = currentItem.copy(
            quantityValue = updated.first ?: currentItem.quantityValue,
            quantityText = updated.second ?: currentItem.quantityText,
            unit = updated.third ?: currentItem.unit
        )
        current.pendingItemIndex = null
        current.pendingItemField = null
        current.request = current.toRequest()
        current.state = GroceryBookingState.PARSING_REQUEST
        return advanceFromCurrentRequest(null)
    }

    private fun handleBrandInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val brand = parseBrandResponse(rawText)
        if (brand == null && rawText.isBlank()) {
            return resultFor(current, GroceryBookingState.NEED_BRAND, GroceryBookingVoiceResponses.askBrandPreference(itemNameForIndex(current, current.pendingItemIndex)))
        }

        current.brandPreference = brand ?: rawText.trim().takeIf { it.isNotBlank() }

        val targetIndex = current.pendingItemIndex ?: current.basket.items.indexOfFirst { it.brand.isNullOrBlank() }
        if (targetIndex >= 0 && targetIndex < current.basket.items.size) {
            current.basket.items[targetIndex] = current.basket.items[targetIndex].copy(
                brand = current.brandPreference
            )
        } else if (!current.brandPreference.isNullOrBlank()) {
            current.basket.items.replaceAll { item ->
                if (item.brand.isNullOrBlank()) item.copy(brand = current.brandPreference) else item
            }
        }

        current.pendingItemIndex = null
        current.pendingItemField = null
        current.request = current.toRequest()
        current.state = GroceryBookingState.PARSING_REQUEST
        return advanceFromCurrentRequest(null)
    }

    private fun handleBudgetInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val budget = intentParser.extractBudgetPreference(rawText)
        if (budget == null) {
            return resultFor(current, GroceryBookingState.NEED_BUDGET_PREFERENCE, GroceryBookingVoiceResponses.askBudgetPreference())
        }

        current.budgetPreference = budget
        current.request = current.toRequest()
        current.state = GroceryBookingState.PARSING_REQUEST
        return advanceFromCurrentRequest(null)
    }

    private fun handleUrgencyInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val urgency = intentParser.extractDeliveryUrgency(rawText)
        if (urgency == null) {
            return resultFor(current, GroceryBookingState.NEED_DELIVERY_URGENCY, GroceryBookingVoiceResponses.askDeliveryUrgency())
        }

        current.deliveryUrgency = urgency
        current.request = current.toRequest()
        current.state = GroceryBookingState.PARSING_REQUEST
        return advanceFromCurrentRequest(null)
    }

    private fun handleLocationInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val normalized = rawText.lowercase(Locale.US).trim()
        current.useCurrentLocation = normalized.contains("current location") || normalized.contains("current address")
        if (!current.useCurrentLocation) {
            current.deliveryLocation = rawText.trim().takeIf { it.isNotBlank() }
        }

        current.request = current.toRequest()
        current.state = GroceryBookingState.PARSING_REQUEST
        return advanceFromCurrentRequest(null)
    }

    private fun handleReplacementInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val followUp = intentParser.parseFollowUpCommand(rawText)
        return handleUnavailableChoice(
            current,
            followUp ?: GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.REPLACEMENT,
                replacementRequested = true
            )
        ) ?: resultFor(current, GroceryBookingState.NEED_REPLACEMENT_PREFERENCE, GroceryBookingVoiceResponses.askReplacementPreference())
    }

    private fun handleSelectionInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val followUp = intentParser.parseFollowUpCommand(rawText)
        return handleSelectionCommand(
            current,
            followUp ?: GroceryFollowUpCommand(
                rawText = rawText,
                type = GroceryFollowUpType.SELECTION,
                selectionPreference = parseSelectionPreference(rawText)
            )
        )
    }

    private fun handleFinalConfirmationInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        if (intentParser.isNegative(rawText)) {
            return cancelSession()
        }

        if (!intentParser.isPositive(rawText) && !current.finalUserConfirmed) {
            val message = current.finalSummary?.let { GroceryBookingVoiceResponses.showFinalSummary(it) }
                ?: current.selectedCandidate?.let { GroceryBookingVoiceResponses.askFinalConfirmation(it) }
                ?: GroceryBookingVoiceResponses.askItems()
            return resultFor(current, current.state, message)
        }

        current.finalUserConfirmed = true
        current.request = current.toRequest()
        return askPaymentMethod(current)
    }

    private fun handlePaymentMethodInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val method = intentParser.extractPaymentPreference(rawText) ?: current.paymentMethod
        return if (method != null) {
            handlePaymentMethodSelection(current, method)
        } else {
            resultFor(current, GroceryBookingState.ASKING_PAYMENT_METHOD, GroceryBookingVoiceResponses.askPaymentMethod())
        }
    }

    private fun handleWalletConfirmationInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        if (intentParser.isNegative(rawText)) {
            current.state = GroceryBookingState.ASKING_PAYMENT_METHOD
            return resultFor(current, GroceryBookingState.ASKING_PAYMENT_METHOD, GroceryBookingVoiceResponses.askPaymentMethod())
        }

        if (!intentParser.isPositive(rawText)) {
            return resultFor(
                current,
                GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION,
                current.finalSummary?.let { GroceryBookingVoiceResponses.askWalletConfirmation(it, current.walletBalanceText ?: "wallet balance") }
                    ?: GroceryBookingVoiceResponses.walletBalanceUnknown()
            )
        }

        return placeOrderWithPaymentMethod(current, GroceryPaymentMethod.WALLET)
    }

    private fun handleCodConfirmationInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        if (intentParser.isNegative(rawText)) {
            current.state = GroceryBookingState.ASKING_PAYMENT_METHOD
            return resultFor(current, GroceryBookingState.ASKING_PAYMENT_METHOD, GroceryBookingVoiceResponses.askPaymentMethod())
        }

        if (!intentParser.isPositive(rawText)) {
            return resultFor(
                current,
                GroceryBookingState.WAITING_FOR_COD_CONFIRMATION,
                current.finalSummary?.let { GroceryBookingVoiceResponses.askCodConfirmation(it) }
                    ?: GroceryBookingVoiceResponses.askPaymentMethod()
            )
        }

        return placeOrderWithPaymentMethod(current, GroceryPaymentMethod.COD)
    }

    private fun handleOrderResponseInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val confirmation = accessibilityService.detectOrderConfirmation()
            ?: current.orderConfirmation

        if (confirmation?.placed == true) {
            return completeOrder(current, confirmation)
        }

        if (intentParser.isNegative(rawText)) {
            return manualActionRequiredResult(current, current.selectedCandidate?.provider, "order confirmation")
        }

        if (intentParser.isPositive(rawText) || rawText.isBlank()) {
            val freshConfirmation = accessibilityService.detectOrderConfirmation()
            if (freshConfirmation?.placed == true) {
                return completeOrder(current, freshConfirmation)
            }
        }

        return manualActionRequiredResult(current, current.selectedCandidate?.provider, "order confirmation")
    }

    private fun handleUnavailableInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val followUp = intentParser.parseFollowUpCommand(rawText)
        return handleUnavailableChoice(current, followUp ?: GroceryFollowUpCommand(rawText = rawText, type = GroceryFollowUpType.REPLACEMENT, replacementRequested = true))
            ?: resultFor(current, GroceryBookingState.NEED_REPLACEMENT_PREFERENCE, GroceryBookingVoiceResponses.askReplacementPreference())
    }

    private fun handleSelectionCommand(current: GroceryBookingSession, followUp: GroceryFollowUpCommand): GroceryBookingResult {
        val comparison = current.comparisonResult ?: compareAcrossProviders(current).comparisonResult ?: return resultFor(current, GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE, GroceryBookingVoiceResponses.askProviderChoice(GroceryComparisonResult(emptyList())))

        val selected = selectCandidate(comparison, followUp)
        if (selected == null) {
            current.state = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE
            val message = GroceryBookingVoiceResponses.askProviderChoice(comparison)
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE,
                message = message,
                request = current.toRequest(),
                comparisonResult = comparison,
                availableProviders = current.availableProviders,
                skippedProviders = current.skippedProviders,
                providerFailures = current.providerFailures,
                providerResults = comparison.providerResults,
                currentState = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE
            )
        }

        current.selectedCandidate = selected
        current.finalSummary = buildFinalSummary(current, selected, comparison)
        current.finalConfirmationAsked = true
        current.state = GroceryBookingState.SHOWING_FINAL_SUMMARY
        val message = GroceryBookingVoiceResponses.showFinalSummary(current.finalSummary!!)
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.SHOWING_FINAL_SUMMARY,
            message = message,
            request = current.toRequest(),
            comparisonResult = comparison,
            selectedCandidate = selected,
            finalSummary = current.finalSummary,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            providerResults = comparison.providerResults,
            finalConfirmationAsked = true,
            currentState = GroceryBookingState.SHOWING_FINAL_SUMMARY
        )
    }

    private fun handleRefineCommand(current: GroceryBookingSession, followUp: GroceryFollowUpCommand): GroceryBookingResult {
        when (followUp.refineTarget) {
            GroceryRefineTarget.ITEM -> {
                current.basket.clear()
                current.request = current.toRequest()
                current.state = GroceryBookingState.NEED_ITEMS
                val message = GroceryBookingVoiceResponses.askItems()
                current.lastPrompt = message
                return resultFor(current, GroceryBookingState.NEED_ITEMS, message)
            }

            GroceryRefineTarget.BRAND -> {
                current.brandPreference = null
                current.pendingItemIndex = current.basket.items.indexOfFirst { it.brand.isNullOrBlank() }.takeIf { it >= 0 }
                current.request = current.toRequest()
                current.state = GroceryBookingState.NEED_BRAND
                val message = GroceryBookingVoiceResponses.askBrandPreference(itemNameForIndex(current, current.pendingItemIndex))
                current.lastPrompt = message
                return resultFor(current, GroceryBookingState.NEED_BRAND, message)
            }

            GroceryRefineTarget.QUANTITY -> {
                current.pendingItemIndex = current.basket.items.indexOfFirst { it.quantityText.isNullOrBlank() }.takeIf { it >= 0 }
                current.request = current.toRequest()
                current.state = GroceryBookingState.NEED_QUANTITY
                val message = GroceryBookingVoiceResponses.askQuantity(itemNameForIndex(current, current.pendingItemIndex))
                current.lastPrompt = message
                return resultFor(current, GroceryBookingState.NEED_QUANTITY, message)
            }

            GroceryRefineTarget.BUDGET -> {
                current.state = GroceryBookingState.NEED_BUDGET_PREFERENCE
                current.budgetPreference = null
                current.request = current.toRequest()
                val message = GroceryBookingVoiceResponses.askBudgetPreference()
                current.lastPrompt = message
                return resultFor(current, GroceryBookingState.NEED_BUDGET_PREFERENCE, message)
            }

            GroceryRefineTarget.DELIVERY_TIME -> {
                current.state = GroceryBookingState.NEED_DELIVERY_URGENCY
                current.deliveryUrgency = null
                current.request = current.toRequest()
                val message = GroceryBookingVoiceResponses.askDeliveryUrgency()
                current.lastPrompt = message
                return resultFor(current, GroceryBookingState.NEED_DELIVERY_URGENCY, message)
            }

            GroceryRefineTarget.PROVIDER -> {
                current.state = GroceryBookingState.CHECKING_PROVIDERS
                current.providerPreference = null
                current.request = current.toRequest()
                return compareAcrossProviders(current)
            }

            GroceryRefineTarget.LOCATION -> {
                current.state = GroceryBookingState.NEED_DELIVERY_LOCATION
                current.deliveryLocation = null
                current.useCurrentLocation = false
                current.request = current.toRequest()
                val message = GroceryBookingVoiceResponses.askDeliveryLocation()
                current.lastPrompt = message
                return resultFor(current, GroceryBookingState.NEED_DELIVERY_LOCATION, message)
            }

            GroceryRefineTarget.SEARCH,
            null -> {
                return restartSearch(current)
            }
        }
    }

    private fun handleUnavailableChoice(current: GroceryBookingSession, followUp: GroceryFollowUpCommand): GroceryBookingResult? {
        val selected = current.selectedCandidate
        val unavailable = selected?.summary?.unavailableItems.orEmpty()
        if (unavailable.isEmpty() && !followUp.removeRequested && !followUp.replacementRequested) {
            return null
        }

        if (followUp.removeRequested) {
            unavailable.forEach { item ->
                current.basket.removeMatching(item)
            }
            current.request = current.toRequest()
            current.state = GroceryBookingState.PARSING_REQUEST
            return advanceFromCurrentRequest(null)
        }

        if (followUp.replacementRequested && followUp.item != null) {
            current.basket.add(followUp.item)
            current.request = current.toRequest()
            current.state = GroceryBookingState.PARSING_REQUEST
            return advanceFromCurrentRequest(null)
        }

        current.state = GroceryBookingState.NEED_REPLACEMENT_PREFERENCE
        val message = GroceryBookingVoiceResponses.askReplacementPreference(unavailable.firstOrNull())
        current.lastPrompt = message
        return resultFor(current, GroceryBookingState.NEED_REPLACEMENT_PREFERENCE, message)
    }

    private fun handleBrandPreference(current: GroceryBookingSession, brand: String): GroceryBookingResult {
        current.brandPreference = brand.takeIf { it.isNotBlank() }
        if (!current.brandPreference.isNullOrBlank()) {
            current.basket.items.replaceAll { item ->
                if (item.brand.isNullOrBlank()) item.copy(brand = current.brandPreference) else item
            }
        }
        current.request = current.toRequest()
        current.state = GroceryBookingState.PARSING_REQUEST
        return advanceFromCurrentRequest(null)
    }

    private fun handlePaymentMethodSelection(current: GroceryBookingSession, paymentMethod: GroceryPaymentMethod): GroceryBookingResult {
        current.paymentMethod = paymentMethod
        current.request = current.toRequest()
        return when (paymentMethod) {
            GroceryPaymentMethod.UPI,
            GroceryPaymentMethod.CARD,
            GroceryPaymentMethod.NET_BANKING -> placeOrderWithPaymentMethod(current, paymentMethod)

            GroceryPaymentMethod.WALLET -> handleWalletPaymentFlow(current)
            GroceryPaymentMethod.COD -> handleCodPaymentFlow(current)
            GroceryPaymentMethod.MANUAL -> manualActionRequiredResult(current, current.selectedCandidate?.provider, "manual payment")
        }
    }

    private fun handleWalletPaymentFlow(current: GroceryBookingSession): GroceryBookingResult {
        current.state = GroceryBookingState.CHECKING_WALLET_BALANCE
        val balance = accessibilityService.detectWalletBalance()
        val finalPrice = current.finalSummary?.finalPrice ?: current.selectedCandidate?.summary?.finalPayableValue

        if (balance == null) {
            current.state = GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION
            val message = GroceryBookingVoiceResponses.walletBalanceUnknown()
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION,
                message = message,
                request = current.toRequest(),
                selectedCandidate = current.selectedCandidate,
                finalSummary = current.finalSummary,
                manualActionRequired = true,
                manualActionReason = "wallet balance unknown",
                finalConfirmationAsked = true,
                currentState = GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION
            )
        }

        if (finalPrice != null && balance < finalPrice) {
            current.state = GroceryBookingState.ASKING_PAYMENT_METHOD
            val message = GroceryBookingVoiceResponses.walletInsufficient(balance, finalPrice)
            current.lastPrompt = message
            return resultFor(current, GroceryBookingState.ASKING_PAYMENT_METHOD, message, manualActionRequired = false)
        }

        current.state = GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION
        val summary = current.finalSummary ?: buildFinalSummary(current, current.selectedCandidate, current.comparisonResult)
        current.walletBalanceText = "₹$balance"
        val message = GroceryBookingVoiceResponses.askWalletConfirmation(summary, current.walletBalanceText ?: "wallet balance")
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION,
            message = message,
            request = current.toRequest(),
            selectedCandidate = current.selectedCandidate,
            finalSummary = summary,
            finalConfirmationAsked = true,
            currentState = GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION
        )
    }

    private fun handleCodPaymentFlow(current: GroceryBookingSession): GroceryBookingResult {
        current.state = GroceryBookingState.CHECKING_COD
        val available = accessibilityService.detectCodAvailability()
        if (available == false) {
            current.state = GroceryBookingState.ASKING_PAYMENT_METHOD
            val message = GroceryBookingVoiceResponses.codUnavailable()
            current.lastPrompt = message
            return resultFor(current, GroceryBookingState.ASKING_PAYMENT_METHOD, message)
        }

        current.state = GroceryBookingState.WAITING_FOR_COD_CONFIRMATION
        val summary = current.finalSummary ?: buildFinalSummary(current, current.selectedCandidate, current.comparisonResult)
        val message = GroceryBookingVoiceResponses.askCodConfirmation(summary)
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.WAITING_FOR_COD_CONFIRMATION,
            message = message,
            request = current.toRequest(),
            selectedCandidate = current.selectedCandidate,
            finalSummary = summary,
            finalConfirmationAsked = true,
            currentState = GroceryBookingState.WAITING_FOR_COD_CONFIRMATION
        )
    }

    private fun placeOrderWithPaymentMethod(current: GroceryBookingSession, paymentMethod: GroceryPaymentMethod): GroceryBookingResult {
        current.paymentMethod = paymentMethod
        current.request = current.toRequest()
        current.state = GroceryBookingState.OPENING_PAYMENT_PAGE

        val tapped = accessibilityService.tapFinalOrderButton()
        if (!tapped) {
            return manualActionRequiredResult(current, current.selectedCandidate?.provider, "final order")
        }

        val snapshot = accessibilityService.captureScreenSnapshot()
        val manualReason = snapshot?.manualActionReason ?: accessibilityService.detectManualActionReason(snapshot?.visibleText.orEmpty())
        if (manualReason != null && isSensitiveManualReason(manualReason)) {
            return manualActionRequiredResult(
                current,
                current.selectedCandidate?.provider,
                GroceryBookingVoiceResponses.describeManualActionReason(manualReason)
            )
        }

        val confirmation = accessibilityService.detectOrderConfirmation(snapshot)
        if (confirmation?.placed == true) {
            current.orderConfirmation = confirmation
            return completeOrder(current, confirmation)
        }

        current.orderConfirmation = confirmation
        current.state = GroceryBookingState.WAITING_FOR_ORDER_RESPONSE
        val summary = current.finalSummary ?: buildFinalSummary(current, current.selectedCandidate, current.comparisonResult)
        val message = when (paymentMethod) {
            GroceryPaymentMethod.UPI,
            GroceryPaymentMethod.CARD,
            GroceryPaymentMethod.NET_BANKING -> GroceryBookingVoiceResponses.paymentManualRequired(paymentMethod)
            GroceryPaymentMethod.WALLET -> "I started the wallet payment flow. Please complete it manually if the app asks for a secret step."
            GroceryPaymentMethod.COD -> "I started the COD order flow. Please complete any manual step if the app asks for one."
            GroceryPaymentMethod.MANUAL -> GroceryBookingVoiceResponses.paymentBoundaryRequired()
        }
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.WAITING_FOR_ORDER_RESPONSE,
            message = message,
            request = current.toRequest(),
            selectedCandidate = current.selectedCandidate,
            finalSummary = summary,
            orderConfirmation = confirmation,
            manualActionRequired = true,
            manualActionReason = "payment boundary",
            finalConfirmationAsked = true,
            currentState = GroceryBookingState.WAITING_FOR_ORDER_RESPONSE
        )
    }

    private fun completeOrder(current: GroceryBookingSession, confirmation: GroceryOrderConfirmation): GroceryBookingResult {
        current.orderConfirmation = confirmation
        current.state = GroceryBookingState.COMPLETED
        val summary = current.finalSummary ?: buildFinalSummary(current, current.selectedCandidate, current.comparisonResult)
        val message = GroceryBookingVoiceResponses.orderPlaced(summary, confirmation)
        val result = GroceryBookingResult(
            state = GroceryBookingState.COMPLETED,
            message = message,
            request = current.toRequest(),
            selectedCandidate = current.selectedCandidate,
            finalSummary = summary,
            orderConfirmation = confirmation,
            comparisonResult = current.comparisonResult,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            providerResults = current.providerResults.toList(),
            finalConfirmationAsked = true,
            finalUserConfirmed = true,
            currentState = GroceryBookingState.COMPLETED
        )
        clearSession()
        return result
    }

    private fun compareAcrossProviders(current: GroceryBookingSession): GroceryBookingResult {
        syncSession(current, null)
        current.state = GroceryBookingState.CHECKING_PROVIDERS

        val discovery = providerRegistry.discoverProviders()
        current.availableProviders = discovery.installedProviders
        current.skippedProviders.clear()
        current.skippedProviders.putAll(discovery.skippedProviders)
        current.providerFailures.clear()
        current.providerResults.clear()

        if (current.availableProviders.isEmpty()) {
            current.state = GroceryBookingState.NO_PROVIDER_AVAILABLE
            current.manualActionReason = "no installed grocery apps"
            val message = GroceryBookingVoiceResponses.noProvidersAvailable(discovery.skippedProviders)
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.NO_PROVIDER_AVAILABLE,
                message = message,
                request = current.toRequest(),
                availableProviders = emptyList(),
                skippedProviders = discovery.skippedProviders,
                manualActionRequired = true,
                manualActionReason = current.manualActionReason,
                currentState = GroceryBookingState.NO_PROVIDER_AVAILABLE
            )
        }

        if (!accessibilityReadyProvider()) {
            return accessibilityNotReadyResult(current)
        }

        val permissions = permissionStatusProvider().copy(
            accessibilityReady = accessibilityReadyProvider(),
            locationPermissionRequired = current.useCurrentLocation
        )
        permissions.blockedReason()?.let { reason ->
            return permissionBlockedResult(current, reason)
        }

        val providersToCheck = when {
            current.providerPreference != null &&
                !current.compareRequested &&
                !current.wantsCheapest &&
                !current.wantsFirstOne -> listOf(current.providerPreference!!)

            else -> current.availableProviders
        }

        val candidates = mutableListOf<GroceryCartCandidate>()
        val providerResults = mutableListOf<GroceryProviderResult>()
        val providerFailures = linkedMapOf<GroceryProvider, String>()
        val skippedProviders = linkedMapOf<GroceryProvider, String>()

        providersToCheck.forEach { provider ->
            val launchPlan = deepLinkBuilder.buildLaunchPlan(provider, current.toRequest(), current.selectedCandidate)
            if (launchPlan.intent == null) {
                val reason = launchPlan.failureReason ?: "launch intent unavailable"
                providerFailures[provider] = reason
                skippedProviders[provider] = reason
                return@forEach
            }

            val launched = providerLauncher.launch(launchPlan.intent)
            if (!launched) {
                providerFailures[provider] = "failed to launch provider app"
                skippedProviders[provider] = "failed to launch provider app"
                return@forEach
            }

            val packageName = providerRegistry.installedPackageName(provider)
            if (!packageName.isNullOrBlank()) {
                accessibilityService.waitForForegroundPackage(setOf(packageName))
            }

            current.state = GroceryBookingState.OPENING_PROVIDER
            val candidate = accessibilityService.collectCartCandidate(
                provider = provider,
                basket = current.basket,
                couponCode = current.couponCode,
                requirementProfile = current.requirementProfile
            )
            val providerResult = candidate.providerResult ?: GroceryProviderResult(
                provider = provider,
                productOptions = candidate.productOptions,
                summary = candidate.summary,
                blocked = candidate.manualActionReason != null,
                partial = candidate.summary.partial,
                blockReason = candidate.manualActionReason?.displayText,
                manualActionReason = candidate.manualActionReason,
                searchQueries = candidate.searchQueries
            )
            providerResults.add(providerResult)

            if (candidate.manualActionReason != null) {
                providerFailures[provider] = candidate.manualActionReason.name
                skippedProviders[provider] = candidate.manualActionReason.displayText
            } else {
                candidates.add(candidate)
            }
        }

        current.providerFailures.clear()
        current.providerFailures.putAll(providerFailures)
        current.skippedProviders.clear()
        current.skippedProviders.putAll(skippedProviders)
        current.providerResults.addAll(providerResults)

        if (candidates.isEmpty()) {
            current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
            val reason = if (providerFailures.isNotEmpty()) {
                providerFailures.entries.joinToString(separator = ", ") { "${it.key.displayName()}: ${it.value}" }
            } else {
                "no readable cart could be collected"
            }
            current.manualActionReason = reason
            val message = GroceryBookingVoiceResponses.bookingFailed(reason)
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
                message = message,
                request = current.toRequest(),
                providerResults = providerResults,
                availableProviders = current.availableProviders,
                skippedProviders = skippedProviders,
                providerFailures = providerFailures,
                manualActionRequired = true,
                manualActionReason = reason,
                currentState = GroceryBookingState.MANUAL_ACTION_REQUIRED
            )
        }

        current.state = GroceryBookingState.COMPARING_OPTIONS
        val comparison = priceComparator.compare(candidates, current.requirementProfile)
        current.comparisonResult = comparison
        current.providerResults.clear()
        current.providerResults.addAll(providerResults)
        current.selectedCandidate = when {
            current.providerPreference != null -> comparison.candidates.firstOrNull { it.provider == current.providerPreference }
            current.wantsCheapest -> comparison.cheapestCompleteCandidate ?: comparison.recommendedCandidate
            current.wantsFirstOne -> comparison.candidates.firstOrNull()
            current.requirementProfile.brandPreference != null -> comparison.bestQualityCandidate ?: comparison.recommendedCandidate
            else -> null
        }

        return if (current.selectedCandidate != null &&
            (current.providerPreference != null || current.wantsCheapest || current.wantsFirstOne)
        ) {
            current.finalSummary = buildFinalSummary(current, current.selectedCandidate, comparison)
            current.finalConfirmationAsked = true
            current.state = GroceryBookingState.SHOWING_FINAL_SUMMARY
            val message = GroceryBookingVoiceResponses.showFinalSummary(current.finalSummary!!)
            current.lastPrompt = message
            GroceryBookingResult(
                state = GroceryBookingState.SHOWING_FINAL_SUMMARY,
                message = message,
                request = current.toRequest(),
                comparisonResult = comparison,
                selectedCandidate = current.selectedCandidate,
                finalSummary = current.finalSummary,
                availableProviders = current.availableProviders,
                skippedProviders = current.skippedProviders,
                providerFailures = current.providerFailures,
                providerResults = current.providerResults.toList(),
                finalConfirmationAsked = true,
                currentState = GroceryBookingState.SHOWING_FINAL_SUMMARY
            )
        } else {
            current.state = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE
            val message = GroceryBookingVoiceResponses.showComparison(comparison)
            current.lastPrompt = message
            GroceryBookingResult(
                state = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE,
                message = message,
                request = current.toRequest(),
                comparisonResult = comparison,
                availableProviders = current.availableProviders,
                skippedProviders = current.skippedProviders,
                providerFailures = current.providerFailures,
                providerResults = current.providerResults.toList(),
                currentState = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE
            )
        }
    }

    private fun prepareSpecificProvider(current: GroceryBookingSession): GroceryBookingResult {
        syncSession(current, null)
        if (!accessibilityReadyProvider()) {
            return accessibilityNotReadyResult(current)
        }

        val permissions = permissionStatusProvider().copy(
            accessibilityReady = accessibilityReadyProvider(),
            locationPermissionRequired = current.useCurrentLocation
        )
        permissions.blockedReason()?.let { reason ->
            return permissionBlockedResult(current, reason)
        }

        val provider = current.providerPreference ?: return compareAcrossProviders(current)
        current.state = GroceryBookingState.OPENING_PROVIDER
        val candidate = accessibilityService.collectCartCandidate(
            provider = provider,
            basket = current.basket,
            couponCode = current.couponCode,
            requirementProfile = current.requirementProfile
        )

        if (candidate.manualActionReason != null) {
            current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
            current.manualActionReason = candidate.manualActionReason.displayText
            val message = GroceryBookingVoiceResponses.manualActionRequired(provider, candidate.manualActionReason.displayText)
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
                message = message,
                request = current.toRequest(),
                selectedCandidate = candidate,
                manualActionRequired = true,
                manualActionReason = candidate.manualActionReason.displayText,
                currentState = GroceryBookingState.MANUAL_ACTION_REQUIRED
            )
        }

        current.selectedCandidate = candidate
        current.finalSummary = buildFinalSummary(current, candidate, null)
        current.finalConfirmationAsked = true
        current.state = GroceryBookingState.SHOWING_FINAL_SUMMARY
        val message = GroceryBookingVoiceResponses.showFinalSummary(current.finalSummary!!)
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.SHOWING_FINAL_SUMMARY,
            message = message,
            request = current.toRequest(),
            selectedCandidate = candidate,
            finalSummary = current.finalSummary,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            providerResults = listOfNotNull(candidate.providerResult),
            finalConfirmationAsked = true,
            currentState = GroceryBookingState.SHOWING_FINAL_SUMMARY
        )
    }

    private fun askPaymentMethod(current: GroceryBookingSession): GroceryBookingResult {
        current.state = GroceryBookingState.ASKING_PAYMENT_METHOD
        val message = GroceryBookingVoiceResponses.askPaymentMethod()
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.ASKING_PAYMENT_METHOD,
            message = message,
            request = current.toRequest(),
            selectedCandidate = current.selectedCandidate,
            finalSummary = current.finalSummary,
            comparisonResult = current.comparisonResult,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            providerResults = current.providerResults.toList(),
            finalConfirmationAsked = true,
            finalUserConfirmed = current.finalUserConfirmed,
            currentState = GroceryBookingState.ASKING_PAYMENT_METHOD
        )
    }

    private fun restartSearch(current: GroceryBookingSession): GroceryBookingResult {
        current.comparisonResult = null
        current.selectedCandidate = null
        current.finalSummary = null
        current.orderConfirmation = null
        current.state = GroceryBookingState.PARSING_REQUEST
        current.request = current.toRequest()
        return advanceFromCurrentRequest(null)
    }

    private fun buildFinalSummary(
        current: GroceryBookingSession,
        selected: GroceryCartCandidate?,
        comparison: GroceryComparisonResult?
    ): GroceryOrderFinalSummary {
        val candidate = selected ?: return GroceryOrderFinalSummary(
            provider = current.providerPreference,
            appName = current.providerPreference?.displayName(),
            deliveryAddress = current.deliveryLocation ?: if (current.useCurrentLocation) "current location" else null,
            paymentMethod = current.paymentMethod
        )

        val summary = candidate.summary
        return GroceryOrderFinalSummary(
            appName = candidate.provider.displayName(),
            provider = candidate.provider,
            items = candidate.productOptions,
            itemTotal = summary.itemSubtotal,
            deliveryFee = summary.deliveryFee,
            handlingFee = summary.handlingFee,
            couponSaving = summary.couponDiscount,
            finalPrice = summary.finalPayableValue,
            deliveryAddress = current.deliveryLocation ?: if (current.useCurrentLocation) "current location" else null,
            deliveryTime = summary.etaText ?: summary.etaMinutes?.let { "$it min" },
            paymentMethod = current.paymentMethod,
            paymentOptions = GroceryPaymentMethod.values().toList(),
            unavailableItems = summary.unavailableItems,
            replacedItems = summary.replacementItems,
            bestCartSuggestion = comparison?.recommendedCandidate?.provider?.displayName() ?: selected.provider.displayName(),
            bestCartReason = candidate.rankingReason?.summary ?: comparison?.recommendedCandidate?.rankingReason?.summary,
            orderId = current.orderConfirmation?.orderId,
            warning = when {
                summary.partial -> "Partial cart read from provider UI"
                summary.unavailableItems.isNotEmpty() -> "Some items were unavailable"
                else -> null
            }
        )
    }

    private fun selectCandidate(
        comparison: GroceryComparisonResult,
        followUp: GroceryFollowUpCommand
    ): GroceryCartCandidate? {
        followUp.providerPreference?.let { provider ->
            return comparison.candidates.firstOrNull { it.provider == provider }
        }

        val selectionIndex = followUp.selectionIndex
        if (selectionIndex != null && selectionIndex > 0) {
            return comparison.candidates.getOrNull(selectionIndex - 1)
        }

        return when {
            followUp.selectionPreference == GrocerySelectionPreference.FIRST || followUp.wantsFirstOne -> comparison.candidates.firstOrNull()
            followUp.selectionPreference == GrocerySelectionPreference.SECOND -> comparison.candidates.getOrNull(1)
            followUp.selectionPreference == GrocerySelectionPreference.THIRD -> comparison.candidates.getOrNull(2)
            followUp.selectionPreference == GrocerySelectionPreference.CHEAPEST || followUp.wantsCheapest -> comparison.cheapestCompleteCandidate ?: comparison.recommendedCandidate
            followUp.selectionPreference == GrocerySelectionPreference.FASTEST || followUp.wantsFastest -> comparison.fastestCandidate ?: comparison.recommendedCandidate
            followUp.selectionPreference == GrocerySelectionPreference.BEST_QUALITY || followUp.wantsBestQuality -> comparison.bestQualityCandidate ?: comparison.recommendedCandidate
            followUp.selectionPreference == GrocerySelectionPreference.BEST_OVERALL || followUp.wantsBestOverall -> comparison.bestOverallCandidate ?: comparison.recommendedCandidate
            followUp.selectionPreference == GrocerySelectionPreference.PROVIDER && currentProviderPreferenceExists(comparison, followUp.providerPreference) -> comparison.candidates.firstOrNull { it.provider == followUp.providerPreference }
            else -> null
        }
    }

    private fun currentProviderPreferenceExists(comparison: GroceryComparisonResult, provider: GroceryProvider?): Boolean {
        return provider != null && comparison.candidates.any { it.provider == provider }
    }

    private fun parseSelectionPreference(rawText: String): GrocerySelectionPreference? {
        val normalized = rawText.lowercase(Locale.US)
        return when {
            normalized.contains("second") -> GrocerySelectionPreference.SECOND
            normalized.contains("third") -> GrocerySelectionPreference.THIRD
            normalized.contains("first") -> GrocerySelectionPreference.FIRST
            normalized.contains("cheapest") -> GrocerySelectionPreference.CHEAPEST
            normalized.contains("fastest") -> GrocerySelectionPreference.FASTEST
            normalized.contains("best quality") -> GrocerySelectionPreference.BEST_QUALITY
            normalized.contains("best overall") || normalized.contains("best option") || normalized.contains("recommended") -> GrocerySelectionPreference.BEST_OVERALL
            else -> null
        }
    }

    private fun handleRefineInput(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val followUp = intentParser.parseFollowUpCommand(rawText)
        return handleRefineCommand(current, followUp ?: GroceryFollowUpCommand(rawText = rawText, type = GroceryFollowUpType.REFINEMENT, refineTarget = GroceryRefineTarget.SEARCH))
    }

    private fun accessibilityNotReadyResult(current: GroceryBookingSession): GroceryBookingResult {
        current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = GroceryFailureReasons.BLOCKED_BY_ACCESSIBILITY_NOT_READY
        val message = AccessibilityReadiness.blockedMessage()
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
            message = message,
            request = current.toRequest(),
            manualActionRequired = true,
            manualActionReason = current.manualActionReason,
            currentState = GroceryBookingState.MANUAL_ACTION_REQUIRED
        )
    }

    private fun permissionBlockedResult(current: GroceryBookingSession, reason: String = GroceryFailureReasons.BLOCKED_BY_LOCATION_PERMISSION): GroceryBookingResult {
        current.state = GroceryBookingState.PERMISSION_BLOCKED
        current.manualActionReason = reason
        val message = when (reason) {
            GroceryFailureReasons.BLOCKED_BY_ACCESSIBILITY_NOT_READY -> AccessibilityReadiness.blockedMessage()
            GroceryFailureReasons.BLOCKED_BY_LOCATION_PERMISSION -> "Please enable location permission so I can continue the grocery flow."
            GroceryFailureReasons.BLOCKED_BY_USAGE_ACCESS -> "Please enable usage access so I can continue the grocery flow."
            else -> "Please enable the required permission so I can continue the grocery flow."
        }
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.PERMISSION_BLOCKED,
            message = message,
            request = current.toRequest(),
            manualActionRequired = true,
            manualActionReason = reason,
            currentState = GroceryBookingState.PERMISSION_BLOCKED
        )
    }

    private fun manualActionRequiredResult(current: GroceryBookingSession, provider: GroceryProvider?, reason: String): GroceryBookingResult {
        current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        val message = GroceryBookingVoiceResponses.manualActionRequired(provider, reason)
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
            message = message,
            request = current.toRequest(),
            selectedCandidate = current.selectedCandidate,
            finalSummary = current.finalSummary,
            comparisonResult = current.comparisonResult,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            providerResults = current.providerResults.toList(),
            manualActionRequired = true,
            manualActionReason = reason,
            currentState = GroceryBookingState.MANUAL_ACTION_REQUIRED
        )
    }

    private fun checkPermissions(current: GroceryBookingSession): Boolean {
        if (!accessibilityReadyProvider()) {
            return false
        }

        val permissions = permissionStatusProvider().copy(
            accessibilityReady = accessibilityReadyProvider(),
            locationPermissionRequired = current.useCurrentLocation
        )
        val blockedReason = permissions.blockedReason()
        if (blockedReason != null) {
            current.manualActionReason = blockedReason
            current.state = GroceryBookingState.PERMISSION_BLOCKED
            return false
        }

        return true
    }

    private fun itemNameForIndex(current: GroceryBookingSession, index: Int?): String? {
        if (index == null || index < 0 || index >= current.basket.items.size) return null
        return current.basket.items[index].name
    }

    private fun parseQuantityResponse(rawText: String): Triple<Double?, String?, String?>? {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return null

        val quantityPattern = Regex("""(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|l|litre|liter|ml|pack|packet|packets|packs|bottle|bottles|piece|pieces|pcs|dozen)""", RegexOption.IGNORE_CASE)
        quantityPattern.find(cleaned)?.let { match ->
            val value = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            val unit = match.groupValues.getOrNull(2)?.lowercase(Locale.US)
            val quantityText = buildString {
                value?.let {
                    append(if (it % 1.0 == 0.0) it.toInt().toString() else it.toString())
                    append(' ')
                }
                unit?.let { append(it) }
            }.trim().takeIf { it.isNotBlank() }
            return Triple(value, quantityText, unit)
        }

        val numericPattern = Regex("""\b(\d+(?:\.\d+)?)\b""")
        numericPattern.find(cleaned)?.let { match ->
            val value = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            val quantityText = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
            return Triple(value, quantityText, null)
        }

        return null
    }

    private fun parseBrandResponse(rawText: String): String? {
        val parsed = intentParser.parseInitialGroceryRequest(rawText)
        parsed?.brandPreference?.takeIf { it.isNotBlank() }?.let { return it }
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null
        return trimmed.takeIf { !it.matches(Regex("""^\d+(\.\d+)?$""")) }
    }

    private fun syncSession(current: GroceryBookingSession, parsed: GroceryIntentParseResult?) {
        if (parsed?.basket?.items?.isNotEmpty() == true && current.basket.items.isEmpty()) {
            current.basket.addAll(parsed.basket.items)
        }
        if (parsed?.providerPreference != null && current.providerPreference == null) {
            current.providerPreference = parsed.providerPreference
        }
        if (!parsed?.brandPreference.isNullOrBlank() && current.brandPreference.isNullOrBlank()) {
            current.brandPreference = parsed?.brandPreference
        }
        if (parsed?.budgetPreference != null && current.budgetPreference == null) {
            current.budgetPreference = parsed.budgetPreference
        }
        if (parsed?.deliveryUrgency != null && current.deliveryUrgency == null) {
            current.deliveryUrgency = parsed.deliveryUrgency
        }
        if (!parsed?.deliveryLocation.isNullOrBlank() && current.deliveryLocation.isNullOrBlank()) {
            current.deliveryLocation = parsed?.deliveryLocation
        }
        if (parsed?.useCurrentLocation == true) {
            current.useCurrentLocation = true
        }
        if (parsed?.paymentPreference != null && current.paymentMethod == null) {
            current.paymentMethod = parsed.paymentPreference
        }
        if (parsed?.compareRequested == true) {
            current.compareRequested = true
        }
        if (parsed?.wantsCheapest == true) {
            current.wantsCheapest = true
        }
        if (parsed?.wantsFirstOne == true) {
            current.wantsFirstOne = true
        }
        if (parsed?.applyCouponRequested == true) {
            current.applyCouponRequested = true
        }
        if (!parsed?.couponCode.isNullOrBlank() && current.couponCode.isNullOrBlank()) {
            current.couponCode = parsed?.couponCode
        }
        current.request = current.toRequest()
        current.requirementProfile = current.request.toRequirementProfile()
    }

    private fun mergeRequest(
        request: GroceryBookingRequest,
        parsed: GroceryIntentParseResult?
    ): GroceryBookingRequest {
        if (parsed == null) return request

        return request.copy(
            basket = if (request.basket.items.isNotEmpty()) request.basket else parsed.basket,
            requirementProfile = request.requirementProfile ?: parsed.requirementProfile,
            preferredProvider = request.preferredProvider ?: parsed.providerPreference,
            brandPreference = request.brandPreference ?: parsed.brandPreference,
            budgetPreference = request.budgetPreference ?: parsed.budgetPreference,
            deliveryUrgency = request.deliveryUrgency ?: parsed.deliveryUrgency,
            scheduledTime = request.scheduledTime ?: parsed.scheduledTime,
            deliveryLocation = request.deliveryLocation ?: parsed.deliveryLocation,
            useCurrentLocation = request.useCurrentLocation || parsed.useCurrentLocation,
            paymentPreference = request.paymentPreference ?: parsed.paymentPreference,
            allowComparison = request.allowComparison || parsed.allowComparison,
            reorderMode = request.reorderMode || parsed.reorderMode,
            previousListMode = request.previousListMode || parsed.previousListMode,
            wantsCheapest = request.wantsCheapest || parsed.wantsCheapest,
            wantsFirstOne = request.wantsFirstOne || parsed.wantsFirstOne,
            compareRequested = request.compareRequested || parsed.compareRequested,
            applyCouponRequested = request.applyCouponRequested || parsed.applyCouponRequested,
            couponCode = request.couponCode ?: parsed.couponCode,
            requiresFinalConfirmation = request.requiresFinalConfirmation,
            safetyNotes = request.safetyNotes ?: parsed.requirementProfile?.safetyNotes,
            finalUserConfirmed = request.finalUserConfirmed
        )
    }

    private fun determineMissingDetailStep(current: GroceryBookingSession): Pair<GroceryBookingState, String>? {
        if (current.basket.items.isEmpty()) {
            return if (current.requirementProfile.reorderMode || current.requirementProfile.previousListMode) {
                GroceryBookingState.NEED_PREVIOUS_LIST to GroceryBookingVoiceResponses.askPreviousList()
            } else {
                GroceryBookingState.NEED_ITEMS to GroceryBookingVoiceResponses.askItems()
            }
        }

        val quantityIndex = current.basket.items.indexOfFirst { it.quantityText.isNullOrBlank() }
        if (quantityIndex >= 0) {
            current.pendingItemIndex = quantityIndex
            current.pendingItemField = GroceryPendingItemField.QUANTITY
            return GroceryBookingState.NEED_QUANTITY to GroceryBookingVoiceResponses.askQuantity(current.basket.items[quantityIndex].name)
        }

        val brandIndex = current.basket.items.indexOfFirst { it.brand.isNullOrBlank() }
        if (brandIndex >= 0 && current.brandPreference.isNullOrBlank()) {
            current.pendingItemIndex = brandIndex
            current.pendingItemField = GroceryPendingItemField.BRAND
            return GroceryBookingState.NEED_BRAND to GroceryBookingVoiceResponses.askBrandPreference(current.basket.items[brandIndex].name)
        }

        if (current.budgetPreference == null) {
            return GroceryBookingState.NEED_BUDGET_PREFERENCE to GroceryBookingVoiceResponses.askBudgetPreference()
        }

        if (current.deliveryUrgency == null) {
            return GroceryBookingState.NEED_DELIVERY_URGENCY to GroceryBookingVoiceResponses.askDeliveryUrgency()
        }

        if (current.deliveryLocation.isNullOrBlank() && !current.useCurrentLocation && !current.compareRequested) {
            return GroceryBookingState.NEED_DELIVERY_LOCATION to GroceryBookingVoiceResponses.askDeliveryLocation()
        }

        return null
    }

    private fun resultFor(
        current: GroceryBookingSession,
        state: GroceryBookingState,
        message: String,
        manualActionRequired: Boolean = false,
        manualActionReason: String? = null
    ): GroceryBookingResult {
        current.state = state
        current.lastPrompt = message
        return GroceryBookingResult(
            state = state,
            message = message,
            request = current.toRequest(),
            comparisonResult = current.comparisonResult,
            selectedCandidate = current.selectedCandidate,
            finalSummary = current.finalSummary,
            orderConfirmation = current.orderConfirmation,
            providerResults = current.providerResults.toList(),
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            finalConfirmationAsked = current.finalConfirmationAsked,
            manualActionRequired = manualActionRequired,
            manualActionReason = manualActionReason,
            finalUserConfirmed = current.finalUserConfirmed,
            currentState = state
        )
    }

    private fun clearSession() {
        session = null
    }

    private fun requireSession(): GroceryBookingSession {
        return session ?: error("Grocery session is not active.")
    }

    private fun isSensitiveManualReason(reason: GroceryManualActionReason?): Boolean {
        return reason in setOf(
            GroceryManualActionReason.LOGIN,
            GroceryManualActionReason.OTP,
            GroceryManualActionReason.PAYMENT,
            GroceryManualActionReason.PASSWORD,
            GroceryManualActionReason.CAPTCHA,
            GroceryManualActionReason.UPI_PIN,
            GroceryManualActionReason.CARD_CVV,
            GroceryManualActionReason.NET_BANKING,
            GroceryManualActionReason.BIOMETRIC
        )
    }
}
