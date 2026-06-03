package com.nova.luna.cab

import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import java.util.Locale

data class CabScreenSnapshot(
    val visibleText: List<String>,
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

            CabScreenSnapshot(
                visibleText = visibleText,
                visibleFareText = visibleFareText,
                finalFareText = finalFareText,
                etaText = etaText,
                rideTypeText = rideTypeText,
                couponText = couponText,
                discountText = discountText,
                manualActionReason = manualActionReason
            )
        } finally {
            root.recycle()
        }
    }

    open fun collectFareOption(provider: CabProvider, request: CabBookingRequest): CabFareOption? {
        val snapshot = captureScreenSnapshot() ?: return null
        if (snapshot.manualActionReason != null) return null

        val visibleFareText = snapshot.visibleFareText ?: snapshot.finalFareText
        val finalFareText = snapshot.finalFareText ?: snapshot.visibleFareText
        val option = CabFareOption(
            provider = provider,
            rideType = request.rideType ?: RideType.ANY,
            visibleFareText = visibleFareText,
            visibleFareAmount = fareComparator.extractFareAmount(visibleFareText),
            finalFareText = finalFareText,
            finalFareAmount = fareComparator.extractFareAmount(finalFareText),
            etaText = snapshot.etaText,
            etaMinutes = fareComparator.extractEtaMinutes(snapshot.etaText),
            couponText = snapshot.couponText,
            discountText = snapshot.discountText
        )

        return if (
            option.visibleFareText.isNullOrBlank() &&
            option.finalFareText.isNullOrBlank() &&
            option.etaText.isNullOrBlank() &&
            option.couponText.isNullOrBlank() &&
            option.discountText.isNullOrBlank()
        ) {
            null
        } else {
            option
        }
    }

    open fun detectManualActionRequired(snapshot: CabScreenSnapshot? = captureScreenSnapshot()): String? {
        return snapshot?.manualActionReason
    }

    open fun fillTripDetails(request: CabBookingRequest): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        var performedAction = false

        request.pickupLocation?.takeIf { it.isNotBlank() }?.let { pickup ->
            if (tapSafeField("pickup") || tapSafeField("from") || tapSafeField("pickup location")) {
                performedAction = service.typeText(pickup) || performedAction
            }
        }

        request.dropLocation?.takeIf { it.isNotBlank() }?.let { drop ->
            if (tapSafeField("drop") || tapSafeField("destination") || tapSafeField("where to")) {
                performedAction = service.typeText(drop) || performedAction
            }
        }

        request.rideType?.takeIf { it != RideType.ANY }?.let { rideType ->
            performedAction = tapSafeField(rideType.displayName()) || performedAction
        }

        return performedAction
    }

    open fun tapSafeField(query: String): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        return service.clickByTextOrDescription(query)
    }

    open fun tapSafeButton(query: String, finalUserConfirmed: Boolean = false): Boolean {
        val normalized = query.lowercase(Locale.US)
        val finalActionKeywords = listOf("book", "confirm", "pay", "request", "complete")
        if (!finalUserConfirmed && finalActionKeywords.any { normalized.contains(it) }) {
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
                return true
            }
        }

        return false
    }

    open fun tapFinalConfirmButton(finalUserConfirmed: Boolean): Boolean {
        if (!finalUserConfirmed) return false

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
        return texts.firstOrNull { fareComparator.extractFareAmount(it) != null }
    }

    private fun findFinalFareText(texts: List<String>): String? {
        return texts.firstOrNull {
            val normalized = it.lowercase(Locale.US)
            (normalized.contains("discount") || normalized.contains("coupon") || normalized.contains("after") || normalized.contains("now")) &&
                fareComparator.extractFareAmount(it) != null
        }
    }

    private fun findEtaText(texts: List<String>): String? {
        return texts.firstOrNull { fareComparator.extractEtaMinutes(it) != null }
    }

    private fun findRideTypeText(texts: List<String>): String? {
        return texts.firstOrNull {
            val normalized = it.lowercase(Locale.US)
            normalized.contains("auto") ||
                normalized.contains("bike") ||
                normalized.contains("mini") ||
                normalized.contains("sedan") ||
                normalized.contains("suv")
        }
    }

    private fun findCouponText(texts: List<String>): String? {
        return texts.firstOrNull {
            val normalized = it.lowercase(Locale.US)
            normalized.contains("coupon") ||
                normalized.contains("promo") ||
                normalized.contains("offer") ||
                normalized.contains("save")
        }
    }

    private fun findDiscountText(texts: List<String>): String? {
        return texts.firstOrNull {
            val normalized = it.lowercase(Locale.US)
            normalized.contains("discount") ||
                normalized.contains("discounted") ||
                normalized.contains("cashback")
        }
    }

    private fun detectManualActionReason(texts: List<String>): String? {
        val normalized = texts.joinToString(separator = " ").lowercase(Locale.US)
        val reasons = linkedMapOf(
            "otp" to "OTP",
            "one time password" to "OTP",
            "captcha" to "captcha",
            "password" to "password",
            "login" to "login",
            "sign in" to "sign-in",
            "payment" to "payment",
            "pay now" to "payment",
            "upi" to "UPI",
            "verification" to "verification",
            "secure" to "secure screen",
            "permission" to "permission"
        )

        return reasons.entries.firstOrNull { (needle, _) -> normalized.contains(needle) }?.value
    }
}
