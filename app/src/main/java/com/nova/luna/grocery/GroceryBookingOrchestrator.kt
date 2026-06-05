package com.nova.luna.grocery

class GroceryBookingOrchestrator(
    private val providerRegistry: GroceryProviderRegistry,
    private val deepLinkBuilder: GroceryDeepLinkBuilder,
    private val accessibilityService: GroceryAccessibilityService = GroceryAccessibilityService(),
    private val priceComparator: GroceryPriceComparator = GroceryPriceComparator(),
    private val intentParser: GroceryIntentParser = GroceryIntentParser(),
    private val providerLauncher: GroceryProviderLauncher
) {
    private var session: GroceryBookingSession? = null

    fun isActive(): Boolean {
        return session != null
    }

    fun currentState(): GroceryBookingState {
        return session?.state ?: GroceryBookingState.IDLE
    }

    fun start(request: GroceryBookingRequest): GroceryBookingResult {
        val activeSession = session
        if (activeSession != null) {
            return handleUserInput(request.rawText, request.finalUserConfirmed)
        }

        val parsed = intentParser.parseInitialGroceryRequest(request.rawText)
        val basket = when {
            request.basket.items.isNotEmpty() -> request.basket
            parsed != null -> parsed.basket
            else -> GroceryBasket()
        }

        session = GroceryBookingSession(
            rawText = request.rawText,
            state = GroceryBookingState.PARSING_REQUEST,
            basket = basket,
            providerPreference = request.preferredProvider ?: parsed?.providerPreference,
            brandPreference = parsed?.brandPreference,
            wantsCheapest = request.wantsCheapest || parsed?.wantsCheapest == true,
            wantsFirstOne = request.wantsFirstOne || parsed?.wantsFirstOne == true,
            compareRequested = request.compareRequested || parsed?.compareRequested == true,
            applyCouponRequested = request.applyCouponRequested || parsed?.applyCouponRequested == true,
            couponCode = request.couponCode ?: parsed?.couponCode,
            finalUserConfirmed = request.finalUserConfirmed
        )

        GroceryLogger.i(
            "start_grocery",
            mapOf(
                "rawText" to request.rawText,
                "items" to basket.displayText(),
                "preferredProvider" to session?.providerPreference?.name,
                "wantsCheapest" to session?.wantsCheapest,
                "compareRequested" to session?.compareRequested
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

        if (finalUserConfirmed && current.state == GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION) {
            return finalizeSelection(current)
        }

        intentParser.parseFollowUpCommand(rawText)?.let { followUp ->
            return handleFollowUp(current, followUp)
        }

        if (current.state == GroceryBookingState.NEED_BRAND) {
            val brandPreference = rawText.trim().takeIf { it.isNotBlank() } ?: "regular"
            current.brandPreference = brandPreference
            current.state = GroceryBookingState.CHECKING_PROVIDERS
            return when {
                current.providerPreference != null && !current.compareRequested && !current.wantsCheapest && !current.wantsFirstOne ->
                    prepareSpecificProvider(current)

                else -> compareAcrossProviders(current)
            }
        }

        if (current.state == GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE) {
            return handleProviderChoiceText(current, rawText)
        }

        if (current.state == GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION) {
            if (finalUserConfirmed) {
                return finalizeSelection(current)
            }

            return GroceryBookingResult(
                state = GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                message = current.selectedCandidate?.let { GroceryBookingVoiceResponses.askFinalConfirmation(it) }
                    ?: GroceryBookingVoiceResponses.askProviderChoice(current.comparisonResult ?: GroceryComparisonResult(emptyList()))
            )
        }

        if (current.state == GroceryBookingState.NEED_ITEMS) {
            val parsed = intentParser.parseInitialGroceryRequest(rawText)
            if (parsed?.basket?.items?.isNotEmpty() == true) {
                current.basket.addAll(parsed.basket.items)
                current.state = GroceryBookingState.NEED_BRAND
                return GroceryBookingResult(
                    state = GroceryBookingState.NEED_BRAND,
                    message = GroceryBookingVoiceResponses.askBrandPreference(),
                    request = current.toRequest()
                )
            }
        }

        return GroceryBookingResult(
            state = current.state,
            message = current.lastPrompt ?: GroceryBookingVoiceResponses.askBrandPreference(),
            request = current.toRequest()
        )
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
        GroceryLogger.i("cancel_grocery", mapOf("rawText" to current.rawText))
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

        if (current.basket.items.isEmpty()) {
            current.state = GroceryBookingState.NEED_ITEMS
            current.lastPrompt = GroceryBookingVoiceResponses.askItems()
            return GroceryBookingResult(
                state = GroceryBookingState.NEED_ITEMS,
                message = GroceryBookingVoiceResponses.askItems(),
                request = current.toRequest()
            )
        }

        if (parsed?.requiresBrandQuestion == true || current.brandPreference.isNullOrBlank()) {
            current.state = GroceryBookingState.NEED_BRAND
            current.lastPrompt = GroceryBookingVoiceResponses.askBrandPreference()
            return GroceryBookingResult(
                state = GroceryBookingState.NEED_BRAND,
                message = GroceryBookingVoiceResponses.askBrandPreference(),
                request = current.toRequest()
            )
        }

        return when {
            current.providerPreference != null && !current.compareRequested && !current.wantsCheapest && !current.wantsFirstOne ->
                prepareSpecificProvider(current)

            else -> compareAcrossProviders(current)
        }
    }

    private fun handleFollowUp(current: GroceryBookingSession, followUp: GroceryFollowUpCommand): GroceryBookingResult {
        return when (followUp.type) {
            GroceryFollowUpType.CANCEL -> cancelSession()
            GroceryFollowUpType.CONFIRM -> {
                if (current.state == GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION) {
                    finalizeSelection(current)
                } else {
                    GroceryBookingResult(
                        state = current.state,
                        message = current.lastPrompt ?: GroceryBookingVoiceResponses.askBrandPreference(),
                        request = current.toRequest()
                    )
                }
            }

            GroceryFollowUpType.ADD_ITEM -> {
                followUp.item?.let { current.basket.add(it) }
                current.state = GroceryBookingState.NEED_BRAND
                current.lastPrompt = GroceryBookingVoiceResponses.askBrandPreference()
                GroceryBookingResult(
                    state = GroceryBookingState.NEED_BRAND,
                    message = GroceryBookingVoiceResponses.askBrandPreference(),
                    request = current.toRequest()
                )
            }

            GroceryFollowUpType.REMOVE_ITEM -> {
                val removed = followUp.itemQuery?.let { current.basket.removeMatching(it) }
                val message = if (removed != null) {
                    "I removed ${removed.displayText()} from the basket."
                } else {
                    "I could not find that item in the basket."
                }
                current.state = GroceryBookingState.NEED_BRAND
                current.lastPrompt = GroceryBookingVoiceResponses.askBrandPreference()
                GroceryBookingResult(
                    state = GroceryBookingState.NEED_BRAND,
                    message = message + " " + GroceryBookingVoiceResponses.askBrandPreference(),
                    request = current.toRequest()
                )
            }

            GroceryFollowUpType.COMPARE -> compareAcrossProviders(current)
            GroceryFollowUpType.BOOK_CHEAPEST -> {
                current.wantsCheapest = true
                compareAcrossProviders(current)
            }
            GroceryFollowUpType.BOOK_PROVIDER -> {
                current.providerPreference = followUp.providerPreference ?: current.providerPreference
                if (current.providerPreference == null) {
                    compareAcrossProviders(current)
                } else {
                    prepareSpecificProvider(current)
                }
            }

            GroceryFollowUpType.APPLY_COUPON -> {
                current.applyCouponRequested = true
                current.couponCode = followUp.couponCode ?: current.couponCode
                if (current.selectedCandidate != null) {
                    prepareSpecificProvider(current)
                } else {
                    compareAcrossProviders(current)
                }
            }

            GroceryFollowUpType.BRAND_PREFERENCE -> {
                current.brandPreference = followUp.brandPreference ?: current.brandPreference
                if (current.providerPreference != null && !current.compareRequested && !current.wantsCheapest && !current.wantsFirstOne) {
                    prepareSpecificProvider(current)
                } else {
                    compareAcrossProviders(current)
                }
            }

            GroceryFollowUpType.REGULAR -> {
                current.brandPreference = followUp.brandPreference ?: "regular"
                if (current.providerPreference != null && !current.compareRequested && !current.wantsCheapest && !current.wantsFirstOne) {
                    prepareSpecificProvider(current)
                } else {
                    compareAcrossProviders(current)
                }
            }

            GroceryFollowUpType.UNKNOWN -> {
                if (current.state == GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE) {
                    handleProviderChoiceText(current, followUp.rawText)
                } else {
                    GroceryBookingResult(
                        state = current.state,
                        message = current.lastPrompt ?: GroceryBookingVoiceResponses.askBrandPreference(),
                        request = current.toRequest()
                    )
                }
            }
        }
    }

    private fun handleProviderChoiceText(current: GroceryBookingSession, rawText: String): GroceryBookingResult {
        val lower = rawText.lowercase()
        val selectedProvider = when {
            lower.contains("blinkit") -> GroceryProvider.BLINKIT
            lower.contains("jiomart") || lower.contains("jio mart") -> GroceryProvider.JIOMART
            lower.contains("instamart") || lower.contains("swiggy") -> GroceryProvider.INSTAMART
            lower.contains("cheapest") -> null
            lower.contains("first") -> null
            else -> current.providerPreference
        }

        return when {
            lower.contains("cheapest") -> {
                current.wantsCheapest = true
                current.selectedCandidate = current.comparisonResult?.cheapestCompleteCandidate
                    ?: current.comparisonResult?.recommendedCandidate
                askForFinalConfirmation(current)
            }

            selectedProvider != null -> {
                val selected = current.comparisonResult?.candidates?.firstOrNull { it.provider == selectedProvider }
                if (selected != null) {
                    current.selectedCandidate = selected
                    askForFinalConfirmation(current)
                } else {
                    GroceryBookingResult(
                        state = current.state,
                        message = GroceryBookingVoiceResponses.noProvidersAvailable(current.skippedProviders),
                        request = current.toRequest()
                    )
                }
            }

            else -> GroceryBookingResult(
                state = current.state,
                message = GroceryBookingVoiceResponses.askProviderChoice(current.comparisonResult ?: GroceryComparisonResult(emptyList())),
                request = current.toRequest()
            )
        }
    }

    private fun compareAcrossProviders(current: GroceryBookingSession): GroceryBookingResult {
        val installedProviders = providerRegistry.installedProviders()
        current.availableProviders = installedProviders
        current.state = GroceryBookingState.CHECKING_PROVIDERS

        if (installedProviders.isEmpty()) {
            current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
            current.manualActionReason = "no installed grocery apps"
            val message = GroceryBookingVoiceResponses.noProvidersAvailable(providerRegistry.discoverProviders().skippedProviders)
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
                message = message,
                request = current.toRequest(),
                manualActionRequired = true,
                manualActionReason = current.manualActionReason
            )
        }

        val providersToCheck = when {
            current.providerPreference != null && !current.compareRequested && !current.wantsCheapest && !current.wantsFirstOne ->
                listOf(current.providerPreference!!)

            else -> installedProviders
        }

        val candidates = mutableListOf<GroceryCartCandidate>()
        val providerFailures = linkedMapOf<GroceryProvider, String>()
        val skippedProviders = linkedMapOf<GroceryProvider, String>()

        providersToCheck.forEach { provider ->
            val launchPlan = deepLinkBuilder.buildLaunchPlan(provider, current.toRequest(), current.selectedCandidate)
            launchPlan.intent?.let { providerLauncher.launch(it) }

            val packageName = providerRegistry.installedPackageName(provider)
            if (!packageName.isNullOrBlank()) {
                accessibilityService.waitForForegroundPackage(setOf(packageName))
            }

            val candidate = accessibilityService.collectCartCandidate(
                provider = provider,
                basket = current.basket,
                couponCode = current.couponCode
            )

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
                manualActionRequired = true,
                manualActionReason = reason,
                providerFailures = providerFailures,
                skippedProviders = skippedProviders
            )
        }

        val comparison = priceComparator.compare(candidates)
        current.comparisonResult = comparison
        current.selectedCandidate = when {
            current.providerPreference != null -> comparison.candidates.firstOrNull { it.provider == current.providerPreference }
            current.wantsCheapest -> comparison.cheapestCompleteCandidate ?: comparison.recommendedCandidate
            current.wantsFirstOne -> comparison.candidates.firstOrNull()
            else -> null
        }

        return if (current.selectedCandidate != null && (current.providerPreference != null || current.wantsCheapest || current.wantsFirstOne)) {
            askForFinalConfirmation(current)
        } else {
            current.state = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE
            val message = GroceryBookingVoiceResponses.showComparison(comparison)
            current.lastPrompt = message
            GroceryBookingResult(
                state = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE,
                message = message,
                request = current.toRequest(),
                comparisonResult = comparison,
                availableProviders = installedProviders,
                skippedProviders = skippedProviders,
                providerFailures = providerFailures,
                currentState = GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE
            )
        }
    }

    private fun prepareSpecificProvider(current: GroceryBookingSession): GroceryBookingResult {
        val candidateProvider = current.providerPreference
        if (candidateProvider == null) {
            return compareAcrossProviders(current)
        }

        val comparison = current.comparisonResult
        val candidate = comparison?.candidates?.firstOrNull { it.provider == candidateProvider }
            ?: collectCandidateForSingleProvider(current, candidateProvider)

        if (candidate.manualActionReason != null) {
            current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
            current.manualActionReason = candidate.manualActionReason.displayText
            val message = GroceryBookingVoiceResponses.manualActionRequired(candidateProvider, candidate.manualActionReason.displayText)
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
                message = message,
                request = current.toRequest(),
                manualActionRequired = true,
                manualActionReason = candidate.manualActionReason.displayText
            )
        }

        current.selectedCandidate = candidate
        return askForFinalConfirmation(current)
    }

    private fun collectCandidateForSingleProvider(
        current: GroceryBookingSession,
        provider: GroceryProvider
    ): GroceryCartCandidate {
        val launchPlan = deepLinkBuilder.buildLaunchPlan(provider, current.toRequest(), current.selectedCandidate)
        launchPlan.intent?.let { providerLauncher.launch(it) }

        val packageName = providerRegistry.installedPackageName(provider)
        if (!packageName.isNullOrBlank()) {
            accessibilityService.waitForForegroundPackage(setOf(packageName))
        }

        return accessibilityService.collectCartCandidate(
            provider = provider,
            basket = current.basket,
            couponCode = current.couponCode
        )
    }

    private fun askForFinalConfirmation(current: GroceryBookingSession): GroceryBookingResult {
        val selected = current.selectedCandidate
        if (selected == null) {
            return GroceryBookingResult(
                state = current.state,
                message = GroceryBookingVoiceResponses.askProviderChoice(current.comparisonResult ?: GroceryComparisonResult(emptyList())),
                request = current.toRequest()
            )
        }

        current.finalConfirmationAsked = true
        current.state = GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION
        val message = GroceryBookingVoiceResponses.askFinalConfirmation(selected)
        current.lastPrompt = message
        return GroceryBookingResult(
            state = GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION,
            message = message,
            request = current.toRequest(),
            comparisonResult = current.comparisonResult,
            selectedCandidate = selected,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            finalConfirmationAsked = true,
            currentState = GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION
        )
    }

    private fun finalizeSelection(current: GroceryBookingSession): GroceryBookingResult {
        val selected = current.selectedCandidate
            ?: return GroceryBookingResult(
                state = GroceryBookingState.FAILED,
                message = GroceryBookingVoiceResponses.bookingFailed("no provider was selected."),
                request = current.toRequest()
            )

        current.state = GroceryBookingState.BOOKING
        current.finalUserConfirmed = true
        GroceryLogger.i(
            "finalize_grocery",
            mapOf(
                "provider" to selected.provider.name,
                "finalPayable" to selected.summary.finalPayableValue,
                "rawText" to current.rawText
            )
        )

        val tapped = accessibilityService.tapFinalOrderButton()
        if (!tapped) {
            current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
            val message = GroceryBookingVoiceResponses.manualActionRequired(selected.provider, "final order")
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
                message = message,
                request = current.toRequest(),
                selectedCandidate = selected,
                manualActionRequired = true,
                manualActionReason = "final order"
            )
        }

        val snapshot = accessibilityService.captureScreenSnapshot()
        val manualReason = snapshot?.manualActionReason ?: accessibilityService.detectManualActionReason(snapshot?.visibleText.orEmpty())
        if (manualReason != null) {
            current.state = GroceryBookingState.MANUAL_ACTION_REQUIRED
            val message = GroceryBookingVoiceResponses.manualActionRequired(selected.provider, GroceryBookingVoiceResponses.describeManualActionReason(manualReason))
            current.lastPrompt = message
            return GroceryBookingResult(
                state = GroceryBookingState.MANUAL_ACTION_REQUIRED,
                message = message,
                request = current.toRequest(),
                selectedCandidate = selected,
                manualActionRequired = true,
                manualActionReason = GroceryBookingVoiceResponses.describeManualActionReason(manualReason)
            )
        }

        current.state = GroceryBookingState.COMPLETED
        val message = "${selected.provider.displayName()} cart is ready. I stopped before payment."
        val result = GroceryBookingResult(
            state = GroceryBookingState.COMPLETED,
            message = message,
            request = current.toRequest(),
            selectedCandidate = selected,
            comparisonResult = current.comparisonResult,
            availableProviders = current.availableProviders,
            skippedProviders = current.skippedProviders,
            providerFailures = current.providerFailures,
            finalConfirmationAsked = true,
            finalUserConfirmed = true,
            currentState = GroceryBookingState.COMPLETED
        )
        clearSession()
        return result
    }

    private fun requireSession(): GroceryBookingSession {
        return session ?: error("Grocery session is not active.")
    }

    private fun clearSession() {
        session = null
    }
}
