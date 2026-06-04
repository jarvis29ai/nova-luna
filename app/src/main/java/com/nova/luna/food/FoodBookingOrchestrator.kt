package com.nova.luna.food

class FoodBookingOrchestrator(
    private val providerRegistry: FoodProviderRegistry,
    private val deepLinkBuilder: FoodDeepLinkBuilder,
    private val accessibilityService: FoodAccessibilityService = FoodAccessibilityService(),
    private val priceComparator: FoodPriceComparator = FoodPriceComparator(),
    private val intentParser: FoodIntentParser = FoodIntentParser(),
    private val providerLauncher: FoodProviderLauncher
) {
    private var session: FoodBookingSession? = null

    fun isActive(): Boolean {
        return session != null
    }

    fun currentState(): FoodBookingState {
        return session?.state ?: FoodBookingState.IDLE
    }

    fun start(request: FoodBookingRequest): FoodComparisonResult {
        session = FoodBookingSession(request = request)
        return advanceSession()
    }

    fun cancelSession(): FoodComparisonResult {
        val current = session ?: return FoodComparisonResult(
            state = FoodBookingState.CANCELLED,
            message = FoodBookingVoiceResponses.bookingCancelled()
        )

        current.state = FoodBookingState.CANCELLED
        val result = buildSessionResult(
            current = current,
            state = FoodBookingState.CANCELLED,
            message = FoodBookingVoiceResponses.bookingCancelled(),
            finalUserConfirmed = false
        )
        clearSession()
        return result
    }

    fun handleUserInput(rawText: String): FoodComparisonResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return promptCurrentState()
        }

        val parsedRequest = intentParser.parse(trimmed)
        if (parsedRequest != null) {
            return start(parsedRequest)
        }

        val current = session ?: return FoodComparisonResult(
            state = FoodBookingState.FAILED,
            message = FoodBookingVoiceResponses.orderFailed("no active food order session.")
        )

        return when (current.state) {
            FoodBookingState.NEED_FOOD_ITEM -> handleFoodItemResponse(trimmed)
            FoodBookingState.NEED_RESTAURANT -> handleRestaurantResponse(trimmed)
            FoodBookingState.OPENING_PROVIDERS,
            FoodBookingState.COLLECTING_QUOTES,
            FoodBookingState.SHOWING_COMPARISON,
            FoodBookingState.WAITING_FOR_PLATFORM_CHOICE -> handlePlatformChoice(trimmed)
            FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION -> handleFinalConfirmation(trimmed)
            FoodBookingState.MANUAL_ACTION_REQUIRED -> handleManualActionFollowUp(trimmed)
            FoodBookingState.PREPARING_ORDER,
            FoodBookingState.PLACING_ORDER,
            FoodBookingState.COMPLETED,
            FoodBookingState.FAILED,
            FoodBookingState.CANCELLED,
            FoodBookingState.IDLE -> promptCurrentState()
        }
    }

    private fun advanceSession(): FoodComparisonResult {
        val current = requireSession()

        if (current.request.foodItem.isNullOrBlank()) {
            current.state = FoodBookingState.NEED_FOOD_ITEM
            return buildSessionResult(current, FoodBookingState.NEED_FOOD_ITEM, FoodBookingVoiceResponses.askFoodItem())
        }

        if (current.request.restaurantName.isNullOrBlank()) {
            current.state = FoodBookingState.NEED_RESTAURANT
            return buildSessionResult(current, FoodBookingState.NEED_RESTAURANT, FoodBookingVoiceResponses.askRestaurant())
        }

        return collectAndCompareQuotes()
    }

    private fun promptCurrentState(): FoodComparisonResult {
        val current = session ?: return FoodComparisonResult(
            state = FoodBookingState.FAILED,
            message = FoodBookingVoiceResponses.orderFailed("no active food order session.")
        )

        return when (current.state) {
            FoodBookingState.NEED_FOOD_ITEM -> buildSessionResult(
                current,
                FoodBookingState.NEED_FOOD_ITEM,
                FoodBookingVoiceResponses.askFoodItem()
            )
            FoodBookingState.NEED_RESTAURANT -> buildSessionResult(
                current,
                FoodBookingState.NEED_RESTAURANT,
                FoodBookingVoiceResponses.askRestaurant()
            )
            FoodBookingState.SHOWING_COMPARISON,
            FoodBookingState.WAITING_FOR_PLATFORM_CHOICE -> buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
            FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION -> {
                val selected = current.selectedQuote
                val message = if (selected != null) {
                    FoodBookingVoiceResponses.askFinalConfirmation(selected)
                } else {
                    FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
                }
                buildSessionResult(current, FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
            }
            FoodBookingState.MANUAL_ACTION_REQUIRED -> buildSessionResult(
                current,
                FoodBookingState.MANUAL_ACTION_REQUIRED,
                FoodBookingVoiceResponses.manualActionRequired(
                    current.manualActionReason ?: "manual action required"
                ),
                manualActionRequired = true,
                manualActionReason = current.manualActionReason
            )
            FoodBookingState.COMPLETED -> buildSessionResult(
                current,
                FoodBookingState.COMPLETED,
                current.selectedQuote?.let { FoodBookingVoiceResponses.orderCompleted(it) }
                    ?: FoodBookingVoiceResponses.orderFailed("the food order has already completed.")
            )
            FoodBookingState.FAILED -> buildSessionResult(
                current,
                FoodBookingState.FAILED,
                FoodBookingVoiceResponses.orderFailed("the food order has already failed.")
            )
            FoodBookingState.CANCELLED -> buildSessionResult(
                current,
                FoodBookingState.CANCELLED,
                FoodBookingVoiceResponses.bookingCancelled()
            )
            else -> {
                if (current.request.foodItem.isNullOrBlank()) {
                    buildSessionResult(current, FoodBookingState.NEED_FOOD_ITEM, FoodBookingVoiceResponses.askFoodItem())
                } else if (current.request.restaurantName.isNullOrBlank()) {
                    buildSessionResult(current, FoodBookingState.NEED_RESTAURANT, FoodBookingVoiceResponses.askRestaurant())
                } else if (current.platformQuotes.isNotEmpty()) {
                    buildSessionResult(
                        current,
                        FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                        FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
                    )
                } else {
                    collectAndCompareQuotes()
                }
            }
        }
    }

    private fun handleFoodItemResponse(rawText: String): FoodComparisonResult {
        val current = requireSession()
        val candidate = intentParser.extractFoodItem(rawText)
        if (candidate.isNullOrBlank() ||
            intentParser.parseProviderChoice(rawText) != null ||
            intentParser.isCheapestChoice(rawText) ||
            intentParser.isAffirmative(rawText) ||
            intentParser.isNegative(rawText)
        ) {
            current.state = FoodBookingState.NEED_FOOD_ITEM
            return buildSessionResult(current, FoodBookingState.NEED_FOOD_ITEM, FoodBookingVoiceResponses.askFoodItem())
        }

        current.request = current.request.copy(foodItem = candidate)
        return advanceSession()
    }

    private fun handleRestaurantResponse(rawText: String): FoodComparisonResult {
        val current = requireSession()
        val candidate = intentParser.extractRestaurantName(rawText)
        if (candidate.isNullOrBlank() ||
            intentParser.parseProviderChoice(rawText) != null ||
            intentParser.isCheapestChoice(rawText) ||
            intentParser.isAffirmative(rawText) ||
            intentParser.isNegative(rawText)
        ) {
            current.state = FoodBookingState.NEED_RESTAURANT
            return buildSessionResult(current, FoodBookingState.NEED_RESTAURANT, FoodBookingVoiceResponses.askRestaurant())
        }

        current.request = current.request.copy(restaurantName = candidate)
        return advanceSession()
    }

    private fun collectAndCompareQuotes(): FoodComparisonResult {
        val current = requireSession()
        FoodLogger.i("Starting food comparison for ${current.request.foodItem}")

        current.state = FoodBookingState.OPENING_PROVIDERS
        current.availableProviders.clear()
        current.platformQuotes.clear()
        current.selectedQuote = null
        current.skippedProviders.clear()

        val providersToAttempt = comparisonProvidersFor(current.request)
        val installedProviders = providersToAttempt.filter { providerRegistry.isInstalled(it) }
        val missingProviders = providersToAttempt.filterNot { providerRegistry.isInstalled(it) }

        current.availableProviders.addAll(installedProviders)
        missingProviders.forEach { provider ->
            current.skippedProviders[provider] = "app is not installed"
        }

        if (current.availableProviders.isEmpty()) {
            current.state = FoodBookingState.FAILED
            val message = FoodBookingVoiceResponses.noProvidersAvailable(current.skippedProviders)
            val result = buildSessionResult(current, FoodBookingState.FAILED, message)
            clearSession()
            return result
        }

        val collectedQuotes = mutableListOf<FoodPlatformQuote>()
        current.availableProviders.forEach { provider ->
            val launchIntent = deepLinkBuilder.buildLaunchIntent(provider, current.request)
            if (launchIntent == null) {
                current.skippedProviders[provider] = "no launch intent available"
                FoodLogger.w("${provider.displayName()} skipped because no launch intent was available")
                return@forEach
            }

            val launched = runCatching { providerLauncher.launch(launchIntent) }.getOrDefault(false)
            if (!launched) {
                current.skippedProviders[provider] = "could not launch the app"
                FoodLogger.w("${provider.displayName()} skipped because the app could not be launched")
                return@forEach
            }

            if (!accessibilityService.awaitProviderForeground(providerRegistry.packageName(provider))) {
                current.skippedProviders[provider] = "provider screen was not ready"
                FoodLogger.w("${provider.displayName()} skipped because the provider screen was not ready")
                return@forEach
            }

            accessibilityService.detectManualActionRequired()?.let { manualReason ->
                return manualAction(current, manualReason)
            }

            val target = current.request.toSearchTarget(provider)
            if (!accessibilityService.fillOrderDetails(target)) {
                current.skippedProviders[provider] = "search or cart controls were not available"
                return@forEach
            }

            accessibilityService.detectManualActionRequired()?.let { manualReason ->
                return manualAction(current, manualReason)
            }

            val couponCandidate = accessibilityService.tryApplyCoupon(target, current.request.couponPreference)

            accessibilityService.detectManualActionRequired()?.let { manualReason ->
                return manualAction(current, manualReason)
            }

            val quote = accessibilityService.collectPlatformQuote(provider, target)
            if (quote == null) {
                current.skippedProviders[provider] = "final payable was not visible"
                return@forEach
            }

            collectedQuotes.add(
                quote.copy(
                    selectedCoupon = couponCandidate ?: quote.selectedCoupon,
                    packageName = quote.packageName?.takeIf { it.isNotBlank() }
                        ?: providerRegistry.packageName(provider)
                )
            )
        }

        if (collectedQuotes.isEmpty()) {
            current.state = FoodBookingState.FAILED
            val message = FoodBookingVoiceResponses.noProvidersAvailable(current.skippedProviders)
            val result = buildSessionResult(current, FoodBookingState.FAILED, message)
            clearSession()
            return result
        }

        val sortedQuotes = priceComparator.sortLowestToHighest(collectedQuotes)
        current.platformQuotes.clear()
        current.platformQuotes.addAll(sortedQuotes)
        current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE

        return buildSessionResult(
            current,
            FoodBookingState.SHOWING_COMPARISON,
            FoodBookingVoiceResponses.showComparison(sortedQuotes, current.skippedProviders)
        )
    }

    private fun handlePlatformChoice(rawText: String): FoodComparisonResult {
        val current = requireSession()
        val selectedQuote = when {
            intentParser.isCheapestChoice(rawText) -> current.platformQuotes.firstOrNull()
            else -> {
                val provider = intentParser.parseProviderChoice(rawText) ?: current.request.preferredProvider
                provider?.let { providerChoice ->
                    current.platformQuotes.firstOrNull { it.provider == providerChoice }
                }
            }
        }

        if (selectedQuote == null) {
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        current.selectedQuote = selectedQuote
        current.request = current.request.copy(
            preferredProvider = selectedQuote.provider,
            finalUserConfirmed = false
        )
        current.state = FoodBookingState.PREPARING_ORDER

        val launchIntent = deepLinkBuilder.buildLaunchIntent(
            selectedQuote.provider,
            current.request,
            selectedQuote
        )
        if (launchIntent == null) {
            current.skippedProviders[selectedQuote.provider] = "no launch intent available"
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        val launched = runCatching { providerLauncher.launch(launchIntent) }.getOrDefault(false)
        if (!launched) {
            current.skippedProviders[selectedQuote.provider] = "could not launch the app"
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        if (!accessibilityService.awaitProviderForeground(providerRegistry.packageName(selectedQuote.provider))) {
            current.skippedProviders[selectedQuote.provider] = "provider screen was not ready"
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        accessibilityService.detectManualActionRequired()?.let { manualReason ->
            return manualAction(current, manualReason)
        }

        val target = current.request.toSearchTarget(selectedQuote.provider)
        if (!accessibilityService.fillOrderDetails(target)) {
            current.skippedProviders[selectedQuote.provider] = "search or cart controls were not available"
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        accessibilityService.detectManualActionRequired()?.let { manualReason ->
            return manualAction(current, manualReason)
        }

        val couponCandidate = accessibilityService.tryApplyCoupon(target, current.request.couponPreference)

        accessibilityService.detectManualActionRequired()?.let { manualReason ->
            return manualAction(current, manualReason)
        }

        val refreshedQuote = accessibilityService.collectPlatformQuote(selectedQuote.provider, target) ?: selectedQuote
        current.selectedQuote = refreshedQuote.copy(
            selectedCoupon = couponCandidate ?: refreshedQuote.selectedCoupon,
            packageName = refreshedQuote.packageName?.takeIf { it.isNotBlank() }
                ?: providerRegistry.packageName(selectedQuote.provider)
        )
        current.state = FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION

        return buildSessionResult(
            current,
            FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION,
            FoodBookingVoiceResponses.askFinalConfirmation(current.selectedQuote!!)
        )
    }

    private fun handleFinalConfirmation(rawText: String): FoodComparisonResult {
        val current = requireSession()
        val selectedQuote = current.selectedQuote

        if (intentParser.isNegative(rawText)) {
            current.selectedQuote = null
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        if (!intentParser.isAffirmative(rawText)) {
            val message = selectedQuote?.let { FoodBookingVoiceResponses.askFinalConfirmation(it) }
                ?: FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            return buildSessionResult(current, FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
        }

        if (selectedQuote == null) {
            current.state = FoodBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
        }

        current.request = current.request.copy(
            preferredProvider = selectedQuote.provider,
            finalUserConfirmed = true
        )
        current.state = FoodBookingState.PLACING_ORDER

        accessibilityService.detectManualActionRequired()?.let { manualReason ->
            return manualAction(current, manualReason)
        }

        if (!accessibilityService.tapFinalPlaceOrderButton(finalUserConfirmed = true)) {
            val reason = accessibilityService.detectManualActionRequired()
                ?: "I could not tap the final place order button."
            return if (reason == "I could not tap the final place order button.") {
                current.state = FoodBookingState.FAILED
                val result = buildSessionResult(
                    current,
                    FoodBookingState.FAILED,
                    FoodBookingVoiceResponses.orderFailed(reason)
                )
                clearSession()
                result
            } else {
                manualAction(current, reason)
            }
        }

        accessibilityService.detectManualActionRequired()?.let { manualReason ->
            return manualAction(current, manualReason)
        }

        current.state = FoodBookingState.COMPLETED
        val result = buildSessionResult(
            current,
            FoodBookingState.COMPLETED,
            FoodBookingVoiceResponses.orderCompleted(selectedQuote),
            finalUserConfirmed = true
        )
        clearSession()
        return result
    }

    private fun handleManualActionFollowUp(rawText: String): FoodComparisonResult {
        val current = requireSession()
        val snapshotReason = accessibilityService.detectManualActionRequired()
        if (snapshotReason != null) {
            return manualAction(current, snapshotReason)
        }

        val selectedQuote = current.selectedQuote
        return when {
            selectedQuote != null && intentParser.isAffirmative(rawText) -> {
                handleFinalConfirmation("yes")
            }
            selectedQuote != null -> buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                FoodBookingVoiceResponses.askFinalConfirmation(selectedQuote)
            )
            current.platformQuotes.isNotEmpty() -> buildSessionResult(
                current,
                FoodBookingState.WAITING_FOR_PLATFORM_CHOICE,
                FoodBookingVoiceResponses.askPlatformChoice(current.platformQuotes, current.skippedProviders)
            )
            current.request.foodItem.isNullOrBlank() -> buildSessionResult(
                current,
                FoodBookingState.NEED_FOOD_ITEM,
                FoodBookingVoiceResponses.askFoodItem()
            )
            current.request.restaurantName.isNullOrBlank() -> buildSessionResult(
                current,
                FoodBookingState.NEED_RESTAURANT,
                FoodBookingVoiceResponses.askRestaurant()
            )
            else -> collectAndCompareQuotes()
        }
    }

    private fun manualAction(current: FoodBookingSession, reason: String): FoodComparisonResult {
        current.state = FoodBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        FoodLogger.w("Food flow needs manual action: $reason")
        return buildSessionResult(
            current,
            FoodBookingState.MANUAL_ACTION_REQUIRED,
            FoodBookingVoiceResponses.manualActionRequired(reason),
            manualActionRequired = true,
            manualActionReason = reason
        )
    }

    private fun comparisonProvidersFor(request: FoodBookingRequest): List<FoodProvider> {
        val supportedProviders = providerRegistry.supportedProviders()
        val requestedProviders = request.requestedProviders

        return if (requestedProviders.isNotEmpty()) {
            supportedProviders.filter { provider -> provider in requestedProviders }
        } else {
            supportedProviders
        }
    }

    private fun buildSessionResult(
        current: FoodBookingSession,
        state: FoodBookingState,
        message: String,
        manualActionRequired: Boolean = false,
        manualActionReason: String? = null,
        finalUserConfirmed: Boolean = current.request.finalUserConfirmed
    ): FoodComparisonResult {
        return FoodComparisonResult(
            state = state,
            message = message,
            request = current.request,
            platformQuotes = current.platformQuotes.toList(),
            selectedQuote = current.selectedQuote,
            availableProviders = current.availableProviders.toList(),
            skippedProviders = current.skippedProviders.toMap(),
            manualActionRequired = manualActionRequired,
            manualActionReason = manualActionReason,
            finalUserConfirmed = finalUserConfirmed
        )
    }

    private fun requireSession(): FoodBookingSession {
        return session ?: throw IllegalStateException("No active food booking session.")
    }

    private fun clearSession() {
        session = null
    }
}
