package com.nova.luna.cab

import android.content.Context
import android.location.LocationManager

fun interface CabProviderLauncher {
    fun launch(intent: android.content.Intent): Boolean
}

fun interface CabPickupLocationResolver {
    fun resolvePickupLocation(): CabPickupLocation?
}

class CabBookingOrchestrator(
    private val providerRegistry: CabProviderRegistry,
    private val deepLinkBuilder: CabDeepLinkBuilder,
    private val accessibilityService: CabAccessibilityService = CabAccessibilityService(),
    private val fareComparator: CabFareComparator = CabFareComparator(),
    private val intentParser: CabIntentParser = CabIntentParser(),
    private val pickupLocationResolver: CabPickupLocationResolver? = null,
    private val providerLauncher: CabProviderLauncher
) {
    private data class CabBookingSession(
        var request: CabBookingRequest,
        var state: CabBookingState = CabBookingState.IDLE,
        val availableProviders: MutableList<CabProvider> = mutableListOf(),
        val skippedProviders: MutableMap<CabProvider, String> = linkedMapOf(),
        val providerFailures: MutableMap<CabProvider, String> = linkedMapOf(),
        val fareOptions: MutableList<CabFareOption> = mutableListOf(),
        var selectedOption: CabFareOption? = null,
        var currentProvider: CabProvider? = null,
        var manualActionReason: String? = null
    )

    private var session: CabBookingSession? = null

    fun isActive(): Boolean {
        return session != null
    }

    fun currentState(): CabBookingState {
        return session?.state ?: CabBookingState.IDLE
    }

    fun start(request: CabBookingRequest): CabBookingResult {
        session = CabBookingSession(
            request = request.copy(finalUserConfirmed = false),
            state = CabBookingState.PARSING_REQUEST
        )

        CabLogger.i(
            "start_booking",
            mapOf(
                "rawText" to request.rawText,
                "pickup" to request.pickupLocation,
                "drop" to request.dropLocation,
                "rideType" to request.rideType?.name,
                "providerPreference" to request.preferredProvider?.name,
                "wantsCheapest" to request.wantsCheapest
            )
        )

        return advanceFromCurrentRequest()
    }

    fun cancelSession(reason: String = "user cancelled") : CabBookingResult {
        val current = session ?: return CabBookingResult(
            state = CabBookingState.CANCELLED,
            message = "There is no active cab booking session to cancel."
        )

        current.state = CabBookingState.CANCELLED
        current.manualActionReason = null

        CabLogger.i(
            "cancel_session",
            mapOf(
                "reason" to reason,
                "currentState" to current.state.name
            )
        )

        val result = buildSessionResult(
            current = current,
            state = CabBookingState.CANCELLED,
            message = CabBookingVoiceResponses.bookingCancelled(),
            finalUserConfirmed = false
        )
        clearSession()
        return result
    }

    fun handleUserInput(rawText: String): CabBookingResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return promptCurrentState()
        }

        val parsedRequest = intentParser.parse(trimmed)
        val current = session
        if (current == null && parsedRequest != null) {
            CabLogger.d(
                "parsed_new_request",
                mapOf(
                    "rawText" to trimmed,
                    "pickupText" to parsedRequest.pickupText,
                    "dropText" to parsedRequest.dropText,
                    "rideType" to parsedRequest.rideType?.name,
                    "providerPreference" to parsedRequest.providerPreference?.name,
                    "wantsCheapest" to parsedRequest.wantsCheapest
                )
            )
            return start(parsedRequest.toBookingRequest())
        }

        val currentSession = current ?: return CabBookingResult(
            state = CabBookingState.CANCELLED,
            message = "There is no active cab booking session."
        )

        CabLogger.d(
            "handle_input",
            mapOf(
                "state" to currentSession.state.name,
                "rawText" to trimmed,
                "selectedProvider" to currentSession.selectedOption?.provider?.name,
                "fareOptions" to currentSession.fareOptions.joinToString(separator = ",") { it.provider.name }
            )
        )

        return when (currentSession.state) {
            CabBookingState.NEED_PICKUP -> handlePickupResponse(trimmed)
            CabBookingState.NEED_DROP -> handleDropResponse(trimmed)
            CabBookingState.NEED_RIDE_TYPE -> handleRideTypeResponse(trimmed)
            CabBookingState.MANUAL_ACTION_REQUIRED -> handleManualActionFollowUp(trimmed)
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION -> handleFinalConfirmation(trimmed)
            CabBookingState.SHOWING_COMPARISON,
            CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
            CabBookingState.CHECKING_PROVIDERS,
            CabBookingState.OPENING_PROVIDER,
            CabBookingState.FILLING_TRIP,
            CabBookingState.COLLECTING_FARES -> handlePlatformChoice(trimmed)
            CabBookingState.COMPLETED,
            CabBookingState.CANCELLED,
            CabBookingState.FAILED,
            CabBookingState.IDLE,
            CabBookingState.BOOKING,
            CabBookingState.PARSING_REQUEST -> promptCurrentState()
        }
    }

    private fun advanceFromCurrentRequest(): CabBookingResult {
        val current = requireSession()
        current.state = CabBookingState.PARSING_REQUEST
        resolvePickupIfPossible(current)

        if (current.request.pickupLocation.isNullOrBlank()) {
            current.state = CabBookingState.NEED_PICKUP
            CabLogger.d("missing_field", mapOf("field" to "pickup"))
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        if (current.request.dropLocation.isNullOrBlank()) {
            current.state = CabBookingState.NEED_DROP
            CabLogger.d("missing_field", mapOf("field" to "drop"))
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        if (current.request.rideType == null) {
            current.state = CabBookingState.NEED_RIDE_TYPE
            CabLogger.d("missing_field", mapOf("field" to "rideType"))
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        return collectAndCompareFares()
    }

    private fun promptCurrentState(): CabBookingResult {
        val current = session ?: return CabBookingResult(
            state = CabBookingState.CANCELLED,
            message = "There is no active cab booking session."
        )

        return when (current.state) {
            CabBookingState.NEED_PICKUP -> buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
            CabBookingState.NEED_DROP -> buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
            CabBookingState.NEED_RIDE_TYPE -> buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
            CabBookingState.SHOWING_COMPARISON,
            CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
            CabBookingState.CHECKING_PROVIDERS,
            CabBookingState.OPENING_PROVIDER,
            CabBookingState.FILLING_TRIP,
            CabBookingState.COLLECTING_FARES -> buildSessionResult(
                current.also { it.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE },
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                )
            )
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION -> {
                val selected = current.selectedOption
                val message = if (selected != null) {
                    CabBookingVoiceResponses.askFinalConfirmation(selected)
                } else {
                    CabBookingVoiceResponses.askPlatformChoice(
                        current.fareOptions,
                        current.skippedProviders,
                        current.providerFailures
                    )
                }
                current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
                buildSessionResult(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
            }
            CabBookingState.MANUAL_ACTION_REQUIRED -> buildSessionResult(
                current,
                CabBookingState.MANUAL_ACTION_REQUIRED,
                CabBookingVoiceResponses.manualActionRequired(
                    current.currentProvider ?: current.selectedOption?.provider,
                    current.manualActionReason ?: "manual action required"
                ),
                manualActionRequired = true,
                manualActionReason = current.manualActionReason
            )
            CabBookingState.COMPLETED -> buildSessionResult(
                current,
                CabBookingState.COMPLETED,
                current.selectedOption?.let { CabBookingVoiceResponses.bookingCompleted(it) }
                    ?: "Cab booking completed."
            )
            CabBookingState.CANCELLED -> buildSessionResult(
                current,
                CabBookingState.CANCELLED,
                CabBookingVoiceResponses.bookingCancelled()
            )
            CabBookingState.FAILED -> buildSessionResult(
                current,
                CabBookingState.FAILED,
                CabBookingVoiceResponses.bookingFailed("no active cab booking session.")
            )
            else -> buildSessionResult(current, current.state, CabBookingVoiceResponses.askPickup())
        }
    }

    private fun resolvePickupIfPossible(current: CabBookingSession) {
        val pickup = current.request.pickupLocation
        val wantsCurrentLocation = pickup.isNullOrBlank() || pickup.equals("current location", ignoreCase = true)
        if (!wantsCurrentLocation) return

        val resolved = pickupLocationResolver?.resolvePickupLocation()
        if (resolved != null) {
            current.request = current.request.withPickupLocation(resolved)
            CabLogger.d(
                "pickup_resolved",
                mapOf(
                    "label" to resolved.label,
                    "latitude" to resolved.latitude,
                    "longitude" to resolved.longitude
                )
            )
        } else if (pickup.equals("current location", ignoreCase = true)) {
            current.request = current.request.copy(
                pickupLocation = null,
                pickupLatitude = null,
                pickupLongitude = null
            )
        }
    }

    private fun handlePickupResponse(rawText: String): CabBookingResult {
        val current = requireSession()

        if (intentParser.isCancelCommand(rawText)) {
            return cancelSession("user cancelled during pickup request")
        }

        if (intentParser.isCurrentLocationRequest(rawText)) {
            val resolved = pickupLocationResolver?.resolvePickupLocation()
            if (resolved != null) {
                current.request = current.request.withPickupLocation(resolved)
                CabLogger.d("pickup_reply_current_location", mapOf("label" to resolved.label))
                return advanceFromCurrentRequest()
            }
            current.state = CabBookingState.NEED_PICKUP
            CabLogger.d("pickup_reply_missing_location", emptyMap())
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        val candidate = intentParser.extractPickupLocation(rawText)
        if (candidate.isNullOrBlank() ||
            intentParser.extractRideType(rawText) != null ||
            intentParser.parseProviderChoice(rawText) != null ||
            intentParser.isCheapestChoice(rawText) ||
            intentParser.isFirstChoice(rawText) ||
            intentParser.isAffirmative(rawText) ||
            intentParser.isNegative(rawText)
        ) {
            current.state = CabBookingState.NEED_PICKUP
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        current.request = current.request.copy(
            pickupLocation = candidate,
            pickupLatitude = null,
            pickupLongitude = null
        )
        CabLogger.d("pickup_set", mapOf("pickup" to candidate))
        return advanceFromCurrentRequest()
    }

    private fun handleDropResponse(rawText: String): CabBookingResult {
        val current = requireSession()

        if (intentParser.isCancelCommand(rawText)) {
            return cancelSession("user cancelled during drop request")
        }

        val candidate = intentParser.extractDropLocation(rawText)
        if (candidate.isNullOrBlank() ||
            intentParser.extractRideType(rawText) != null ||
            intentParser.parseProviderChoice(rawText) != null ||
            intentParser.isCheapestChoice(rawText) ||
            intentParser.isFirstChoice(rawText) ||
            intentParser.isAffirmative(rawText) ||
            intentParser.isNegative(rawText)
        ) {
            current.state = CabBookingState.NEED_DROP
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        current.request = current.request.copy(dropLocation = candidate)
        CabLogger.d("drop_set", mapOf("drop" to candidate))
        return advanceFromCurrentRequest()
    }

    private fun handleRideTypeResponse(rawText: String): CabBookingResult {
        val current = requireSession()

        if (intentParser.isCancelCommand(rawText)) {
            return cancelSession("user cancelled during ride type request")
        }

        val rideType = intentParser.extractRideType(rawText)
        val wantsCheapest = intentParser.isCheapestChoice(rawText)
        if (rideType == null && !wantsCheapest) {
            current.state = CabBookingState.NEED_RIDE_TYPE
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        current.request = current.request.copy(
            rideType = rideType ?: RideType.ANY,
            wantsCheapest = current.request.wantsCheapest || wantsCheapest
        )
        CabLogger.d(
            "ride_type_set",
            mapOf(
                "rideType" to current.request.rideType?.name,
                "wantsCheapest" to current.request.wantsCheapest
            )
        )
        return collectAndCompareFares()
    }

    private fun collectAndCompareFares(): CabBookingResult {
        val current = requireSession()
        current.state = CabBookingState.CHECKING_PROVIDERS
        current.availableProviders.clear()
        current.availableProviders.addAll(providerRegistry.installedProviders())
        current.skippedProviders.clear()
        current.skippedProviders.putAll(providerRegistry.missingProviders().associateWith { "app is not installed" })
        current.providerFailures.clear()
        current.fareOptions.clear()

        CabLogger.d(
            "provider_list",
            mapOf(
                "installedProviders" to current.availableProviders.joinToString(separator = ",") { it.name },
                "skippedProviders" to current.skippedProviders.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
            )
        )

        if (current.availableProviders.isEmpty()) {
            current.state = CabBookingState.FAILED
            val message = CabBookingVoiceResponses.noProvidersAvailable(current.skippedProviders)
            val result = buildSessionResult(current, CabBookingState.FAILED, message)
            clearSession()
            return result
        }

        val collectedOptions = mutableListOf<CabFareOption>()

        current.availableProviders.forEach { provider ->
            current.currentProvider = provider
            current.state = CabBookingState.OPENING_PROVIDER
            CabLogger.d(
                "opening_provider",
                mapOf(
                    "provider" to provider.name,
                    "packageName" to providerRegistry.installedPackageName(provider)
                )
            )

            val launchIntent = deepLinkBuilder.buildLaunchIntent(provider, current.request)
            if (launchIntent == null) {
                current.providerFailures[provider] = "no launch intent available"
                CabLogger.w("provider_launch_missing", mapOf("provider" to provider.name))
                return@forEach
            }

            val launched = runCatching { providerLauncher.launch(launchIntent) }.getOrDefault(false)
            if (!launched) {
                current.providerFailures[provider] = "could not launch the app"
                CabLogger.w("provider_launch_failed", mapOf("provider" to provider.name))
                return@forEach
            }

            val preFillManualReason = accessibilityService.detectManualActionRequired()
            if (preFillManualReason != null) {
                return manualAction(current, preFillManualReason)
            }

            current.state = CabBookingState.FILLING_TRIP
            val fillSuccess = accessibilityService.fillTripDetails(current.request)
            if (!fillSuccess) {
                current.providerFailures[provider] = "could not fill trip details"
            }

            val postFillManualReason = accessibilityService.detectManualActionRequired()
            if (postFillManualReason != null) {
                return manualAction(current, postFillManualReason)
            }

            current.state = CabBookingState.COLLECTING_FARES
            val snapshot = accessibilityService.captureScreenSnapshot()
            CabLogger.d(
                "screen_text_captured",
                mapOf(
                    "provider" to provider.name,
                    "sourceText" to snapshot?.sourceText,
                    "visibleFareText" to snapshot?.visibleFareText,
                    "finalFareText" to snapshot?.finalFareText,
                    "etaText" to snapshot?.etaText,
                    "manualActionReason" to snapshot?.manualActionReason
                )
            )

            val option = accessibilityService.collectFareOption(provider, current.request, snapshot)
            if (option == null) {
                current.providerFailures[provider] = "fare was not visible before timeout"
                return@forEach
            }

            val withPackageName = option.copy(
                packageName = providerRegistry.installedPackageName(provider) ?: providerRegistry.packageName(provider)
            )
            collectedOptions.add(withPackageName)
        }

        if (collectedOptions.isEmpty()) {
            current.state = CabBookingState.FAILED
            val failureSummary = CabBookingVoiceResponses.providerFailureSummary(current.providerFailures)
            val message = if (failureSummary.isNotBlank()) {
                CabBookingVoiceResponses.bookingFailed(failureSummary)
            } else {
                CabBookingVoiceResponses.bookingFailed("fare was not visible on any provider.")
            }
            val result = buildSessionResult(current, CabBookingState.FAILED, message)
            clearSession()
            return result
        }

        val sortedOptions = fareComparator.sortLowestToHighest(collectedOptions)
        current.fareOptions.clear()
        current.fareOptions.addAll(sortedOptions)
        current.state = CabBookingState.SHOWING_COMPARISON
        val message = CabBookingVoiceResponses.showComparison(
            sortedOptions,
            current.skippedProviders,
            current.providerFailures
        )
        CabLogger.d(
            "fare_comparison_ready",
            mapOf(
                "options" to sortedOptions.joinToString(separator = ",") {
                    "${it.provider.name}:${it.finalFareAmount ?: it.visibleFareAmount ?: "unavailable"}"
                }
            )
        )
        return buildSessionResult(current, CabBookingState.SHOWING_COMPARISON, message)
    }

    private fun handlePlatformChoice(rawText: String): CabBookingResult {
        val current = requireSession()

        if (intentParser.isCancelCommand(rawText)) {
            return cancelSession("user cancelled while choosing a provider")
        }

        if (intentParser.isChangeRideRequest(rawText)) {
            current.selectedOption = null
            current.request = current.request.copy(rideType = null)
            current.state = CabBookingState.NEED_RIDE_TYPE
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        if (intentParser.isChangeDropRequest(rawText)) {
            current.selectedOption = null
            current.request = current.request.copy(dropLocation = null)
            current.state = CabBookingState.NEED_DROP
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        if (intentParser.isChangePickupRequest(rawText)) {
            current.selectedOption = null
            current.request = current.request.copy(
                pickupLocation = null,
                pickupLatitude = null,
                pickupLongitude = null
            )
            current.state = CabBookingState.NEED_PICKUP
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        val selectedOption = when {
            intentParser.isCheapestChoice(rawText) -> current.fareOptions.firstOrNull()
            intentParser.isFirstChoice(rawText) -> current.fareOptions.firstOrNull()
            else -> {
                val provider = intentParser.parseProviderChoice(rawText)
                    ?: current.request.preferredProvider
                provider?.let { providerChoice ->
                    current.fareOptions.firstOrNull { it.provider == providerChoice }
                }
            }
        }

        if (selectedOption == null) {
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            val message = CabBookingVoiceResponses.askPlatformChoice(
                current.fareOptions,
                current.skippedProviders,
                current.providerFailures
            )
            CabLogger.d(
                "platform_choice_pending",
                mapOf(
                    "rawText" to rawText,
                    "fareOptions" to current.fareOptions.joinToString(separator = ",") { it.provider.name }
                )
            )
            return buildSessionResult(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, message)
        }

        current.selectedOption = selectedOption
        current.request = current.request.copy(
            preferredProvider = selectedOption.provider,
            finalUserConfirmed = false
        )
        CabLogger.d(
            "selected_fare",
            mapOf(
                "provider" to selectedOption.provider.name,
                "rideType" to selectedOption.rideType.name,
                "finalFareAmount" to selectedOption.finalFareAmount,
                "visibleFareAmount" to selectedOption.visibleFareAmount,
                "etaText" to selectedOption.etaText
            )
        )

        val launchIntent = deepLinkBuilder.buildLaunchIntent(
            selectedOption.provider,
            current.request,
            selectedOption
        )
        if (launchIntent == null) {
            current.providerFailures[selectedOption.provider] = "no launch intent available"
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                )
            )
        }

        val launched = runCatching { providerLauncher.launch(launchIntent) }.getOrDefault(false)
        if (!launched) {
            current.providerFailures[selectedOption.provider] = "could not launch the app"
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                )
            )
        }

        val preFillManualReason = accessibilityService.detectManualActionRequired()
        if (preFillManualReason != null) {
            return manualAction(current, preFillManualReason)
        }

        current.state = CabBookingState.FILLING_TRIP
        val fillSuccess = accessibilityService.fillTripDetails(current.request)
        if (!fillSuccess) {
            current.providerFailures[selectedOption.provider] = "could not fill trip details"
        }

        val postFillManualReason = accessibilityService.detectManualActionRequired()
        if (postFillManualReason != null) {
            return manualAction(current, postFillManualReason)
        }

        current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
        val message = CabBookingVoiceResponses.askFinalConfirmation(selectedOption)
        CabLogger.d(
            "awaiting_final_confirmation",
            mapOf(
                "provider" to selectedOption.provider.name,
                "finalFareAmount" to selectedOption.finalFareAmount,
                "confirmed" to false
            )
        )
        return buildSessionResult(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
    }

    private fun handleFinalConfirmation(rawText: String): CabBookingResult {
        val current = requireSession()
        val selectedOption = current.selectedOption

        if (intentParser.isChangeRideRequest(rawText)) {
            current.selectedOption = null
            current.request = current.request.copy(rideType = null, finalUserConfirmed = false)
            current.state = CabBookingState.NEED_RIDE_TYPE
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        if (intentParser.isChangeDropRequest(rawText)) {
            current.selectedOption = null
            current.request = current.request.copy(dropLocation = null, finalUserConfirmed = false)
            current.state = CabBookingState.NEED_DROP
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        if (intentParser.isChangePickupRequest(rawText)) {
            current.selectedOption = null
            current.request = current.request.copy(
                pickupLocation = null,
                pickupLatitude = null,
                pickupLongitude = null,
                finalUserConfirmed = false
            )
            current.state = CabBookingState.NEED_PICKUP
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        if (intentParser.isPauseCommand(rawText)) {
            current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                selectedOption?.let { CabBookingVoiceResponses.askFinalConfirmation(it) }
                    ?: CabBookingVoiceResponses.askPlatformChoice(
                        current.fareOptions,
                        current.skippedProviders,
                        current.providerFailures
                    )
            )
        }

        if (intentParser.isNegative(rawText)) {
            return cancelSession("user cancelled before final booking")
        }

        if (!intentParser.isAffirmative(rawText)) {
            val message = selectedOption?.let { CabBookingVoiceResponses.askFinalConfirmation(it) }
                ?: CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                )
            current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
            return buildSessionResult(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
        }

        if (selectedOption == null) {
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                )
            )
        }

        current.request = current.request.copy(finalUserConfirmed = true)
        current.state = CabBookingState.BOOKING
        CabLogger.d(
            "final_confirmation_status",
            mapOf(
                "provider" to selectedOption.provider.name,
                "confirmed" to true
            )
        )

        val manualReasonBeforeTap = accessibilityService.detectManualActionRequired()
        if (manualReasonBeforeTap != null) {
            return manualAction(current, manualReasonBeforeTap)
        }

        if (!accessibilityService.tapFinalConfirmButton(finalUserConfirmed = true)) {
            val reason = accessibilityService.detectManualActionRequired() ?: "I could not tap the final confirm button."
            return if (reason == "I could not tap the final confirm button.") {
                current.state = CabBookingState.FAILED
                val result = buildSessionResult(
                    current,
                    CabBookingState.FAILED,
                    CabBookingVoiceResponses.bookingFailed(reason)
                )
                clearSession()
                result
            } else {
                manualAction(current, reason)
            }
        }

        val postTapManualReason = accessibilityService.detectManualActionRequired()
        if (postTapManualReason != null) {
            return manualAction(current, postTapManualReason)
        }

        current.state = CabBookingState.COMPLETED
        val result = buildSessionResult(
            current,
            CabBookingState.COMPLETED,
            CabBookingVoiceResponses.bookingCompleted(selectedOption),
            finalUserConfirmed = true
        )
        clearSession()
        return result
    }

    private fun handleManualActionFollowUp(rawText: String): CabBookingResult {
        val current = requireSession()
        val snapshotReason = accessibilityService.detectManualActionRequired()
        if (snapshotReason != null) {
            return manualAction(current, snapshotReason)
        }

        if (intentParser.isCancelCommand(rawText)) {
            return cancelSession("user cancelled after manual step")
        }

        if (current.selectedOption != null) {
            current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                CabBookingVoiceResponses.askFinalConfirmation(current.selectedOption!!)
            )
        }

        if (current.fareOptions.isNotEmpty()) {
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                )
            )
        }

        if (current.request.pickupLocation.isNullOrBlank()) {
            current.state = CabBookingState.NEED_PICKUP
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        if (current.request.dropLocation.isNullOrBlank()) {
            current.state = CabBookingState.NEED_DROP
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        if (current.request.rideType == null) {
            current.state = CabBookingState.NEED_RIDE_TYPE
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        return collectAndCompareFares()
    }

    private fun manualAction(current: CabBookingSession, reason: String): CabBookingResult {
        current.state = CabBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        CabLogger.w(
            "manual_action_required",
            mapOf(
                "provider" to current.currentProvider?.name,
                "selectedProvider" to current.selectedOption?.provider?.name,
                "reason" to reason
            )
        )
        return buildSessionResult(
            current = current,
            state = CabBookingState.MANUAL_ACTION_REQUIRED,
            message = CabBookingVoiceResponses.manualActionRequired(
                current.currentProvider ?: current.selectedOption?.provider,
                reason
            ),
            manualActionRequired = true,
            manualActionReason = reason
        )
    }

    private fun buildSessionResult(
        current: CabBookingSession,
        state: CabBookingState,
        message: String,
        manualActionRequired: Boolean = false,
        manualActionReason: String? = null,
        finalUserConfirmed: Boolean = current.request.finalUserConfirmed
    ): CabBookingResult {
        return CabBookingResult(
            state = state,
            message = message,
            request = current.request,
            fareOptions = current.fareOptions.toList(),
            selectedOption = current.selectedOption,
            selectedFareOption = current.selectedOption,
            selectedProvider = current.selectedOption?.provider,
            availableProviders = current.availableProviders.toList(),
            skippedProviders = current.skippedProviders.toMap(),
            providerFailures = current.providerFailures.toMap(),
            manualActionRequired = manualActionRequired,
            manualActionReason = manualActionReason,
            finalUserConfirmed = finalUserConfirmed,
            currentState = current.state
        )
    }

    private fun requireSession(): CabBookingSession {
        return session ?: throw IllegalStateException("No active cab booking session.")
    }

    private fun clearSession() {
        session = null
    }
}

class AndroidCabPickupLocationResolver(
    private val context: Context
) : CabPickupLocationResolver {
    override fun resolvePickupLocation(): CabPickupLocation? {
        if (!hasLocationPermission()) return null

        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        val location = providers.asSequence()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .firstOrNull()
            ?: return null

        return CabPickupLocation(
            label = "Current location",
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private fun hasLocationPermission(): Boolean {
        return runCatching {
            val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            fineGranted || coarseGranted
        }.getOrDefault(false)
    }
}
