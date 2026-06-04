package com.nova.luna.cab

import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.AccessibilityNodeUtils
import com.nova.luna.util.FuzzyMatcher
import java.util.Locale

data class CabFareCandidate(
    val rawText: String,
    val amount: Long?,
    val etaMinutes: Int? = null,
    val isLikelyFinal: Boolean = false
)

data class CabScreenSnapshot(
    val visibleText: List<String>,
    val sourceText: String? = null,
    val sourcePackageName: String? = null,
    val visibleFareText: String? = null,
    val finalFareText: String? = null,
    val etaText: String? = null,
    val rideTypeText: String? = null,
    val couponText: String? = null,
    val discountText: String? = null,
    val manualActionReason: String? = null,
    val fareCandidates: List<CabFareCandidate> = emptyList()
)

open class CabAccessibilityService(
    private val fareComparator: CabFareComparator = CabFareComparator()
) {
    private val providerPackages = setOf(
        CabProviderRegistry.UBER_PACKAGE_NAME,
        CabProviderRegistry.OLA_PACKAGE_NAME,
        CabProviderRegistry.RAPIDO_PACKAGE_NAME,
        CabProviderRegistry.INDRIVE_PACKAGE_NAME,
        CabProviderRegistry.INDRIVE_PACKAGE_NAME_LOWER,
        CabProviderRegistry.INDRIVE_PACKAGE_NAME_FALLBACK
    )

    private val knownPaymentOrLoginPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel",
        "net.one97.paytm",
        "com.phonepe.app",
        "com.freecharge.android",
        "com.mobikwik_new",
        "in.org.npci.upiapp",
        "com.android.chrome",
        "com.google.android.webview"
    )

    open fun captureScreenText(): String? {
        return captureScreenSnapshot()?.sourceText
    }

    open fun currentForegroundPackageName(): String? {
        val service = NovaAccessibilityService.instance ?: return null
        val root = service.rootInActiveWindow ?: return null

        return try {
            root.packageName?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } finally {
            root.recycle()
        }
    }

    open fun waitForForegroundPackage(
        expectedPackageNames: Set<String>,
        attempts: Int = 10,
        totalWaitMs: Long = 5000L
    ): String? {
        if (expectedPackageNames.isEmpty() || attempts <= 0) return null

        val delayMs = (totalWaitMs / attempts).coerceAtLeast(100L)
        repeat(attempts) { attempt ->
            val currentPackage = currentForegroundPackageName()
            if (!currentPackage.isNullOrBlank() && expectedPackageNames.contains(currentPackage)) {
                CabLogger.d(
                    "foreground_package_matched",
                    mapOf(
                        "packageName" to currentPackage,
                        "attempt" to (attempt + 1).toString()
                    )
                )
                return currentPackage
            }

            if (attempt < attempts - 1) {
                runCatching { Thread.sleep(delayMs) }
            }
        }

        CabLogger.w(
            "foreground_package_timeout",
            mapOf(
                "expectedPackages" to expectedPackageNames.joinToString(separator = ",")
            )
        )
        return null
    }

    open fun captureScreenSnapshot(): CabScreenSnapshot? {
        val service = NovaAccessibilityService.instance ?: return null
        val root = service.rootInActiveWindow ?: return null

        return try {
            val visibleText = collectVisibleText(root)
            val sourceText = visibleText.joinToString(separator = " | ")
            val fareCandidates = extractFareCandidates(sourceText)
            val visibleFareText = findFareText(visibleText) ?: fareCandidates.firstOrNull()?.rawText
            val finalFareText = findFinalFareText(visibleText)
                ?: fareCandidates.firstOrNull { it.isLikelyFinal }?.rawText
                ?: visibleFareText
            val etaText = findEtaText(visibleText)
            val rideTypeText = findRideTypeText(visibleText)
            val couponText = findCouponText(visibleText)
            val discountText = findDiscountText(visibleText)
            val sourcePackageName = root.packageName?.toString()
            val manualActionReason = if (isInspectableCabPackage(sourcePackageName)) {
                detectManualActionReason(visibleText)
            } else {
                null
            }

            val snapshot = CabScreenSnapshot(
                visibleText = visibleText,
                sourceText = sourceText,
                sourcePackageName = sourcePackageName,
                visibleFareText = visibleFareText,
                finalFareText = finalFareText,
                etaText = etaText,
                rideTypeText = rideTypeText,
                couponText = couponText,
                discountText = discountText,
                manualActionReason = manualActionReason,
                fareCandidates = fareCandidates
            )

            CabLogger.d(
                "screen_snapshot",
                mapOf(
                    "packageName" to sourcePackageName,
                    "fareText" to visibleFareText,
                    "finalFareText" to finalFareText,
                    "etaText" to etaText,
                    "rideTypeText" to rideTypeText,
                    "couponText" to couponText,
                    "discountText" to discountText,
                    "manualActionReason" to manualActionReason,
                    "visibleTextCount" to visibleText.size,
                    "fareCandidateCount" to fareCandidates.size
                )
            )

            snapshot
        } finally {
            root.recycle()
        }
    }

    open fun findEditableFields(): List<String> {
        val service = NovaAccessibilityService.instance ?: return emptyList()
        val root = service.rootInActiveWindow ?: return emptyList()

        return try {
            val fields = mutableListOf<String>()
            collectEditableFields(root, fields)
            fields.distinct()
        } finally {
            root.recycle()
        }
    }

    open fun clickTextOrDescriptionAnyOf(candidates: List<String>): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            if (service.clickByTextOrDescription(candidate)) {
                CabLogger.d("accessibility_click", mapOf("query" to candidate))
                return true
            }
        }
        CabLogger.w(
            "accessibility_click_failed",
            mapOf("queries" to candidates.joinToString(separator = ","))
        )
        return false
    }

    open fun typeIntoFocusedField(text: String): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        val success = service.typeText(text)
        CabLogger.d(
            "accessibility_type",
            mapOf(
                "text" to text,
                "success" to success
            )
        )
        return success
    }

    open fun fillTripForProvider(
        provider: CabProvider,
        pickup: LocationValue?,
        drop: LocationValue?,
        rideType: RideType?
    ): CabTripFillResult {
        return when (provider) {
            CabProvider.OLA -> fillOlaTrip(pickup, drop, rideType)
            CabProvider.RAPIDO -> fillRapidoTrip(pickup, drop, rideType)
            else -> fillGenericTrip(pickup, drop, rideType)
        }
    }

    open fun fillPickupIfNeeded(pickup: LocationValue?): Boolean {
        val location = pickup?.displayText()?.takeIf { it.isNotBlank() } ?: return false
        if (pickup?.isCurrentLocation == true) {
            val clickedCurrentLocation = clickTextOrDescriptionAnyOf(buildPickupKeywords(true))
            CabLogger.d(
                "fill_pickup_current_location",
                mapOf(
                    "pickup" to location,
                    "clickedCurrentLocation" to clickedCurrentLocation,
                    "latitude" to pickup.latitude?.toString(),
                    "longitude" to pickup.longitude?.toString()
                )
            )
            return true
        }

        val keywords = buildPickupKeywords(pickup.isCurrentLocation)

        if (!clickTextOrDescriptionAnyOf(keywords)) {
            CabLogger.w(
                "fill_pickup_failed",
                mapOf("pickup" to location)
            )
            return false
        }

        val typed = typeIntoFocusedField(location)
        CabLogger.d(
            "fill_pickup_result",
            mapOf(
                "pickup" to location,
                "typed" to typed
            )
        )
        return typed
    }

    open fun fillDropIfNeeded(drop: LocationValue?): Boolean {
        val location = drop?.displayText()?.takeIf { it.isNotBlank() } ?: return false
        val keywords = buildDropKeywords()

        if (!clickTextOrDescriptionAnyOf(keywords)) {
            CabLogger.w(
                "fill_drop_failed",
                mapOf("drop" to location)
            )
            return false
        }

        val typed = typeIntoFocusedField(location)
        CabLogger.d(
            "fill_drop_result",
            mapOf(
                "drop" to location,
                "typed" to typed
            )
        )
        return typed
    }

    open fun selectRideType(rideType: RideType?): Boolean {
        if (rideType == null || rideType == RideType.ANY) {
            return true
        }

        val keywords = listOf(
            rideType.displayName(),
            rideType.displayName().lowercase(Locale.US),
            rideType.name.lowercase(Locale.US),
            when (rideType) {
                RideType.AUTO -> "auto"
                RideType.BIKE -> "bike"
                RideType.MINI -> "mini"
                RideType.SEDAN -> "sedan"
                RideType.SUV -> "suv"
                RideType.ANY -> "any"
            }
        ).distinct()

        val success = clickTextOrDescriptionAnyOf(keywords)
        CabLogger.d(
            "select_ride_type",
            mapOf(
                "rideType" to rideType.name,
                "success" to success
            )
        )
        return success
    }

    open fun detectManualAction(screenText: String?): String? {
        if (screenText.isNullOrBlank()) return null
        return detectManualActionReason(listOf(screenText))
    }

    open fun extractFareCandidates(screenText: String?): List<CabFareCandidate> {
        val text = screenText.orEmpty().trim()
        if (text.isBlank()) return emptyList()

        val segments = text
            .split(Regex("""(?:\r?\n+|\s*\|\s*|•|·)"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val candidates = mutableListOf<CabFareCandidate>()
        for (segment in segments) {
            val amount = fareComparator.extractFareAmount(segment)
            val etaMinutes = fareComparator.extractEtaMinutes(segment)
            val hasFareContext = amount != null || etaMinutes != null || looksLikeFareText(segment)
            if (!hasFareContext) continue

            val isLikelyFinal = looksLikeFinalFare(segment, amount)
            candidates.add(
                CabFareCandidate(
                    rawText = segment,
                    amount = amount,
                    etaMinutes = etaMinutes,
                    isLikelyFinal = isLikelyFinal
                )
            )
        }

        return candidates
            .distinctBy { it.rawText }
            .sortedWith(
                compareBy<CabFareCandidate> { it.amount == null }
                    .thenBy { it.amount ?: Long.MAX_VALUE }
                    .thenBy { it.etaMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.rawText.lowercase(Locale.US) }
            )
    }

    open fun collectFareOption(
        provider: CabProvider,
        request: CabBookingRequest,
        snapshot: CabScreenSnapshot? = captureScreenSnapshot(),
        attempts: Int = 20,
        totalWaitMs: Long = 10_000L
    ): CabFareCollectionResult {
        val currentSnapshot = waitForFareSnapshot(provider, snapshot, attempts, totalWaitMs)
        if (currentSnapshot == null) {
            return CabFareCollectionResult(
                failureReason = CabFailureReasons.PROVIDER_SCREEN_UNAVAILABLE
            )
        }

        val manualActionReason = detectManualActionRequired(currentSnapshot)
        if (manualActionReason != null) {
            CabLogger.d(
                "fare_collection_manual_action",
                mapOf(
                    "provider" to provider.name,
                    "reason" to manualActionReason
                )
            )
            return CabFareCollectionResult(
                failureReason = manualActionReason,
                snapshot = currentSnapshot
            )
        }

        if (!isCabProviderPackage(currentSnapshot.sourcePackageName)) {
            CabLogger.d(
                "fare_snapshot_ignored",
                mapOf(
                    "provider" to provider.name,
                    "packageName" to currentSnapshot.sourcePackageName
                )
            )
            return CabFareCollectionResult(
                failureReason = CabFailureReasons.PROVIDER_SCREEN_UNAVAILABLE,
                snapshot = currentSnapshot
            )
        }

        val visibleFareText = currentSnapshot.visibleFareText ?: currentSnapshot.fareCandidates.firstOrNull()?.rawText
        val finalFareText = currentSnapshot.finalFareText ?: currentSnapshot.fareCandidates.firstOrNull { it.isLikelyFinal }?.rawText
            ?: currentSnapshot.visibleFareText
        val visibleRawText = currentSnapshot.sourceText
        val hasFareSignal = currentSnapshot.fareCandidates.isNotEmpty() ||
            !visibleFareText.isNullOrBlank() ||
            !finalFareText.isNullOrBlank() ||
            currentSnapshot.visibleFareText != null ||
            currentSnapshot.finalFareText != null ||
            currentSnapshot.visibleFareText != null ||
            currentSnapshot.finalFareText != null

        if (!hasFareSignal) {
            CabLogger.w(
                "provider_no_fare_visible",
                mapOf(
                    "provider" to provider.name,
                    "packageName" to currentSnapshot.sourcePackageName,
                    "screenText" to visibleRawText
                )
            )
            return CabFareCollectionResult(
                failureReason = CabFailureReasons.NO_FARE_VISIBLE,
                snapshot = currentSnapshot
            )
        }

        val option = CabFareOption(
            provider = provider,
            rideType = request.rideType ?: RideType.ANY,
            visibleFareText = visibleFareText,
            visibleFareAmount = fareComparator.extractFareAmount(visibleFareText ?: visibleRawText),
            originalFareAmount = fareComparator.extractFareAmount(visibleFareText ?: visibleRawText),
            finalFareText = finalFareText,
            finalFareAmount = fareComparator.extractFareAmount(finalFareText ?: visibleRawText),
            etaText = currentSnapshot.etaText,
            etaMinutes = fareComparator.extractEtaMinutes(currentSnapshot.etaText),
            couponText = currentSnapshot.couponText,
            discountText = currentSnapshot.discountText,
            visibleRawText = visibleRawText,
            packageName = currentSnapshot.sourcePackageName
        )

        val hasFareData =
            option.visibleFareText != null ||
            option.visibleFareAmount != null ||
            option.originalFareAmount != null ||
            option.finalFareText != null ||
            option.finalFareAmount != null

        val hasVisibleData = hasFareData || option.etaText != null

        val normalizedOption = fareComparator.normalize(option)
        return if (hasVisibleData) {
            CabLogger.d(
                "fare_parsed",
                mapOf(
                    "provider" to provider.name,
                    "rideType" to normalizedOption.rideType.name,
                    "visibleFareText" to normalizedOption.visibleFareText,
                    "originalFareAmount" to normalizedOption.originalFareAmount,
                    "finalFareText" to normalizedOption.finalFareText,
                    "finalFareAmount" to normalizedOption.finalFareAmount,
                    "etaText" to normalizedOption.etaText,
                    "couponText" to normalizedOption.couponText,
                    "discountText" to normalizedOption.discountText,
                    "sourceText" to normalizedOption.visibleRawText
                )
            )
            CabFareCollectionResult(
                fareOption = normalizedOption,
                snapshot = currentSnapshot
            )
        } else {
            CabLogger.w(
                "fare_unavailable",
                mapOf(
                    "provider" to provider.name,
                    "sourceText" to visibleRawText
                )
            )
            CabFareCollectionResult(
                failureReason = CabFailureReasons.NO_FARE_VISIBLE,
                snapshot = currentSnapshot
            )
        }
    }

    open fun detectManualActionRequired(snapshot: CabScreenSnapshot? = captureScreenSnapshot()): String? {
        val sourcePackageName = snapshot?.sourcePackageName
        if (!isInspectableCabPackage(sourcePackageName)) {
            if (!sourcePackageName.isNullOrBlank()) {
                CabLogger.d(
                    "manual_action_ignored",
                    mapOf("packageName" to sourcePackageName)
                )
            }
            return null
        }

        val reason = snapshot?.manualActionReason ?: detectManualAction(snapshot?.sourceText)
        if (reason != null) {
            CabLogger.d(
                "manual_action_detected",
                mapOf(
                    "reason" to reason,
                    "packageName" to sourcePackageName
                )
            )
        }
        return reason
    }

    open fun fillTripDetails(
        request: CabBookingRequest,
        snapshot: CabScreenSnapshot? = captureScreenSnapshot()
    ): Boolean {
        val pickup = request.pickupLocation?.takeIf { it.isNotBlank() }?.let {
            LocationValue(
                rawText = it,
                isCurrentLocation = request.pickupMode == PickupMode.CURRENT_LOCATION,
                latitude = request.pickupLatitude,
                longitude = request.pickupLongitude,
                displayName = it
            )
        }
        val drop = request.dropLocation?.takeIf { it.isNotBlank() }?.let {
            LocationValue(rawText = it, displayName = it)
        }

        val pickupFilled = fillPickupIfNeeded(pickup)
        val dropFilled = fillDropIfNeeded(drop)
        val rideTypeFilled = selectRideType(request.rideType)

        CabLogger.d(
            "fill_trip_details",
            mapOf(
                "pickup" to request.pickupLocation,
                "drop" to request.dropLocation,
                "rideType" to request.rideType?.name,
                "pickupFilled" to pickupFilled,
                "dropFilled" to dropFilled,
                "rideTypeFilled" to rideTypeFilled,
                "sourceText" to snapshot?.sourceText
            )
        )

        return pickupFilled || dropFilled || rideTypeFilled
    }

    open fun tapSafeField(query: String): Boolean {
        return clickTextOrDescriptionAnyOf(listOf(query))
    }

    open fun tapSafeButton(query: String, finalUserConfirmed: Boolean = false): Boolean {
        val normalized = query.lowercase(Locale.US)
        val finalActionKeywords = listOf("book", "confirm", "pay", "request", "complete", "submit")
        if (!finalUserConfirmed && finalActionKeywords.any { normalized.contains(it) }) {
            CabLogger.d(
                "tap_safe_button_blocked",
                mapOf(
                    "query" to query,
                    "finalUserConfirmed" to finalUserConfirmed
                )
            )
            return false
        }

        val candidates = listOf(
            query,
            "$query now",
            "confirm booking",
            "book now",
            "book ride",
            "final booking",
            "request now",
            "continue"
        ).distinct()

        for (candidate in candidates) {
            if (clickTextOrDescriptionAnyOf(listOf(candidate))) {
                CabLogger.d(
                    "tap_safe_button",
                    mapOf(
                        "query" to query,
                        "candidate" to candidate,
                        "finalUserConfirmed" to finalUserConfirmed
                    )
                )
                return true
            }
        }

        CabLogger.w(
            "tap_safe_button_failed",
            mapOf(
                "query" to query,
                "finalUserConfirmed" to finalUserConfirmed
            )
        )
        return false
    }

    open fun tapFinalBookButtonOnlyIfConfirmed(finalUserConfirmed: Boolean = false): Boolean {
        if (!finalUserConfirmed) {
            CabLogger.d("tap_final_confirm_blocked", mapOf("finalUserConfirmed" to false))
            return false
        }

        val snapshot = captureScreenSnapshot()
        val manualReason = detectManualActionRequired(snapshot)
        if (manualReason != null) {
            CabLogger.d(
                "tap_final_book_blocked_manual",
                mapOf("reason" to manualReason)
            )
            return false
        }

        val keywords = listOf(
            "confirm booking",
            "book now",
            "book",
            "confirm",
            "request now",
            "request ride",
            "request",
            "final booking",
            "book ride"
        )

        return keywords.any { tapSafeButton(it, finalUserConfirmed = true) }
    }

    open fun tapFinalConfirmButton(finalUserConfirmed: Boolean): Boolean {
        return tapFinalBookButtonOnlyIfConfirmed(finalUserConfirmed)
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): List<String> {
        val text = mutableListOf<String>()
        collectVisibleText(root, text)
        return text.distinct()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectVisibleText(child, out)
        }
    }

    private fun collectEditableFields(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (node.isEditable || isEditText(node)) {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            node.className?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectEditableFields(child, out)
        }
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty().lowercase(Locale.US)
        return className.contains("edittext") || className.contains("textfield")
    }

    private fun findFareText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val amount = fareComparator.extractFareAmount(text)
            amount != null && !isSavingsOnlyText(text)
        }
    }

    private fun findFinalFareText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            val amounts = fareComparator.extractFareAmounts(text)
            val hasDiscountContext = listOf(
                "discount",
                "coupon",
                "after",
                "final",
                "now",
                "save",
                "offer",
                "promo",
                "payable"
            ).any { normalized.contains(it) }
            fareComparator.extractFareAmount(text) != null && (hasDiscountContext || amounts.size > 1)
        }
    }

    private fun findEtaText(texts: List<String>): String? {
        return texts.firstOrNull { fareComparator.extractEtaMinutes(it) != null }
    }

    private fun findRideTypeText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("auto") ||
                normalized.contains("bike") ||
                normalized.contains("mini") ||
                normalized.contains("sedan") ||
                normalized.contains("suv")
        }
    }

    private fun findCouponText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("coupon") ||
                normalized.contains("promo") ||
                normalized.contains("offer") ||
                normalized.contains("save")
        }
    }

    private fun findDiscountText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("discount") ||
                normalized.contains("discounted") ||
                normalized.contains("cashback")
        }
    }

    private fun detectManualActionReason(texts: List<String>): String? {
        val normalized = texts.joinToString(separator = " ").lowercase(Locale.US)
        val patterns = listOf(
            "one time password" to "OTP",
            "otp" to "OTP",
            "login" to "login",
            "sign in" to "login",
            "password" to "password",
            "payment" to "payment",
            "pay now" to "payment",
            "pay with" to "payment",
            "upi" to "UPI",
            "card" to "card",
            "captcha" to "captcha",
            "verify" to "verification",
            "verification" to "verification",
            "permission" to "permission",
            "allow location" to "permission",
            "location disabled" to "permission",
            "location off" to "permission",
            "update app" to "app update required",
            "app update" to "app update required",
            "secure" to "secure or unreadable screen",
            "unavailable" to "provider unavailable",
            "not available" to "provider unavailable"
        )

        return patterns.firstOrNull { (needle, _) -> normalized.contains(needle) }?.second
    }

    private fun buildPickupKeywords(isCurrentLocation: Boolean): List<String> {
        val base = listOf(
            "pickup",
            "pick up",
            "from",
            "source",
            "start",
            "origin",
            "where from"
        )
        val currentLocationKeywords = if (isCurrentLocation) {
            listOf("current location", "use current location", "my location", "here", "from here")
        } else {
            emptyList()
        }
        return (currentLocationKeywords + base).distinct()
    }

    private fun fillGenericTrip(
        pickup: LocationValue?,
        drop: LocationValue?,
        rideType: RideType?
    ): CabTripFillResult {
        val pickupFilled = fillPickupIfNeeded(pickup)
        val dropFilled = fillDropIfNeeded(drop)
        val rideTypeFilled = selectRideType(rideType)
        val canContinue = pickupFilled && dropFilled && rideTypeFilled
        return CabTripFillResult(
            filledPickup = pickupFilled,
            filledDrop = dropFilled,
            selectedRideType = rideTypeFilled,
            canContinueToFareScreen = canContinue,
            failureReason = if (canContinue) null else buildTripFailureReason(pickupFilled, dropFilled, rideTypeFilled)
        )
    }

    private fun fillRapidoTrip(
        pickup: LocationValue?,
        drop: LocationValue?,
        rideType: RideType?
    ): CabTripFillResult {
        val snapshot = captureScreenSnapshot()
        val pickupFilled = when {
            pickup?.isCurrentLocation == true -> {
                CabLogger.i(
                    "rapido_current_location_assumed",
                    mapOf(
                        "pickup" to pickup.displayText(),
                        "screenText" to snapshot?.sourceText,
                        "packageName" to snapshot?.sourcePackageName
                    )
                )
                true
            }
            else -> fillPickupIfNeeded(pickup)
        }

        val dropFilled = if (drop == null) {
            false
        } else {
            fillRapidoDestination(drop)
        }

        val rideTypeFilled = selectRapidoRideType(rideType)
        val canContinue = pickupFilled && dropFilled
        val warningReason = if (!rideTypeFilled && canContinue) "could not select ride type" else null

        return CabTripFillResult(
            filledPickup = pickupFilled,
            filledDrop = dropFilled,
            selectedRideType = rideTypeFilled,
            canContinueToFareScreen = canContinue,
            failureReason = if (canContinue) null else buildTripFailureReason(pickupFilled, dropFilled, rideTypeFilled),
            warningReason = warningReason
        )
    }

    private fun fillOlaTrip(
        pickup: LocationValue?,
        drop: LocationValue?,
        rideType: RideType?
    ): CabTripFillResult {
        val pickupFilled = fillPickupIfNeeded(pickup)
        val dropFilled = if (drop == null) {
            false
        } else {
            fillOlaDestination(drop)
        }

        val rideTypeFilled = selectRideType(rideType)
        val canContinue = pickupFilled && dropFilled
        val warningReason = if (!rideTypeFilled && canContinue) "could not select ride type" else null

        return CabTripFillResult(
            filledPickup = pickupFilled,
            filledDrop = dropFilled,
            selectedRideType = rideTypeFilled,
            canContinueToFareScreen = canContinue,
            failureReason = if (canContinue) null else buildTripFailureReason(pickupFilled, dropFilled, rideTypeFilled),
            warningReason = warningReason
        )
    }

    private fun fillRapidoDestination(drop: LocationValue): Boolean {
        val destination = drop.displayText().takeIf { it.isNotBlank() } ?: return false
        CabLogger.d(
            "rapido_destination_click_attempt",
            mapOf("destination" to destination)
        )

        val clickedDestinationField = clickTextOrDescriptionAnyOf(buildRapidoDestinationKeywords())
        if (!clickedDestinationField) {
            CabLogger.w(
                "rapido_destination_field_not_found",
                mapOf("destination" to destination)
            )
            return false
        }

        CabLogger.d(
            "rapido_destination_field_found",
            mapOf("destination" to destination)
        )
        sleep(250L)

        val typed = typeIntoFocusedField(destination)
        CabLogger.d(
            "rapido_destination_typed",
            mapOf(
                "destination" to destination,
                "typed" to typed
            )
        )
        if (!typed) {
            return false
        }

        sleep(250L)
        val suggestionClicked = clickRapidoDestinationSuggestion(destination)
        if (suggestionClicked) {
            CabLogger.d(
                "rapido_suggestion_clicked",
                mapOf("destination" to destination)
            )
        }

        val verified = destinationVisibleInSnapshot(destination)
        CabLogger.d(
            "rapido_destination_verified",
            mapOf(
                "destination" to destination,
                "verified" to verified
            )
        )
        return verified || suggestionClicked || typed
    }

    private fun fillOlaDestination(drop: LocationValue): Boolean {
        val destination = drop.displayText().takeIf { it.isNotBlank() } ?: return false
        CabLogger.d(
            "ola_destination_click_attempt",
            mapOf("destination" to destination)
        )

        val clickedDestinationField = clickTextOrDescriptionAnyOf(buildOlaDestinationKeywords())
        if (!clickedDestinationField) {
            CabLogger.w(
                "ola_destination_field_not_found",
                mapOf(
                    "destination" to destination,
                    "editableFields" to findEditableFields().joinToString(separator = ",")
                )
            )
            return false
        }

        CabLogger.d(
            "ola_destination_field_found",
            mapOf("destination" to destination)
        )
        sleep(250L)

        val typed = typeIntoFocusedField(destination)
        CabLogger.d(
            "ola_destination_typed",
            mapOf(
                "destination" to destination,
                "typed" to typed
            )
        )
        if (!typed) {
            return false
        }

        sleep(250L)
        val suggestionClicked = clickOlaDestinationSuggestion(destination)
        if (suggestionClicked) {
            CabLogger.d(
                "ola_suggestion_clicked",
                mapOf("destination" to destination)
            )
        }

        val verified = destinationVisibleInSnapshot(destination)
        CabLogger.d(
            "ola_destination_verified",
            mapOf(
                "destination" to destination,
                "verified" to verified
            )
        )
        return verified || suggestionClicked || typed
    }

    private fun selectRapidoRideType(rideType: RideType?): Boolean {
        if (rideType == null || rideType == RideType.ANY) {
            return true
        }

        val keywords = when (rideType) {
            RideType.AUTO -> listOf("Auto", "auto")
            RideType.BIKE -> listOf("Bike", "bike")
            RideType.MINI,
            RideType.SEDAN -> listOf(
                "Cab Economy",
                "Cab",
                "Car",
                rideType.displayName(),
                rideType.displayName().lowercase(Locale.US),
                rideType.name.lowercase(Locale.US)
            )
            RideType.SUV -> listOf("SUV", "suv", rideType.displayName())
            RideType.ANY -> emptyList()
        }.distinct()

        CabLogger.d(
            "rapido_ride_type_click_attempt",
            mapOf(
                "rideType" to rideType.name,
                "keywords" to keywords.joinToString(separator = ",")
            )
        )

        val success = clickTextOrDescriptionAnyOf(keywords)
        CabLogger.d(
            "rapido_ride_type_selected",
            mapOf(
                "rideType" to rideType.name,
                "success" to success
            )
        )
        if (!success) {
            CabLogger.w(
                "rapido_ride_type_not_selected",
                mapOf("rideType" to rideType.name)
            )
        }
        return success
    }

    private fun clickRapidoDestinationSuggestion(destination: String): Boolean {
        val snapshot = captureScreenSnapshot() ?: return false
        val normalizedDestination = FuzzyMatcher.normalize(destination)
        val tokens = normalizedDestination
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 1 }
            .filterNot { it in setOf("the", "and", "for", "with", "from", "to", "your") }

        val candidateTexts = snapshot.visibleText
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isGenericRapidoText(it) }
            .distinct()
            .sortedWith(
                compareByDescending<String> { text ->
                    val normalizedText = FuzzyMatcher.normalize(text)
                    when {
                        normalizedText == normalizedDestination -> 3
                        normalizedText.contains(normalizedDestination) -> 2
                        tokens.any { token -> normalizedText.contains(token) } -> 1
                        else -> 0
                    }
                }.thenBy { it.length }
            )

        if (candidateTexts.isEmpty()) {
            return false
        }

        return clickTextOrDescriptionAnyOf(candidateTexts)
    }

    private fun clickOlaDestinationSuggestion(destination: String): Boolean {
        val snapshot = captureScreenSnapshot() ?: return false
        val normalizedDestination = FuzzyMatcher.normalize(destination)
        val tokens = normalizedDestination
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 1 }
            .filterNot { it in setOf("the", "and", "for", "with", "from", "to", "your") }

        val candidateTexts = snapshot.visibleText
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isGenericOlaText(it) }
            .distinct()
            .sortedWith(
                compareByDescending<String> { text ->
                    val normalizedText = FuzzyMatcher.normalize(text)
                    when {
                        normalizedText == normalizedDestination -> 3
                        normalizedText.contains(normalizedDestination) -> 2
                        tokens.any { token -> normalizedText.contains(token) } -> 1
                        else -> 0
                    }
                }.thenBy { it.length }
            )

        if (candidateTexts.isEmpty()) {
            return false
        }

        return clickTextOrDescriptionAnyOf(candidateTexts)
    }

    private fun waitForFareSnapshot(
        provider: CabProvider,
        initialSnapshot: CabScreenSnapshot?,
        attempts: Int,
        totalWaitMs: Long
    ): CabScreenSnapshot? {
        if (provider != CabProvider.RAPIDO) {
            return initialSnapshot ?: captureScreenSnapshot()
        }

        val delayMs = (totalWaitMs / attempts.coerceAtLeast(1)).coerceAtLeast(100L)
        var latestSnapshot = initialSnapshot ?: captureScreenSnapshot()
        if (hasFareSignal(latestSnapshot)) {
            CabLogger.d(
                "rapido_fare_found",
                mapOf(
                    "provider" to provider.name,
                    "packageName" to latestSnapshot?.sourcePackageName,
                    "screenText" to latestSnapshot?.sourceText
                )
            )
            return latestSnapshot
        }

        CabLogger.d(
            "rapido_fare_wait",
            mapOf(
                "provider" to provider.name,
                "attempts" to attempts.toString(),
                "totalWaitMs" to totalWaitMs.toString(),
                "packageName" to latestSnapshot?.sourcePackageName
            )
        )

        repeat(attempts.coerceAtLeast(1)) { attempt ->
            if (attempt > 0) {
                sleep(delayMs)
            }
            latestSnapshot = captureScreenSnapshot()
            if (hasFareSignal(latestSnapshot)) {
                CabLogger.d(
                    "rapido_fare_found",
                    mapOf(
                        "provider" to provider.name,
                        "attempt" to (attempt + 1).toString(),
                        "packageName" to latestSnapshot?.sourcePackageName,
                        "screenText" to latestSnapshot?.sourceText
                    )
                )
                return latestSnapshot
            }
        }

        CabLogger.w(
            "provider_no_fare_visible",
            mapOf(
                "provider" to provider.name,
                "packageName" to latestSnapshot?.sourcePackageName,
                "screenText" to latestSnapshot?.sourceText
            )
        )
        return latestSnapshot
    }

    private fun hasFareSignal(snapshot: CabScreenSnapshot?): Boolean {
        if (snapshot == null) return false
        return snapshot.fareCandidates.isNotEmpty() ||
            !snapshot.visibleFareText.isNullOrBlank() ||
            !snapshot.finalFareText.isNullOrBlank() ||
            snapshot.visibleFareText != null ||
            snapshot.finalFareText != null
    }

    private fun buildRapidoDestinationKeywords(): List<String> {
        return listOf(
            "Where do you want to go?",
            "Where to",
            "Where to go",
            "Where are you going",
            "Enter destination",
            "Search destination",
            "Search your destination",
            "Going to",
            "Drop",
            "Drop location",
            "Drop point",
            "Destination"
        )
    }

    private fun buildOlaDestinationKeywords(): List<String> {
        return listOf(
            "Where do you want to go?",
            "Where do you want to go",
            "Where to",
            "Where to go",
            "Where are you going",
            "Search destination",
            "Search your destination",
            "Enter destination",
            "Drop",
            "Drop location",
            "Drop point",
            "Destination",
            "Go to"
        )
    }

    private fun destinationVisibleInSnapshot(destination: String): Boolean {
        val snapshot = captureScreenSnapshot() ?: return false
        val normalizedDestination = FuzzyMatcher.normalize(destination)
        val visibleTexts = buildList {
            addAll(snapshot.visibleText)
            snapshot.sourceText?.let { add(it) }
            addAll(findEditableFields())
        }

        return visibleTexts.any { text ->
            val normalizedText = FuzzyMatcher.normalize(text)
            normalizedText == normalizedDestination ||
                normalizedText.contains(normalizedDestination) ||
                normalizedDestination.contains(normalizedText)
        }
    }

    private fun isGenericRapidoText(text: String): Boolean {
        val normalized = FuzzyMatcher.normalize(text)
        return listOf(
            "home screen",
            "explore",
            "all services",
            "travel",
            "profile",
            "parcel on bike",
            "auto",
            "bike",
            "ride",
            "cab economy"
        ).any { normalized == it || normalized.contains(it) }
    }

    private fun isGenericOlaText(text: String): Boolean {
        val normalized = FuzzyMatcher.normalize(text)
        return listOf(
            "home",
            "parcel",
            "electric",
            "loans",
            "profile",
            "travel",
            "ride",
            "auto",
            "bike",
            "map",
            "maplibre",
            "all services",
            "explore"
        ).any { normalized == it || normalized.contains(it) }
    }

    private fun buildTripFailureReason(
        pickupFilled: Boolean,
        dropFilled: Boolean,
        rideTypeFilled: Boolean
    ): String {
        val parts = buildList {
            if (!pickupFilled) add(CabFailureReasons.PICKUP_FIELD_NOT_FOUND)
            if (!dropFilled) add(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND)
            if (!rideTypeFilled) add(CabFailureReasons.RIDE_TYPE_NOT_SELECTED)
        }

        return if (parts.isEmpty()) {
            CabFailureReasons.MANUAL_ACTION_REQUIRED
        } else {
            parts.joinToString(separator = " and ")
        }
    }

    open fun sleep(delayMs: Long) {
        runCatching { Thread.sleep(delayMs) }
    }

    private fun buildDropKeywords(): List<String> {
        return listOf(
            "drop",
            "destination",
            "where to",
            "to",
            "enter destination",
            "search destination",
            "drop off",
            "going to"
        )
    }

    private fun looksLikeFareText(text: String): Boolean {
        val normalized = text.lowercase(Locale.US)
        return listOf("₹", "rs", "inr", "fare", "price", "total", "payable", "coupon", "discount", "save", "eta", "min")
            .any { normalized.contains(it) }
    }

    private fun looksLikeFinalFare(text: String, amount: Long?): Boolean {
        if (amount == null) return false
        val normalized = text.lowercase(Locale.US)
        val hasDiscountContext = listOf(
            "discount",
            "coupon",
            "after",
            "final",
            "payable",
            "offer",
            "promo",
            "save"
        ).any { normalized.contains(it) }
        val amountCount = fareComparator.extractFareAmounts(text).size
        return hasDiscountContext || amountCount > 1
    }

    private fun isSavingsOnlyText(text: String): Boolean {
        val normalized = text.lowercase(Locale.US)
        if (!normalized.startsWith("save ")) return false

        val containsFareWords = listOf(
            "fare",
            "ride",
            "trip",
            "price",
            "cost",
            "pay",
            "total",
            "estimate",
            "estimated",
            "book",
            "now",
            "after"
        ).any { normalized.contains(it) }

        return !containsFareWords
    }

    private fun isCabProviderPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return providerPackages.contains(packageName)
    }

    private fun shouldInspectManualActionPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName.contains("com.nova.luna", ignoreCase = true)) return false
        return isCabProviderPackage(packageName) || knownPaymentOrLoginPackages.contains(packageName)
    }

    open fun isInspectableCabPackage(packageName: String?): Boolean {
        return shouldInspectManualActionPackage(packageName)
    }
}
