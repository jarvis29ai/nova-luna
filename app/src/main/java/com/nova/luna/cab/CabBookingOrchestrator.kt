package com.nova.luna.cab

class CabBookingOrchestrator(
    private val providerRegistry: CabProviderRegistry,
    private val deepLinkBuilder: CabDeepLinkBuilder,
    private val accessibilityService: CabAccessibilityService = CabAccessibilityService(),
    private val fareComparator: CabFareComparator = CabFareComparator(),
    private val intentParser: CabIntentParser = CabIntentParser(),
    private val locationResolver: CabLocationResolver? = null,
    private val providerLauncher: CabProviderLauncher
) {
    constructor(
        providerRegistry: CabProviderRegistry,
        deepLinkBuilder: CabDeepLinkBuilder,
        accessibilityService: CabAccessibilityService = CabAccessibilityService(),
        fareComparator: CabFareComparator = CabFareComparator(),
        intentParser: CabIntentParser = CabIntentParser(),
        pickupLocationResolver: CabPickupLocationResolver? = null,
        providerLauncher: CabProviderLauncher
    ) : this(
        providerRegistry = providerRegistry,
        deepLinkBuilder = deepLinkBuilder,
        accessibilityService = accessibilityService,
        fareComparator = fareComparator,
        intentParser = intentParser,
        locationResolver = pickupLocationResolver?.let { pickupResolver ->
            object : CabLocationResolver {
                override fun hasLocationPermission(): Boolean = true

                override fun getCurrentLocationDisplay(): String? {
                    return pickupResolver.resolvePickupLocation()?.label
                }

                override fun getCurrentLatLng(): Pair<Double, Double>? {
                    val pickup = pickupResolver.resolvePickupLocation() ?: return null
                    val lat = pickup.latitude ?: return null
                    val lon = pickup.longitude ?: return null
                    return lat to lon
                }
            }
        },
        providerLauncher = providerLauncher
    )

    private var session: CabBookingSession? = null

    fun isActive(): Boolean {
        return session != null
    }

    fun currentState(): CabBookingState {
        return session?.state ?: CabBookingState.IDLE
    }

    fun start(request: CabBookingRequest): CabBookingResult {
        val pickupMode = when {
            request.pickupMode == PickupMode.CURRENT_LOCATION -> PickupMode.CURRENT_LOCATION
            request.pickupLocation?.equals("current location", ignoreCase = true) == true -> PickupMode.CURRENT_LOCATION
            request.pickupLocation.isNullOrBlank() -> PickupMode.UNKNOWN
            else -> PickupMode.USER_TEXT
        }

        session = CabBookingSession(
            rawText = request.rawText,
            state = CabBookingState.PARSING_REQUEST,
            pickup = when {
                pickupMode == PickupMode.CURRENT_LOCATION -> resolveCurrentLocationValue()
                request.pickupLocation.isNullOrBlank() -> null
                else -> LocationValue(
                    rawText = request.pickupLocation,
                    isCurrentLocation = false,
                    latitude = request.pickupLatitude,
                    longitude = request.pickupLongitude,
                    displayName = request.pickupLocation
                )
            },
            pickupMode = pickupMode,
            drop = request.dropLocation?.takeIf { it.isNotBlank() }?.let {
                LocationValue(rawText = it, displayName = it)
            },
            rideType = request.rideType,
            providerPreference = request.preferredProvider,
            wantsCheapest = request.wantsCheapest,
            wantsFirstOne = request.wantsFirstOne
        )

        CabLogger.i(
            "start_booking",
            mapOf(
                "rawText" to request.rawText,
                "pickup" to request.pickupLocation,
                "pickupMode" to request.pickupMode.name,
                "drop" to request.dropLocation,
                "rideType" to request.rideType?.name,
                "providerPreference" to request.preferredProvider?.name,
                "wantsCheapest" to request.wantsCheapest,
                "wantsFirstOne" to request.wantsFirstOne
            )
        )

        return advanceFromCurrentRequest()
    }

    fun cancelSession(reason: String = "user cancelled"): CabBookingResult {
        val current = session ?: return CabBookingResult(
            state = CabBookingState.CANCELLED,
            message = "There is no active cab booking session to cancel."
        )

        val previousState = current.state
        current.state = CabBookingState.CANCELLED
        current.manualActionReason = null

        CabLogger.i(
            "cancel_session",
            mapOf(
                "reason" to reason,
                "fromState" to previousState.name
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

        val current = session
        if (current == null) {
            val parsedRequest = intentParser.parseInitialCabRequest(trimmed)
            return if (parsedRequest != null) {
                CabLogger.d(
                    "parsed_new_request",
                    mapOf(
                        "rawText" to trimmed,
                        "pickupText" to parsedRequest.pickupText,
                        "pickupMode" to parsedRequest.pickupMode.name,
                        "dropText" to parsedRequest.dropText,
                        "rideType" to parsedRequest.rideType?.name,
                        "providerPreference" to parsedRequest.providerPreference?.name,
                        "wantsCheapest" to parsedRequest.wantsCheapest,
                        "wantsFirstOne" to parsedRequest.wantsFirstOne
                    )
                )
                start(parsedRequest.toBookingRequest())
            } else {
                CabBookingResult(
                    state = CabBookingState.CANCELLED,
                    message = "There is no active cab booking session."
                )
            }
        }

        if (intentParser.isCancel(trimmed)) {
            return cancelSession("user cancelled during cab booking")
        }

        CabLogger.d(
            "handle_input",
            mapOf(
                "state" to current.state.name,
                "rawText" to trimmed,
                "selectedProvider" to current.selectedFare?.provider?.name,
                "fareOptions" to current.fareOptions.joinToString(separator = ",") { it.provider.name }
            )
        )

        return when (current.state) {
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
        setState(current, CabBookingState.PARSING_REQUEST, "evaluating request")

        if (current.pickup == null) {
            if (current.pickupMode == PickupMode.CURRENT_LOCATION) {
                val resolvedLocation = resolveCurrentLocationValue()
                if (resolvedLocation == null) {
                    val blockedReason = currentLocationBlockedReason()
                    CabLogger.d(
                        "pickup_resolution_blocked",
                        mapOf(
                            "mode" to current.pickupMode.name,
                            "reason" to (blockedReason ?: "current_location_unavailable")
                        )
                    )
                    setState(current, CabBookingState.NEED_PICKUP, "current location unavailable")
                    return buildSessionResult(
                        current = current,
                        state = CabBookingState.NEED_PICKUP,
                        message = CabBookingVoiceResponses.currentLocationUnavailable(),
                        pickupBlockedReason = blockedReason
                    )
                }

                current.pickup = resolvedLocation
                CabLogger.d(
                    "pickup_resolution_placeholder",
                    mapOf(
                        "mode" to current.pickupMode.name,
                        "pickup" to current.pickup?.displayText(),
                        "latitude" to current.pickup?.latitude?.toString(),
                        "longitude" to current.pickup?.longitude?.toString()
                    )
                )
            }
        }

        if (current.pickup == null) {
            setState(current, CabBookingState.NEED_PICKUP, "missing pickup")
            CabLogger.d("missing_field", mapOf("field" to "pickup"))
            return buildSessionResult(
                current = current,
                state = CabBookingState.NEED_PICKUP,
                message = CabBookingVoiceResponses.askPickup()
            )
        }

        if (current.pickupMode == PickupMode.CURRENT_LOCATION) {
            CabLogger.d(
                "current_location_ready",
                mapOf(
                    "pickup" to current.pickup?.displayText(),
                    "latitude" to current.pickup?.latitude?.toString(),
                    "longitude" to current.pickup?.longitude?.toString()
                )
            )
        }

        if (current.drop == null) {
            setState(current, CabBookingState.NEED_DROP, "missing drop")
            CabLogger.d("missing_field", mapOf("field" to "drop"))
            val result = buildSessionResult(
                current = current,
                state = CabBookingState.NEED_DROP,
                message = CabBookingVoiceResponses.askDrop()
            )
            return if (current.pickupMode == PickupMode.CURRENT_LOCATION) {
                prependCurrentLocationAnnouncement(result)
            } else {
                result
            }
        }

        if (current.rideType == null) {
            setState(current, CabBookingState.NEED_RIDE_TYPE, "missing ride type")
            CabLogger.d("missing_field", mapOf("field" to "rideType"))
            val result = buildSessionResult(
                current = current,
                state = CabBookingState.NEED_RIDE_TYPE,
                message = CabBookingVoiceResponses.askRideType()
            )
            return if (current.pickupMode == PickupMode.CURRENT_LOCATION) {
                prependCurrentLocationAnnouncement(result)
            } else {
                result
            }
        }

        val result = collectAndCompareFares()
        return if (current.pickupMode == PickupMode.CURRENT_LOCATION) {
            prependCurrentLocationAnnouncement(result)
        } else {
            result
        }
    }

    private fun promptCurrentState(): CabBookingResult {
        val current = session ?: return CabBookingResult(
            state = CabBookingState.CANCELLED,
            message = "There is no active cab booking session."
        )

        return when (current.state) {
            CabBookingState.NEED_PICKUP -> buildSessionResult(
                current,
                CabBookingState.NEED_PICKUP,
                pickupPromptMessage(current),
                pickupBlockedReason = pickupBlockedReason(current)
            )
            CabBookingState.NEED_DROP -> buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
            CabBookingState.NEED_RIDE_TYPE -> buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
            CabBookingState.SHOWING_COMPARISON,
            CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
            CabBookingState.CHECKING_PROVIDERS,
            CabBookingState.OPENING_PROVIDER,
            CabBookingState.FILLING_TRIP,
            CabBookingState.COLLECTING_FARES -> {
                current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
                buildSessionResult(
                    current,
                    CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                    CabBookingVoiceResponses.showComparison(
                        current.fareOptions,
                        current.skippedProviders,
                        current.providerFailures
                    )
                )
            }
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION -> {
                val selected = current.selectedFare
                current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
                buildSessionResult(
                    current,
                    CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                    selected?.let { CabBookingVoiceResponses.askFinalConfirmation(it) }
                        ?: CabBookingVoiceResponses.askPlatformChoice(
                            current.fareOptions,
                            current.skippedProviders,
                            current.providerFailures
                        )
                )
            }
            CabBookingState.MANUAL_ACTION_REQUIRED -> buildSessionResult(
                current,
                CabBookingState.MANUAL_ACTION_REQUIRED,
                CabBookingVoiceResponses.manualActionRequired(
                    current.currentProvider ?: current.selectedFare?.provider,
                    current.manualActionReason ?: "manual action"
                ),
                manualActionRequired = true,
                manualActionReason = current.manualActionReason
            )
            CabBookingState.COMPLETED -> buildSessionResult(
                current,
                CabBookingState.COMPLETED,
                current.selectedFare?.let { CabBookingVoiceResponses.bookingCompleted(it) }
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

    private fun handlePickupResponse(rawText: String): CabBookingResult {
        val current = requireSession()

        val pickupReply = intentParser.parsePickupReply(rawText)
        if (pickupReply == null) {
            setState(current, CabBookingState.NEED_PICKUP, "pickup reply not understood")
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        if (pickupReply.isCurrentLocation) {
            current.pickupMode = PickupMode.CURRENT_LOCATION
            val resolvedLocation = resolveCurrentLocationValue()
            if (resolvedLocation == null) {
                val blockedReason = currentLocationBlockedReason()
                CabLogger.d(
                    "pickup_reply_current_location_blocked",
                    mapOf(
                        "reason" to (blockedReason ?: "current_location_unavailable")
                    )
                )
                current.pickup = null
                setState(current, CabBookingState.NEED_PICKUP, "current location unavailable")
                return buildSessionResult(
                    current = current,
                    state = CabBookingState.NEED_PICKUP,
                    message = CabBookingVoiceResponses.currentLocationUnavailable(),
                    pickupBlockedReason = blockedReason
                )
            }

            current.pickup = resolvedLocation
            CabLogger.d(
                "pickup_reply_current_location",
                mapOf(
                    "displayName" to current.pickup?.displayText(),
                    "latitude" to current.pickup?.latitude?.toString(),
                    "longitude" to current.pickup?.longitude?.toString()
                )
            )
            return advanceFromCurrentRequest()
        }

        val candidate = pickupReply.displayText().takeIf { it.isNotBlank() }
        if (candidate.isNullOrBlank() ||
            intentParser.extractRideType(rawText) != null ||
            intentParser.parseProviderChoiceReply(rawText) != null ||
            intentParser.isCheapestChoice(rawText) ||
            intentParser.isFirstChoice(rawText) ||
            intentParser.isAffirmative(rawText) ||
            intentParser.isNegative(rawText)
        ) {
            setState(current, CabBookingState.NEED_PICKUP, "pickup reply not usable")
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        current.pickup = LocationValue(
            rawText = candidate,
            isCurrentLocation = false,
            displayName = candidate
        )
        current.pickupMode = PickupMode.USER_TEXT
        CabLogger.d("pickup_set", mapOf("pickup" to candidate))
        return advanceFromCurrentRequest()
    }

    private fun handleDropResponse(rawText: String): CabBookingResult {
        val current = requireSession()

        val candidate = intentParser.extractDropLocation(rawText)
        if (candidate.isNullOrBlank() ||
            intentParser.extractRideType(rawText) != null ||
            intentParser.parseProviderChoiceReply(rawText) != null ||
            intentParser.isCheapestChoice(rawText) ||
            intentParser.isFirstChoice(rawText) ||
            intentParser.isAffirmative(rawText) ||
            intentParser.isNegative(rawText)
        ) {
            setState(current, CabBookingState.NEED_DROP, "drop reply not usable")
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        current.drop = LocationValue(
            rawText = candidate,
            displayName = candidate
        )
        CabLogger.d("drop_set", mapOf("drop" to candidate))
        return advanceFromCurrentRequest()
    }

    private fun handleRideTypeResponse(rawText: String): CabBookingResult {
        val current = requireSession()

        val rideType = intentParser.parseRideTypeReply(rawText)
        val wantsCheapest = intentParser.isCheapestChoice(rawText)
        if (rideType == null && !wantsCheapest) {
            setState(current, CabBookingState.NEED_RIDE_TYPE, "ride type reply not usable")
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        current.rideType = rideType ?: RideType.ANY
        current.wantsCheapest = current.wantsCheapest || wantsCheapest
        CabLogger.d(
            "ride_type_set",
            mapOf(
                "rideType" to current.rideType?.name,
                "wantsCheapest" to current.wantsCheapest
            )
        )
        return collectAndCompareFares()
    }

    private fun collectAndCompareFares(): CabBookingResult {
        val current = requireSession()
        setState(current, CabBookingState.CHECKING_PROVIDERS, "collecting provider fares")

        val installedProviders = orderedInstalledProviders(current)
        current.skippedProviders.clear()
        current.providerFailures.clear()
        current.fareOptions.clear()
        current.selectedFare = null
        current.finalConfirmationAsked = false

        val missingProviders = providerRegistry.missingProviders()
        missingProviders.forEach { provider ->
            current.skippedProviders[provider] = "app is not installed"
        }

        CabLogger.d(
            "provider_list",
            mapOf(
                "installedProviders" to installedProviders.joinToString(separator = ",") { it.name },
                "skippedProviders" to current.skippedProviders.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
            )
        )

        if (installedProviders.isEmpty()) {
            setState(current, CabBookingState.FAILED, "no installed providers")
            val message = CabBookingVoiceResponses.noProvidersAvailable(current.skippedProviders)
            val result = buildSessionResult(current, CabBookingState.FAILED, message, availableProviders = emptyList())
            clearSession()
            return result
        }

        val collectedOptions = mutableListOf<CabFareOption>()

        installedProviders.forEachIndexed { index, provider ->
            current.currentProviderIndex = index
            current.currentProvider = provider

            val attempt = attemptProvider(provider, current.toRequest(), collectFare = true)
            CabLogger.d(
                "provider_attempt_result",
                mapOf(
                    "provider" to provider.name,
                    "opened" to attempt.opened,
                    "filledPickup" to attempt.filledPickup,
                    "filledDrop" to attempt.filledDrop,
                    "selectedRideType" to attempt.selectedRideType,
                    "skipped" to attempt.skipped,
                    "failureReason" to attempt.failureReason,
                    "manualActionReason" to attempt.manualActionReason
                )
            )

            attempt.manualActionReason?.let { reason ->
                return manualAction(current, reason)
            }

            if (attempt.skipped) {
                current.skippedProviders[provider] = attempt.failureReason ?: "could not fill trip details"
                CabLogger.i(
                    "provider_skipped",
                    mapOf(
                        "provider" to provider.name,
                        "reason" to current.skippedProviders.getValue(provider)
                    )
                )
                return@forEachIndexed
            }

            if (!attempt.opened || attempt.failureReason != null) {
                current.providerFailures[provider] = attempt.failureReason ?: "could not launch the app"
                CabLogger.w(
                    "provider_failed",
                    mapOf(
                        "provider" to provider.name,
                        "reason" to current.providerFailures.getValue(provider)
                    )
                )
                return@forEachIndexed
            }

            attempt.fareOption?.let { collectedOptions.add(it) }
            attempt.fareOption?.let { current.fareOptions.add(it) }
        }

        if (collectedOptions.isEmpty()) {
            val combinedFailure = combinedProviderFailureSummary(current.skippedProviders, current.providerFailures)
            val allFailureReasons = buildList {
                addAll(current.skippedProviders.values)
                addAll(current.providerFailures.values)
            }
            val currentProvider = current.currentProvider ?: current.selectedFare?.provider
            if (allFailureReasons.any { it == CabFailureReasons.NO_FARE_VISIBLE }) {
                return noFareVisible(current, currentProvider)
            }
            if (allFailureReasons.any { CabFailureReasons.isProviderLaunchIssue(it) }) {
                return providerAccessIssue(
                    current,
                    currentProvider,
                    allFailureReasons.first { CabFailureReasons.isProviderLaunchIssue(it) }
                )
            }

            setState(current, CabBookingState.FAILED, "no fare data read")
            val message = if (combinedFailure.isNotBlank()) {
                CabBookingVoiceResponses.bookingFailed(combinedFailure)
            } else {
                CabBookingVoiceResponses.bookingFailed("fare was not visible on any provider.")
            }
            val result = buildSessionResult(current, CabBookingState.FAILED, message, availableProviders = installedProviders)
            clearSession()
            return result
        }

        val sortedOptions = fareComparator.sortLowestToHighest(collectedOptions)
        current.fareOptions.clear()
        current.fareOptions.addAll(sortedOptions)
        setState(current, CabBookingState.SHOWING_COMPARISON, "fare comparison ready")

        CabLogger.d(
            "fare_comparison_ready",
            mapOf(
                "options" to sortedOptions.joinToString(separator = ",") {
                    "${it.provider.name}:${it.finalFareAmount ?: it.visibleFareAmount ?: "unavailable"}"
                }
            )
        )

        return buildSessionResult(
            current,
            CabBookingState.SHOWING_COMPARISON,
            CabBookingVoiceResponses.showComparison(
                sortedOptions,
                current.skippedProviders,
                current.providerFailures
            ),
            availableProviders = installedProviders
        )
    }

    private fun handlePlatformChoice(rawText: String): CabBookingResult {
        val current = requireSession()

        if (intentParser.isChangeRideRequest(rawText)) {
            current.selectedFare = null
            current.rideType = null
            setState(current, CabBookingState.NEED_RIDE_TYPE, "user changed ride type")
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        if (intentParser.isChangeDropRequest(rawText)) {
            current.selectedFare = null
            current.drop = null
            setState(current, CabBookingState.NEED_DROP, "user changed destination")
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        if (intentParser.isChangePickupRequest(rawText)) {
            current.selectedFare = null
            current.pickup = null
            current.pickupMode = PickupMode.UNKNOWN
            setState(current, CabBookingState.NEED_PICKUP, "user changed pickup")
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        val selectedOption = when {
            intentParser.isCheapestChoice(rawText) -> current.fareOptions.firstOrNull()
            intentParser.isFirstChoice(rawText) -> current.fareOptions.firstOrNull()
            else -> {
                val provider = intentParser.parseProviderChoiceReply(rawText)
                provider?.let { providerChoice ->
                    current.fareOptions.firstOrNull { it.provider == providerChoice }
                }
            }
        }

        if (selectedOption == null) {
            setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "platform choice pending")
            CabLogger.d(
                "platform_choice_pending",
                mapOf(
                    "rawText" to rawText,
                    "fareOptions" to current.fareOptions.joinToString(separator = ",") { it.provider.name }
                )
            )
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                ),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        current.selectedFare = selectedOption
        current.providerPreference = selectedOption.provider
        current.finalUserConfirmed = false
        current.finalConfirmationAsked = false
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

        val prepResult = prepareSelectedProviderForConfirmation(current, selectedOption)
        if (prepResult != null) {
            return prepResult
        }

        setState(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, "awaiting final confirmation")
        current.finalConfirmationAsked = true
        val message = CabBookingVoiceResponses.askFinalConfirmation(selectedOption)
        CabLogger.d(
            "awaiting_final_confirmation",
            mapOf(
                "provider" to selectedOption.provider.name,
                "finalFareAmount" to selectedOption.finalFareAmount,
                "confirmed" to false
            )
        )
        return buildSessionResult(
            current,
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
            message,
            availableProviders = current.fareOptions.map { it.provider }.distinct()
        )
    }

    private fun handleFinalConfirmation(rawText: String): CabBookingResult {
        val current = requireSession()
        val selectedOption = current.selectedFare

        when (intentParser.parseFinalConfirmationReply(rawText)) {
            CabFinalConfirmationReply.DECLINE -> return cancelSession("user cancelled before final booking")
            CabFinalConfirmationReply.NONE -> {
                setState(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, "final confirmation not understood")
                val message = selectedOption?.let { CabBookingVoiceResponses.askFinalConfirmation(it) }
                    ?: CabBookingVoiceResponses.askPlatformChoice(
                        current.fareOptions,
                        current.skippedProviders,
                        current.providerFailures
                    )
                return buildSessionResult(
                    current,
                    CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                    message,
                    availableProviders = current.fareOptions.map { it.provider }.distinct()
                )
            }
            CabFinalConfirmationReply.CONFIRM -> Unit
        }

        if (selectedOption == null) {
            setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "final confirmation without selected fare")
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                ),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        current.finalUserConfirmed = true
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

        if (!accessibilityService.tapFinalBookButtonOnlyIfConfirmed(finalUserConfirmed = true)) {
            val reason = accessibilityService.detectManualActionRequired()
                ?: "I could not tap the final confirm button."
            return if (reason == "I could not tap the final confirm button.") {
                setState(current, CabBookingState.FAILED, "final booking tap failed")
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

        setState(current, CabBookingState.COMPLETED, "booking completed")
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

        if (intentParser.isCancel(rawText)) {
            return cancelSession("user cancelled after manual step")
        }

        if (intentParser.isFareResumeRequest(rawText)) {
            resumeFareComparisonFromCurrentProvider()?.let { return it }
        }

        current.manualActionReason = null

        if (current.selectedFare != null) {
            setState(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, "manual step cleared for selected fare")
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                CabBookingVoiceResponses.askFinalConfirmation(current.selectedFare!!),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        if (current.fareOptions.isNotEmpty()) {
            setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "manual step cleared for fare comparison")
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(
                    current.fareOptions,
                    current.skippedProviders,
                    current.providerFailures
                ),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        if (current.pickup == null) {
            setState(current, CabBookingState.NEED_PICKUP, "manual step cleared and pickup still missing")
            return buildSessionResult(
                current,
                CabBookingState.NEED_PICKUP,
                pickupPromptMessage(current),
                pickupBlockedReason = pickupBlockedReason(current)
            )
        }

        if (current.drop == null) {
            setState(current, CabBookingState.NEED_DROP, "manual step cleared and drop still missing")
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        if (current.rideType == null) {
            setState(current, CabBookingState.NEED_RIDE_TYPE, "manual step cleared and ride type still missing")
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        return collectAndCompareFares()
    }

    private fun manualAction(current: CabBookingSession, reason: String): CabBookingResult {
        val provider = current.currentProvider ?: current.selectedFare?.provider
        return when {
            CabFailureReasons.isFieldMissing(reason) -> manualDestinationHandoff(current, provider, reason)
            reason == CabFailureReasons.NO_FARE_VISIBLE -> noFareVisible(current, provider)
            CabFailureReasons.isProviderLaunchIssue(reason) -> providerAccessIssue(current, provider, reason)
            else -> genericManualAction(current, reason)
        }
    }

    private fun genericManualAction(current: CabBookingSession, reason: String): CabBookingResult {
        current.state = CabBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        CabLogger.w(
            "manual_action_required",
            mapOf(
                "provider" to current.currentProvider?.name,
                "selectedProvider" to current.selectedFare?.provider?.name,
                "reason" to reason
            )
        )
        return buildSessionResult(
            current = current,
            state = CabBookingState.MANUAL_ACTION_REQUIRED,
            message = CabBookingVoiceResponses.manualActionRequired(
                current.currentProvider ?: current.selectedFare?.provider,
                reason
            ),
            manualActionRequired = true,
            manualActionReason = reason
        )
    }

    private fun manualDestinationHandoff(
        current: CabBookingSession,
        provider: CabProvider?,
        reason: String
    ): CabBookingResult {
        current.state = CabBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        if (provider != null) {
            current.currentProvider = provider
        }

        CabLogger.w(
            "manual_destination_handoff",
            mapOf(
                "provider" to provider?.name,
                "reason" to reason
            )
        )

        return buildSessionResult(
            current = current,
            state = CabBookingState.MANUAL_ACTION_REQUIRED,
            message = provider?.let { CabBookingVoiceResponses.manualDestinationHandoff(it) }
                ?: CabBookingVoiceResponses.manualActionRequired(null, reason),
            manualActionRequired = true,
            manualActionReason = reason
        )
    }

    private fun providerAccessIssue(
        current: CabBookingSession,
        provider: CabProvider?,
        reason: String
    ): CabBookingResult {
        current.state = CabBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        if (provider != null) {
            current.currentProvider = provider
        }

        CabLogger.w(
            "provider_access_issue",
            mapOf(
                "provider" to provider?.name,
                "reason" to reason
            )
        )

        return buildSessionResult(
            current = current,
            state = CabBookingState.MANUAL_ACTION_REQUIRED,
            message = provider?.let { CabBookingVoiceResponses.providerFailed(it, reason) }
                ?: CabBookingVoiceResponses.bookingFailed(CabBookingVoiceResponses.describeFailureReason(reason)),
            manualActionRequired = true,
            manualActionReason = reason
        )
    }

    private fun noFareVisible(
        current: CabBookingSession,
        provider: CabProvider?
    ): CabBookingResult {
        current.state = CabBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = CabFailureReasons.NO_FARE_VISIBLE
        if (provider != null) {
            current.currentProvider = provider
        }

        CabLogger.w(
            "no_fare_visible_state",
            mapOf(
                "provider" to provider?.name,
                "currentProviderIndex" to current.currentProviderIndex.toString()
            )
        )

        return buildSessionResult(
            current = current,
            state = CabBookingState.MANUAL_ACTION_REQUIRED,
            message = CabBookingVoiceResponses.noFareVisible(provider),
            manualActionRequired = true,
            manualActionReason = CabFailureReasons.NO_FARE_VISIBLE
        )
    }

    private fun resumeFareComparisonFromCurrentProvider(): CabBookingResult? {
        val current = requireSession()
        val provider = current.currentProvider ?: current.selectedFare?.provider ?: return null
        val snapshot = accessibilityService.captureScreenSnapshot()
        if (snapshot == null) {
            return providerAccessIssue(current, provider, CabFailureReasons.PROVIDER_NOT_OPENED)
        }

        val manualReason = accessibilityService.detectManualActionRequired(snapshot)
        if (manualReason != null) {
            return manualAction(current, manualReason)
        }

        if (!isCurrentProviderSnapshot(provider, snapshot.sourcePackageName)) {
            return providerAccessIssue(current, provider, CabFailureReasons.PROVIDER_NOT_OPENED)
        }

        val fareCollection = accessibilityService.collectFareOption(
            provider = provider,
            request = current.toRequest(),
            snapshot = snapshot,
            attempts = 1,
            totalWaitMs = 1_000L
        )

        val fareOption = fareCollection.fareOption
        if (fareOption == null) {
            val reason = fareCollection.failureReason ?: CabFailureReasons.NO_FARE_VISIBLE
            return when {
                CabFailureReasons.isFieldMissing(reason) -> manualDestinationHandoff(current, provider, reason)
                reason == CabFailureReasons.NO_FARE_VISIBLE -> noFareVisible(current, provider)
                CabFailureReasons.isProviderLaunchIssue(reason) -> providerAccessIssue(current, provider, reason)
                else -> manualAction(current, reason)
            }
        }

        current.fareOptions.removeAll { it.provider == provider }
        current.fareOptions.add(fareOption)
        current.manualActionReason = null

        val installedProviders = orderedInstalledProviders(current)
        val collectedOptions = current.fareOptions.toMutableList()
        val startIndex = (current.currentProviderIndex + 1).coerceAtLeast(0)

        for (index in startIndex until installedProviders.size) {
            val nextProvider = installedProviders[index]
            current.currentProviderIndex = index
            current.currentProvider = nextProvider

            val attempt = attemptProvider(nextProvider, current.toRequest(), collectFare = true)
            CabLogger.d(
                "resume_provider_attempt_result",
                mapOf(
                    "provider" to nextProvider.name,
                    "opened" to attempt.opened,
                    "filledPickup" to attempt.filledPickup,
                    "filledDrop" to attempt.filledDrop,
                    "selectedRideType" to attempt.selectedRideType,
                    "skipped" to attempt.skipped,
                    "failureReason" to attempt.failureReason,
                    "manualActionReason" to attempt.manualActionReason
                )
            )

            attempt.manualActionReason?.let { reason ->
                return manualAction(current, reason)
            }

            if (attempt.skipped) {
                current.skippedProviders[nextProvider] = attempt.failureReason ?: "could not fill trip details"
                continue
            }

            if (!attempt.opened || attempt.failureReason != null) {
                current.providerFailures[nextProvider] = attempt.failureReason ?: CabFailureReasons.PROVIDER_NOT_OPENED
                continue
            }

            attempt.fareOption?.let { option ->
                collectedOptions.removeAll { it.provider == option.provider }
                collectedOptions.add(option)
                current.fareOptions.removeAll { it.provider == option.provider }
                current.fareOptions.add(option)
            }
        }

        if (collectedOptions.isEmpty()) {
            return noFareVisible(current, provider)
        }

        val sortedOptions = fareComparator.sortLowestToHighest(collectedOptions)
        current.fareOptions.clear()
        current.fareOptions.addAll(sortedOptions)
        current.manualActionReason = null
        setState(current, CabBookingState.SHOWING_COMPARISON, "fare comparison resumed")

        CabLogger.d(
            "fare_comparison_resumed",
            mapOf(
                "provider" to provider.name,
                "options" to sortedOptions.joinToString(separator = ",") {
                    "${it.provider.name}:${it.finalFareAmount ?: it.visibleFareAmount ?: "unavailable"}"
                }
            )
        )

        return buildSessionResult(
            current,
            CabBookingState.SHOWING_COMPARISON,
            CabBookingVoiceResponses.showComparison(
                sortedOptions,
                current.skippedProviders,
                current.providerFailures
            ),
            availableProviders = installedProviders
        )
    }

    private fun isCurrentProviderSnapshot(provider: CabProvider, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return expectedCurrentProviderPackages(provider).contains(packageName)
    }

    private fun expectedCurrentProviderPackages(provider: CabProvider): Set<String> {
        return buildSet {
            providerRegistry.installedPackageName(provider)?.takeIf { it.isNotBlank() }?.let { add(it) }
            providerRegistry.packageName(provider).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    private fun buildSessionResult(
        current: CabBookingSession,
        state: CabBookingState,
        message: String,
        manualActionRequired: Boolean = false,
        manualActionReason: String? = null,
        pickupBlockedReason: String? = null,
        finalUserConfirmed: Boolean = current.finalUserConfirmed,
        availableProviders: List<CabProvider> = current.fareOptions.map { it.provider }.distinct()
    ): CabBookingResult {
        return CabBookingResult(
            state = state,
            message = message,
            request = current.toRequest(),
            fareOptions = current.fareOptions.toList(),
            selectedOption = current.selectedFare,
            selectedFareOption = current.selectedFare,
            selectedFare = current.selectedFare,
            selectedProvider = current.selectedFare?.provider,
            availableProviders = availableProviders,
            skippedProviders = current.skippedProviders.toMap(),
            providerFailures = current.providerFailures.toMap(),
            currentProviderIndex = current.currentProviderIndex,
            finalConfirmationAsked = current.finalConfirmationAsked,
            manualActionRequired = manualActionRequired,
            manualActionReason = manualActionReason,
            pickupBlockedReason = pickupBlockedReason,
            finalUserConfirmed = finalUserConfirmed,
            currentState = current.state
        )
    }

    private fun attemptProvider(
        provider: CabProvider,
        request: CabBookingRequest,
        collectFare: Boolean
    ): ProviderAttemptResult {
        val current = requireSession()
        val launchPlan = deepLinkBuilder.buildLaunchPlan(provider, request, null)
        CabLogger.d(
            "deep_link_result",
            mapOf(
                "provider" to provider.name,
                "launched" to launchPlan.launched,
                "supportsDirectTripIntent" to launchPlan.supportsDirectTripIntent,
                "needsAccessibilityFill" to launchPlan.needsAccessibilityFill,
                "failureReason" to launchPlan.failureReason,
                "launchMode" to launchPlan.launchMode
            )
        )

        val launchOutcome = launchProviderAndWaitForForeground(
            provider = provider,
            request = request,
            initialLaunchPlan = launchPlan
        )
        if (!launchOutcome.isSuccessful) {
            return ProviderAttemptResult(
                provider = provider,
                opened = launchOutcome.failureReason != CabFailureReasons.PROVIDER_NOT_OPENED,
                filledPickup = false,
                filledDrop = false,
                selectedRideType = false,
                failureReason = launchOutcome.failureReason
                    ?: launchPlan.failureReason
                    ?: CabFailureReasons.PROVIDER_NOT_OPENED
            )
        }

        val foregroundPackage = launchOutcome.foregroundPackage
        val usedLaunchPlan = launchOutcome.launchPlan

        val preFillSnapshot = waitForInspectableCabSnapshot(provider, usedLaunchPlan)
        if (preFillSnapshot == null || !isExpectedProviderSnapshot(provider, usedLaunchPlan, preFillSnapshot.sourcePackageName)) {
            CabLogger.w(
                "provider_snapshot_unavailable",
                mapOf(
                    "provider" to provider.name,
                    "foregroundPackage" to foregroundPackage,
                    "snapshotPackage" to preFillSnapshot?.sourcePackageName
                )
            )
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = false,
                filledDrop = false,
                selectedRideType = false,
                failureReason = CabFailureReasons.PROVIDER_NOT_OPENED
            )
        }

        val preFillManualReason = accessibilityService.detectManualActionRequired(preFillSnapshot)
        val shouldDeferRapidoPaymentStop = provider == CabProvider.RAPIDO &&
            preFillManualReason == "payment" &&
            current.pickup?.isCurrentLocation == true &&
            hasFareSignal(preFillSnapshot)

        if (preFillManualReason != null && !shouldDeferRapidoPaymentStop) {
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = false,
                filledDrop = false,
                selectedRideType = false,
                manualActionReason = preFillManualReason
            )
        }

        if (shouldDeferRapidoPaymentStop) {
            CabLogger.d(
                "rapido_payment_manual_action_deferred",
                mapOf(
                    "provider" to provider.name,
                    "screenText" to preFillSnapshot.sourceText
                )
            )
        }

        setState(current, CabBookingState.FILLING_TRIP, "filling ${provider.name}")
        val tripFill = accessibilityService.fillTripForProvider(provider, current.pickup, current.drop, current.rideType)

        val postFillSnapshot = accessibilityService.captureScreenSnapshot()
        current.lastProviderScreenText = postFillSnapshot?.sourceText
        if (postFillSnapshot == null || !isExpectedProviderSnapshot(provider, usedLaunchPlan, postFillSnapshot.sourcePackageName)) {
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = tripFill.filledPickup,
                filledDrop = tripFill.filledDrop,
                selectedRideType = tripFill.selectedRideType,
                failureReason = CabFailureReasons.PROVIDER_NOT_OPENED
            )
        }
        CabLogger.d(
            "accessibility_screen_summary",
            mapOf(
                "provider" to provider.name,
                "screenText" to postFillSnapshot?.sourceText,
                "fareText" to postFillSnapshot?.visibleFareText,
                "finalFareText" to postFillSnapshot?.finalFareText,
                "manualActionReason" to postFillSnapshot?.manualActionReason
            )
        )

        val postFillManualReason = accessibilityService.detectManualActionRequired(postFillSnapshot)
        if (postFillManualReason != null) {
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = tripFill.filledPickup,
                filledDrop = tripFill.filledDrop,
                selectedRideType = tripFill.selectedRideType,
                manualActionReason = postFillManualReason
            )
        }

        if (!tripFill.canContinueToFareScreen) {
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = tripFill.filledPickup,
                filledDrop = tripFill.filledDrop,
                selectedRideType = tripFill.selectedRideType,
                failureReason = tripFill.failureReason ?: CabFailureReasons.MANUAL_ACTION_REQUIRED,
                manualActionReason = if (CabFailureReasons.isFieldMissing(tripFill.failureReason)) {
                    tripFill.failureReason
                } else {
                    null
                },
                skipped = !CabFailureReasons.isFieldMissing(tripFill.failureReason)
            )
        }

        tripFill.warningReason?.let { warningReason ->
            CabLogger.w(
                "provider_fill_warning",
                mapOf(
                    "provider" to provider.name,
                    "reason" to warningReason
                )
            )
        }

        if (!collectFare) {
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = tripFill.filledPickup,
                filledDrop = tripFill.filledDrop,
                selectedRideType = tripFill.selectedRideType
            )
        }

        setState(current, CabBookingState.COLLECTING_FARES, "reading fare from ${provider.name}")
        val fareCollection = accessibilityService.collectFareOption(provider, request, postFillSnapshot)
        if (fareCollection.fareOption == null) {
            return ProviderAttemptResult(
                provider = provider,
                opened = true,
                filledPickup = tripFill.filledPickup,
                filledDrop = tripFill.filledDrop,
                selectedRideType = tripFill.selectedRideType,
                failureReason = fareCollection.failureReason ?: CabFailureReasons.NO_FARE_VISIBLE
            )
        }

        return ProviderAttemptResult(
            provider = provider,
            opened = true,
            filledPickup = tripFill.filledPickup,
            filledDrop = tripFill.filledDrop,
            selectedRideType = tripFill.selectedRideType,
            fareOption = fareCollection.fareOption
        )
    }

    private fun prepareSelectedProviderForConfirmation(
        current: CabBookingSession,
        selectedOption: CabFareOption
    ): CabBookingResult? {
        current.currentProvider = selectedOption.provider
        val request = current.toRequest().copy(
            preferredProvider = selectedOption.provider,
            finalUserConfirmed = false
        )
        val launchPlan = deepLinkBuilder.buildLaunchPlan(selectedOption.provider, request, selectedOption)
        CabLogger.d(
            "deep_link_result",
            mapOf(
                "provider" to selectedOption.provider.name,
                "launched" to launchPlan.launched,
                "supportsDirectTripIntent" to launchPlan.supportsDirectTripIntent,
                "needsAccessibilityFill" to launchPlan.needsAccessibilityFill,
                "failureReason" to launchPlan.failureReason,
                "launchMode" to launchPlan.launchMode
            )
        )

        val launchOutcome = launchProviderAndWaitForForeground(
            provider = selectedOption.provider,
            request = request,
            initialLaunchPlan = launchPlan,
            selectedOption = selectedOption
        )
        if (!launchOutcome.isSuccessful) {
            current.providerFailures[selectedOption.provider] = launchOutcome.failureReason
                ?: launchPlan.failureReason
                ?: CabFailureReasons.PROVIDER_NOT_OPENED
            current.selectedFare = null
            current.finalConfirmationAsked = false
            current.finalUserConfirmed = false
            setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "selected provider launch failed")
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.providerFailed(
                    selectedOption.provider,
                    current.providerFailures.getValue(selectedOption.provider)
                ),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        val foregroundPackage = launchOutcome.foregroundPackage
        val usedLaunchPlan = launchOutcome.launchPlan

        val preFillSnapshot = waitForInspectableCabSnapshot(selectedOption.provider, usedLaunchPlan)
        if (preFillSnapshot == null || !isExpectedProviderSnapshot(selectedOption.provider, usedLaunchPlan, preFillSnapshot.sourcePackageName)) {
            current.providerFailures[selectedOption.provider] = CabFailureReasons.PROVIDER_NOT_OPENED
            current.selectedFare = null
            current.finalConfirmationAsked = false
            current.finalUserConfirmed = false
            setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "selected provider snapshot unavailable")
            CabLogger.w(
                "provider_snapshot_unavailable",
                mapOf(
                    "provider" to selectedOption.provider.name,
                    "foregroundPackage" to foregroundPackage,
                    "snapshotPackage" to preFillSnapshot?.sourcePackageName
                )
            )
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.providerFailed(
                    selectedOption.provider,
                    CabFailureReasons.PROVIDER_NOT_OPENED
                ),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        val preFillManualReason = accessibilityService.detectManualActionRequired(preFillSnapshot)
        if (preFillManualReason != null) {
            return manualAction(current, preFillManualReason)
        }

        setState(current, CabBookingState.FILLING_TRIP, "preparing selected provider")
        val tripFill = accessibilityService.fillTripForProvider(selectedOption.provider, current.pickup, current.drop, current.rideType)
        val snapshot = accessibilityService.captureScreenSnapshot()
        current.lastProviderScreenText = snapshot?.sourceText

        if (snapshot == null || !isExpectedProviderSnapshot(selectedOption.provider, usedLaunchPlan, snapshot.sourcePackageName)) {
            current.providerFailures[selectedOption.provider] = CabFailureReasons.PROVIDER_NOT_OPENED
            current.selectedFare = null
            current.finalConfirmationAsked = false
            current.finalUserConfirmed = false
            setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "selected provider snapshot unavailable after fill")
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.providerFailed(
                    selectedOption.provider,
                    CabFailureReasons.PROVIDER_NOT_OPENED
                ),
                availableProviders = current.fareOptions.map { it.provider }.distinct()
            )
        }

        val postFillManualReason = accessibilityService.detectManualActionRequired(snapshot)
        if (postFillManualReason != null) {
            return manualAction(current, postFillManualReason)
        }

        if (!tripFill.canContinueToFareScreen) {
            val reason = tripFill.failureReason ?: "could not fill trip details"
            return if (CabFailureReasons.isFieldMissing(reason)) {
                manualAction(current, reason)
            } else {
                current.skippedProviders[selectedOption.provider] = reason
                current.selectedFare = null
                current.finalConfirmationAsked = false
                current.finalUserConfirmed = false
                setState(current, CabBookingState.WAITING_FOR_PLATFORM_CHOICE, "selected provider field fill failed")
                buildSessionResult(
                    current,
                    CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                    CabBookingVoiceResponses.providerSkipped(
                        selectedOption.provider,
                        reason,
                        nextProvider = nextAvailableProvider(current, selectedOption.provider)
                    ),
                    availableProviders = current.fareOptions.map { it.provider }.distinct()
                )
            }
        }

        tripFill.warningReason?.let { warningReason ->
            CabLogger.w(
                "provider_fill_warning",
                mapOf(
                    "provider" to selectedOption.provider.name,
                    "reason" to warningReason
                )
            )
        }

        current.finalConfirmationAsked = true
        setState(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, "selected provider prepared")
        CabLogger.d(
            "awaiting_final_confirmation",
            mapOf(
                "provider" to selectedOption.provider.name,
                "finalFareAmount" to selectedOption.finalFareAmount,
                "confirmed" to false
            )
        )

        return buildSessionResult(
            current,
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
            CabBookingVoiceResponses.askFinalConfirmation(selectedOption),
            availableProviders = current.fareOptions.map { it.provider }.distinct()
        )
    }

    private fun resolveCurrentLocationValue(): LocationValue? {
        val resolver = locationResolver
        if (resolver == null || !resolver.hasLocationPermission()) {
            return null
        }

        val latLng = resolver.getCurrentLatLng() ?: return null
        val displayName = resolver.getCurrentLocationDisplay()?.takeIf { it.isNotBlank() } ?: "Current location"
        return LocationValue(
            rawText = displayName,
            isCurrentLocation = true,
            latitude = latLng.first,
            longitude = latLng.second,
            displayName = displayName
        )
    }

    private fun currentLocationBlockedReason(): String? {
        return if (locationResolver?.hasLocationPermission() == false) {
            CabFailureReasons.BLOCKED_BY_LOCATION_PERMISSION
        } else {
            null
        }
    }

    private fun pickupPromptMessage(current: CabBookingSession): String {
        return if (current.pickupMode == PickupMode.CURRENT_LOCATION && current.pickup != null) {
            CabBookingVoiceResponses.usingCurrentLocationAsPickup()
        } else if (current.pickupMode == PickupMode.CURRENT_LOCATION) {
            CabBookingVoiceResponses.currentLocationUnavailable()
        } else {
            CabBookingVoiceResponses.askPickup()
        }
    }

    private fun pickupBlockedReason(current: CabBookingSession): String? {
        return if (current.pickupMode == PickupMode.CURRENT_LOCATION && current.pickup == null) {
            currentLocationBlockedReason()
        } else {
            null
        }
    }

    private fun orderedInstalledProviders(current: CabBookingSession): List<CabProvider> {
        val installed = providerRegistry.installedProviders().distinct()
        val preferred = current.providerPreference
        return if (preferred != null && installed.contains(preferred)) {
            listOf(preferred) + installed.filterNot { it == preferred }
        } else {
            installed
        }
    }

    private fun nextAvailableProvider(current: CabBookingSession, provider: CabProvider): CabProvider? {
        val remaining = current.fareOptions.map { it.provider }.distinct().filterNot { it == provider }
        return remaining.firstOrNull()
    }

    private fun combinedProviderFailureSummary(
        skippedProviders: Map<CabProvider, String>,
        providerFailures: Map<CabProvider, String>
    ): String {
        val parts = buildList {
            if (skippedProviders.isNotEmpty()) {
                add(skippedProviders.entries.joinToString(separator = ". ") {
                    "${it.key.displayName()} was skipped because ${CabBookingVoiceResponses.describeFailureReason(it.value)}"
                })
            }
            if (providerFailures.isNotEmpty()) {
                add(providerFailures.entries.joinToString(separator = ". ") {
                    "${it.key.displayName()} failed because ${CabBookingVoiceResponses.describeFailureReason(it.value)}"
                })
            }
        }.filter { it.isNotBlank() }

        return parts.joinToString(separator = ". ")
    }

    private data class ProviderLaunchOutcome(
        val launchPlan: CabDeepLinkResult,
        val foregroundPackage: String? = null,
        val failureReason: String? = null
    ) {
        val isSuccessful: Boolean
            get() = foregroundPackage != null
    }

    private fun launchProviderAndWaitForForeground(
        provider: CabProvider,
        request: CabBookingRequest,
        initialLaunchPlan: CabDeepLinkResult,
        selectedOption: CabFareOption? = null,
        primaryWaitMs: Long = 5_000L,
        fallbackWaitMs: Long = 8_000L
    ): ProviderLaunchOutcome {
        val current = requireSession()
        val primaryOutcome = launchPlanAndWaitForForeground(
            current = current,
            provider = provider,
            launchPlan = initialLaunchPlan,
            waitTotalMs = primaryWaitMs
        )
        if (primaryOutcome.isSuccessful) {
            return ProviderLaunchOutcome(
                launchPlan = initialLaunchPlan,
                foregroundPackage = primaryOutcome.foregroundPackage
            )
        }

        if (provider != CabProvider.OLA) {
            return primaryOutcome
        }

        val fallbackIntent = deepLinkBuilder.buildPackageLaunchIntent(provider, request, selectedOption) ?: return primaryOutcome
        CabLogger.d(
            "ola_package_fallback_attempt",
            mapOf(
                "provider" to provider.name,
                "packageName" to fallbackIntent.`package`
            )
        )

        val fallbackLaunchPlan = initialLaunchPlan.copy(
            intent = fallbackIntent,
            launched = true,
            supportsDirectTripIntent = false,
            launchMode = "package",
            failureReason = null
        )
        val fallbackOutcome = launchPlanAndWaitForForeground(
            current = current,
            provider = provider,
            launchPlan = fallbackLaunchPlan,
            waitTotalMs = fallbackWaitMs
        )
        return if (fallbackOutcome.isSuccessful) {
            ProviderLaunchOutcome(
                launchPlan = fallbackLaunchPlan,
                foregroundPackage = fallbackOutcome.foregroundPackage
            )
        } else {
            fallbackOutcome.copy(launchPlan = fallbackLaunchPlan)
        }
    }

    private fun launchPlanAndWaitForForeground(
        current: CabBookingSession,
        provider: CabProvider,
        launchPlan: CabDeepLinkResult,
        waitTotalMs: Long
    ): ProviderLaunchOutcome {
        val intent = launchPlan.intent ?: return ProviderLaunchOutcome(
            launchPlan = launchPlan,
            failureReason = CabFailureReasons.PROVIDER_NOT_OPENED
        )
        val opened = runCatching { providerLauncher.launch(intent) }.getOrDefault(false)
        CabLogger.d(
            "provider_opened",
            mapOf(
                "provider" to provider.name,
                "opened" to opened,
                "launchMode" to launchPlan.launchMode
            )
        )
        if (!opened) {
            return ProviderLaunchOutcome(
                launchPlan = launchPlan,
                failureReason = CabFailureReasons.PROVIDER_NOT_OPENED
            )
        }

        val waitReason = if (launchPlan.launchMode == "package") {
            "waiting for ${provider.name} foreground via package fallback"
        } else {
            "waiting for ${provider.name} foreground"
        }
        setState(current, CabBookingState.OPENING_PROVIDER, waitReason)
        val foregroundPackage = waitForProviderForeground(provider, launchPlan, totalWaitMs = waitTotalMs)
        return if (foregroundPackage != null) {
            ProviderLaunchOutcome(
                launchPlan = launchPlan,
                foregroundPackage = foregroundPackage
            )
        } else {
            ProviderLaunchOutcome(
                launchPlan = launchPlan,
                failureReason = CabFailureReasons.PROVIDER_FOREGROUND_TIMEOUT
            )
        }
    }

    private fun waitForProviderForeground(
        provider: CabProvider,
        launchPlan: CabDeepLinkResult,
        totalWaitMs: Long = 5_000L
    ): String? {
        val expectedPackages = expectedProviderPackages(provider, launchPlan)
        CabLogger.d(
            "provider_foreground_wait",
            mapOf(
                "provider" to provider.name,
                "expectedPackages" to expectedPackages.joinToString(separator = ","),
                "totalWaitMs" to totalWaitMs.toString()
            )
        )
        return accessibilityService.waitForForegroundPackage(expectedPackages, totalWaitMs = totalWaitMs)
    }

    private fun waitForInspectableCabSnapshot(
        provider: CabProvider,
        launchPlan: CabDeepLinkResult,
        attempts: Int = 10,
        totalWaitMs: Long = 5000L
    ): CabScreenSnapshot? {
        val expectedPackages = expectedProviderPackages(provider, launchPlan)
        if (attempts <= 0) return null

        val delayMs = (totalWaitMs / attempts).coerceAtLeast(100L)
        CabLogger.d(
            "provider_snapshot_wait",
            mapOf(
                "provider" to provider.name,
                "expectedPackages" to expectedPackages.joinToString(separator = ",")
            )
        )

        repeat(attempts) { attempt ->
            val snapshot = accessibilityService.captureScreenSnapshot()
            val packageName = snapshot?.sourcePackageName?.takeIf { it.isNotBlank() }
            if (snapshot != null &&
                packageName != null &&
                accessibilityService.isInspectableCabPackage(packageName)
            ) {
                CabLogger.d(
                    "provider_snapshot_matched",
                    mapOf(
                        "provider" to provider.name,
                        "packageName" to packageName,
                        "attempt" to (attempt + 1).toString()
                    )
                )
                return snapshot
            }

            if (attempt < attempts - 1) {
                runCatching { Thread.sleep(delayMs) }
            }
        }

        CabLogger.w(
            "provider_snapshot_timeout",
            mapOf(
                "provider" to provider.name,
                "expectedPackages" to expectedPackages.joinToString(separator = ",")
            )
        )
        return null
    }

    private fun hasFareSignal(snapshot: CabScreenSnapshot?): Boolean {
        if (snapshot == null) return false
        return snapshot.fareCandidates.isNotEmpty() ||
            !snapshot.visibleFareText.isNullOrBlank() ||
            !snapshot.finalFareText.isNullOrBlank()
    }

    private fun expectedProviderPackages(provider: CabProvider, launchPlan: CabDeepLinkResult): Set<String> {
        return buildSet {
            launchPlan.intent?.`package`?.takeIf { it.isNotBlank() }?.let { add(it) }
            providerRegistry.installedPackageName(provider)?.takeIf { it.isNotBlank() }?.let { add(it) }
            providerRegistry.packageName(provider).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    private fun isExpectedProviderSnapshot(
        provider: CabProvider,
        launchPlan: CabDeepLinkResult,
        packageName: String?
    ): Boolean {
        if (packageName.isNullOrBlank()) return false
        return expectedProviderPackages(provider, launchPlan).contains(packageName) ||
            accessibilityService.isInspectableCabPackage(packageName)
    }

    private fun prependCurrentLocationAnnouncement(result: CabBookingResult): CabBookingResult {
        val announcement = CabBookingVoiceResponses.usingCurrentLocationAsPickup()
        val combinedMessage = if (result.message.startsWith(announcement)) {
            result.message
        } else {
            "$announcement ${result.message}".trim()
        }
        return result.copy(message = combinedMessage)
    }

    private fun setState(current: CabBookingSession, newState: CabBookingState, reason: String) {
        val previous = current.state
        current.state = newState
        CabLogger.d(
            "state_transition",
            mapOf(
                "from" to previous.name,
                "to" to newState.name,
                "reason" to reason
            )
        )
    }

    private fun requireSession(): CabBookingSession {
        return session ?: throw IllegalStateException("No active cab booking session.")
    }

    private fun clearSession() {
        session = null
    }

}
