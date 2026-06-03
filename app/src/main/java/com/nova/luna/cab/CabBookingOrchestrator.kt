package com.nova.luna.cab

import android.content.Context
import android.content.Intent
import android.location.LocationManager

fun interface CabProviderLauncher {
    fun launch(intent: Intent): Boolean
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
        val fareOptions: MutableList<CabFareOption> = mutableListOf(),
        var selectedOption: CabFareOption? = null,
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
        session = CabBookingSession(request = request)
        return advanceSession()
    }

    fun handleUserInput(rawText: String): CabBookingResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return promptCurrentState()
        }

        val parsedRequest = intentParser.parse(trimmed)
        if (parsedRequest != null) {
            return start(parsedRequest)
        }

        val current = session ?: return CabBookingResult(
            state = CabBookingState.FAILED,
            message = CabBookingVoiceResponses.bookingFailed("no active cab booking session.")
        )

        return when (current.state) {
            CabBookingState.NEED_PICKUP -> handlePickupResponse(trimmed)
            CabBookingState.NEED_DROP -> handleDropResponse(trimmed)
            CabBookingState.NEED_RIDE_TYPE -> handleRideTypeResponse(trimmed)
            CabBookingState.OPENING_PROVIDERS,
            CabBookingState.COLLECTING_FARES,
            CabBookingState.SHOWING_COMPARISON,
            CabBookingState.WAITING_FOR_PLATFORM_CHOICE -> handlePlatformChoice(trimmed)
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION -> handleFinalConfirmation(trimmed)
            CabBookingState.MANUAL_ACTION_REQUIRED -> handleManualActionFollowUp(trimmed)
            CabBookingState.COMPLETED,
            CabBookingState.FAILED,
            CabBookingState.IDLE,
            CabBookingState.BOOKING -> promptCurrentState()
        }
    }

    private fun advanceSession(): CabBookingResult {
        val current = requireSession()
        resolvePickupIfPossible(current)

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

    private fun promptCurrentState(): CabBookingResult {
        val current = session ?: return CabBookingResult(
            state = CabBookingState.FAILED,
            message = CabBookingVoiceResponses.bookingFailed("no active cab booking session.")
        )

        return when (current.state) {
            CabBookingState.NEED_PICKUP -> buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
            CabBookingState.NEED_DROP -> buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
            CabBookingState.NEED_RIDE_TYPE -> buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
            CabBookingState.SHOWING_COMPARISON,
            CabBookingState.WAITING_FOR_PLATFORM_CHOICE -> buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION -> {
                val selected = current.selectedOption
                val message = if (selected != null) {
                    CabBookingVoiceResponses.askFinalConfirmation(selected)
                } else {
                    CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
                }
                buildSessionResult(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
            }
            CabBookingState.MANUAL_ACTION_REQUIRED -> buildSessionResult(
                current,
                CabBookingState.MANUAL_ACTION_REQUIRED,
                CabBookingVoiceResponses.manualActionRequired(
                    current.manualActionReason
                        ?: current.selectedOption?.let { CabBookingVoiceResponses.formatFareOption(it) }
                        ?: "manual action required"
                ),
                manualActionRequired = true
            )
            CabBookingState.COMPLETED -> buildSessionResult(
                current,
                CabBookingState.COMPLETED,
                current.selectedOption?.let { CabBookingVoiceResponses.bookingCompleted(it) }
                    ?: CabBookingVoiceResponses.bookingCompleted(
                        CabFareOption(
                            provider = CabProvider.UBER,
                            rideType = current.request.rideType ?: RideType.ANY
                        )
                    )
            )
            else -> buildSessionResult(current, current.state, CabBookingVoiceResponses.askPickup())
        }
    }

    private fun resolvePickupIfPossible(current: CabBookingSession) {
        if (!current.request.pickupLocation.isNullOrBlank()) return
        val resolved = pickupLocationResolver?.resolvePickupLocation() ?: return
        current.request = current.request.withPickupLocation(resolved)
    }

    private fun handlePickupResponse(rawText: String): CabBookingResult {
        val current = requireSession()
        val candidate = intentParser.extractDropLocation(rawText)
        if (candidate.isNullOrBlank() || intentParser.extractRideType(rawText) != null || intentParser.parseProviderChoice(rawText) != null || intentParser.isCheapestChoice(rawText) || intentParser.isAffirmative(rawText) || intentParser.isNegative(rawText)) {
            current.state = CabBookingState.NEED_PICKUP
            return buildSessionResult(current, CabBookingState.NEED_PICKUP, CabBookingVoiceResponses.askPickup())
        }

        current.request = current.request.copy(pickupLocation = candidate)
        return advanceSession()
    }

    private fun handleDropResponse(rawText: String): CabBookingResult {
        val current = requireSession()
        val candidate = intentParser.extractDropLocation(rawText)
        if (candidate.isNullOrBlank() || intentParser.extractRideType(rawText) != null || intentParser.parseProviderChoice(rawText) != null || intentParser.isCheapestChoice(rawText) || intentParser.isAffirmative(rawText) || intentParser.isNegative(rawText)) {
            current.state = CabBookingState.NEED_DROP
            return buildSessionResult(current, CabBookingState.NEED_DROP, CabBookingVoiceResponses.askDrop())
        }

        current.request = current.request.copy(dropLocation = candidate)
        return advanceSession()
    }

    private fun handleRideTypeResponse(rawText: String): CabBookingResult {
        val current = requireSession()
        val rideType = intentParser.extractRideType(rawText)
        if (rideType == null) {
            current.state = CabBookingState.NEED_RIDE_TYPE
            return buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }

        current.request = current.request.copy(rideType = rideType)
        return collectAndCompareFares()
    }

    private fun collectAndCompareFares(): CabBookingResult {
        val current = requireSession()
        current.state = CabBookingState.OPENING_PROVIDERS
        current.availableProviders.clear()
        current.availableProviders.addAll(providerRegistry.installedProviders())
        current.skippedProviders.clear()

        providerRegistry.missingProviders().forEach { provider ->
            current.skippedProviders[provider] = "app is not installed"
        }

        if (current.availableProviders.isEmpty()) {
            current.state = CabBookingState.FAILED
            val message = CabBookingVoiceResponses.noProvidersAvailable(current.skippedProviders)
            val result = buildSessionResult(current, CabBookingState.FAILED, message)
            clearSession()
            return result
        }

        val collectedOptions = mutableListOf<CabFareOption>()
        current.availableProviders.forEach { provider ->
            val launchIntent = deepLinkBuilder.buildLaunchIntent(provider, current.request)
            if (launchIntent == null) {
                current.skippedProviders[provider] = "no launch intent available"
                return@forEach
            }

            val launched = runCatching { providerLauncher.launch(launchIntent) }.getOrDefault(false)
            if (!launched) {
                current.skippedProviders[provider] = "could not launch the app"
                return@forEach
            }

            val manualReason = accessibilityService.detectManualActionRequired()
            if (manualReason != null) {
                return manualAction(current, manualReason)
            }

            accessibilityService.fillTripDetails(current.request)

            val postFillManualReason = accessibilityService.detectManualActionRequired()
            if (postFillManualReason != null) {
                return manualAction(current, postFillManualReason)
            }

            val option = accessibilityService.collectFareOption(provider, current.request)
            if (option == null) {
                current.skippedProviders[provider] = "fare was not visible"
            } else {
                collectedOptions.add(option.copy(packageName = providerRegistry.packageName(provider)))
            }
        }

        if (collectedOptions.isEmpty()) {
            current.state = CabBookingState.FAILED
            val message = CabBookingVoiceResponses.noProvidersAvailable(current.skippedProviders)
            val result = buildSessionResult(current, CabBookingState.FAILED, message)
            clearSession()
            return result
        }

        val sortedOptions = fareComparator.sortLowestToHighest(collectedOptions)
        current.fareOptions.clear()
        current.fareOptions.addAll(sortedOptions)
        current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE

        return buildSessionResult(
            current,
            CabBookingState.SHOWING_COMPARISON,
            CabBookingVoiceResponses.showComparison(sortedOptions, current.skippedProviders)
        )
    }

    private fun handlePlatformChoice(rawText: String): CabBookingResult {
        val current = requireSession()
        val selectedOption = when {
            intentParser.isCheapestChoice(rawText) -> current.fareOptions.firstOrNull()
            else -> {
                val provider = intentParser.parseProviderChoice(rawText) ?: current.request.preferredProvider
                provider?.let { providerChoice ->
                    current.fareOptions.firstOrNull { it.provider == providerChoice }
                }
            }
        }

        if (selectedOption == null) {
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
        }

        current.selectedOption = selectedOption
        current.request = current.request.copy(
            preferredProvider = selectedOption.provider,
            finalUserConfirmed = false
        )

        val launchIntent = deepLinkBuilder.buildLaunchIntent(
            selectedOption.provider,
            current.request,
            selectedOption
        )
        if (launchIntent == null) {
            current.skippedProviders[selectedOption.provider] = "no launch intent available"
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
        }

        val launched = runCatching { providerLauncher.launch(launchIntent) }.getOrDefault(false)
        if (!launched) {
            current.skippedProviders[selectedOption.provider] = "could not launch the app"
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
        }

        val manualReason = accessibilityService.detectManualActionRequired()
        if (manualReason != null) {
            return manualAction(current, manualReason)
        }

        accessibilityService.fillTripDetails(current.request)

        val postFillManualReason = accessibilityService.detectManualActionRequired()
        if (postFillManualReason != null) {
            return manualAction(current, postFillManualReason)
        }

        current.state = CabBookingState.WAITING_FOR_FINAL_CONFIRMATION
        return buildSessionResult(
            current,
            CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
            CabBookingVoiceResponses.askFinalConfirmation(selectedOption)
        )
    }

    private fun handleFinalConfirmation(rawText: String): CabBookingResult {
        val current = requireSession()
        val selectedOption = current.selectedOption

        if (intentParser.isNegative(rawText)) {
            current.selectedOption = null
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
        }

        if (!intentParser.isAffirmative(rawText)) {
            val message = selectedOption?.let { CabBookingVoiceResponses.askFinalConfirmation(it) }
                ?: CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            return buildSessionResult(current, CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, message)
        }

        if (selectedOption == null) {
            current.state = CabBookingState.WAITING_FOR_PLATFORM_CHOICE
            return buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
        }

        current.request = current.request.copy(finalUserConfirmed = true)

        val manualReason = accessibilityService.detectManualActionRequired()
        if (manualReason != null) {
            return manualAction(current, manualReason)
        }

        current.state = CabBookingState.BOOKING
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

        val selectedOption = current.selectedOption
        val followUpMessage = when {
            selectedOption != null && intentParser.isAffirmative(rawText) -> {
                handleFinalConfirmation("yes")
            }
            selectedOption != null -> buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_FINAL_CONFIRMATION,
                CabBookingVoiceResponses.askFinalConfirmation(selectedOption)
            )
            current.fareOptions.isNotEmpty() -> buildSessionResult(
                current,
                CabBookingState.WAITING_FOR_PLATFORM_CHOICE,
                CabBookingVoiceResponses.askPlatformChoice(current.fareOptions, current.skippedProviders)
            )
            else -> buildSessionResult(current, CabBookingState.NEED_RIDE_TYPE, CabBookingVoiceResponses.askRideType())
        }
        return followUpMessage
    }

    private fun manualAction(current: CabBookingSession, reason: String): CabBookingResult {
        current.state = CabBookingState.MANUAL_ACTION_REQUIRED
        current.manualActionReason = reason
        return buildSessionResult(
            current,
            CabBookingState.MANUAL_ACTION_REQUIRED,
            CabBookingVoiceResponses.manualActionRequired(reason),
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
            availableProviders = current.availableProviders.toList(),
            skippedProviders = current.skippedProviders.toMap(),
            manualActionRequired = manualActionRequired,
            manualActionReason = manualActionReason,
            finalUserConfirmed = finalUserConfirmed
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
