package com.nova.luna.cab

import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import java.util.Locale

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
    val manualActionReason: String? = null
)

open class CabAccessibilityService(
    private val fareComparator: CabFareComparator = CabFareComparator()
) {
    open fun captureScreenSnapshot(): CabScreenSnapshot? {
        val service = NovaAccessibilityService.instance ?: return null
        val root = service.rootInActiveWindow ?: return null

        return try {
            val visibleText = collectVisibleText(root)
            val visibleFareText = findFareText(visibleText)
            val finalFareText = findFinalFareText(visibleText) ?: visibleFareText
            val etaText = findEtaText(visibleText)
            val rideTypeText = findRideTypeText(visibleText)
            val couponText = findCouponText(visibleText)
            val discountText = findDiscountText(visibleText)
            val manualActionReason = detectManualActionReason(visibleText)
            val sourceText = visibleText.joinToString(separator = " | ")
            val sourcePackageName = root.packageName?.toString()

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
                manualActionReason = manualActionReason
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
                    "visibleTextCount" to visibleText.size
                )
            )

            snapshot
        } finally {
            root.recycle()
        }
    }

    open fun collectFareOption(
        provider: CabProvider,
        request: CabBookingRequest,
        snapshot: CabScreenSnapshot? = captureScreenSnapshot()
    ): CabFareOption? {
        val currentSnapshot = snapshot ?: return null
        if (currentSnapshot.manualActionReason != null) {
            CabLogger.d(
                "fare_collection_manual_action",
                mapOf(
                    "provider" to provider.name,
                    "reason" to currentSnapshot.manualActionReason
                )
            )
            return null
        }

        val visibleFareText = currentSnapshot.visibleFareText ?: currentSnapshot.finalFareText
        val finalFareText = currentSnapshot.finalFareText ?: currentSnapshot.visibleFareText
        val visibleRawText = currentSnapshot.sourceText

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
            normalizedOption
        } else {
            CabLogger.w(
                "fare_unavailable",
                mapOf(
                    "provider" to provider.name,
                    "sourceText" to visibleRawText
                )
            )
            null
        }
    }

    open fun detectManualActionRequired(snapshot: CabScreenSnapshot? = captureScreenSnapshot()): String? {
        val reason = snapshot?.manualActionReason
        if (reason != null) {
            CabLogger.d(
                "manual_action_detected",
                mapOf(
                    "reason" to reason,
                    "packageName" to snapshot.sourcePackageName
                )
            )
        }
        return reason
    }

    open fun fillTripDetails(
        request: CabBookingRequest,
        snapshot: CabScreenSnapshot? = captureScreenSnapshot()
    ): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        var performedAction = false
        val safeSnapshot = snapshot

        request.pickupLocation?.takeIf { it.isNotBlank() }?.let { pickup ->
            val pickupField = listOf(
                "pickup location",
                "pickup",
                "from where",
                "from",
                "enter pickup",
                "set pickup"
            ).firstOrNull { tapSafeField(it) }

            if (pickupField != null) {
                performedAction = service.typeText(pickup) || performedAction
            }
        }

        request.dropLocation?.takeIf { it.isNotBlank() }?.let { drop ->
            val dropField = listOf(
                "drop location",
                "destination",
                "where to",
                "drop",
                "enter drop",
                "drop off"
            ).firstOrNull { tapSafeField(it) }

            if (dropField != null) {
                performedAction = service.typeText(drop) || performedAction
            }
        }

        request.rideType?.takeIf { it != RideType.ANY }?.let { rideType ->
            performedAction = listOf(
                rideType.displayName(),
                rideType.displayName().lowercase(Locale.US)
            ).any { tapSafeField(it) } || performedAction
        }

        CabLogger.d(
            "fill_trip_details",
            mapOf(
                "pickup" to request.pickupLocation,
                "drop" to request.dropLocation,
                "rideType" to request.rideType?.name,
                "performedAction" to performedAction,
                "sourceText" to safeSnapshot?.sourceText
            )
        )

        return performedAction
    }

    open fun tapSafeField(query: String): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        return service.clickByTextOrDescription(query)
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

        val service = NovaAccessibilityService.instance ?: return false
        val candidates = listOf(
            query,
            "$query now",
            "confirm booking",
            "book now",
            "book ride",
            "request now",
            "continue"
        ).distinct()

        for (candidate in candidates) {
            if (service.clickByTextOrDescription(candidate)) {
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

    open fun tapFinalConfirmButton(finalUserConfirmed: Boolean): Boolean {
        if (!finalUserConfirmed) {
            CabLogger.d("tap_final_confirm_blocked", mapOf("finalUserConfirmed" to false))
            return false
        }

        return listOf(
            "confirm booking",
            "book now",
            "book",
            "confirm",
            "request now",
            "pay now"
        ).any { tapSafeButton(it, finalUserConfirmed = true) }
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
                "promo"
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
            "sign in" to "sign-in",
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
            "location disabled" to "location disabled",
            "location off" to "location disabled",
            "update app" to "app update required",
            "app update" to "app update required",
            "secure" to "secure or unreadable screen",
            "unavailable" to "provider unavailable",
            "not available" to "provider unavailable"
        )

        return patterns.firstOrNull { (needle, _) -> normalized.contains(needle) }?.second
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
}
