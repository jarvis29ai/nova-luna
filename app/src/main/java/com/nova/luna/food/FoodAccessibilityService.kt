package com.nova.luna.food

import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.AccessibilityNodeUtils
import java.util.Locale

open class FoodAccessibilityService(
    private val priceComparator: FoodPriceComparator = FoodPriceComparator(),
    private val couponEngine: FoodCouponEngine = FoodCouponEngine(priceComparator)
) {
    open fun captureScreenSnapshot(): FoodCartSnapshot? {
        val service = NovaAccessibilityService.instance ?: return null
        val root = service.rootInActiveWindow ?: return null
        val screenPackageName = root.packageName?.toString()
        val ownPackageName = service.packageName

        return try {
            if (!screenPackageName.isNullOrBlank() && screenPackageName == ownPackageName) {
                return null
            }

            val visibleText = collectVisibleText(root)
            val visiblePriceText = findVisiblePriceText(visibleText)
            val finalPayableText = findFinalPayableText(visibleText) ?: visiblePriceText
            val deliveryFeeText = findDeliveryFeeText(visibleText)
            val taxText = findTaxText(visibleText)
            val couponText = findCouponText(visibleText)
            val discountText = findDiscountText(visibleText)
            val etaText = findEtaText(visibleText)
            val manualActionReason = detectManualActionReason(visibleText)

            FoodCartSnapshot(
                visibleText = visibleText,
                screenPackageName = screenPackageName,
                foodItemText = findFoodItemText(visibleText),
                restaurantText = findRestaurantText(visibleText),
                visiblePriceText = visiblePriceText,
                finalPayableText = finalPayableText,
                deliveryFeeText = deliveryFeeText,
                taxText = taxText,
                couponText = couponText,
                discountText = discountText,
                etaText = etaText,
                manualActionReason = manualActionReason
            )
        } finally {
            root.recycle()
        }
    }

    open fun awaitProviderForeground(
        expectedPackageName: String?,
        retries: Int = 4,
        pollDelayMs: Long = 120L
    ): Boolean {
        val attempts = retries.coerceAtLeast(1)
        val ownPackageName = NovaAccessibilityService.instance?.packageName

        repeat(attempts) { index ->
            val snapshot = captureScreenSnapshot()
            val screenPackageName = snapshot?.screenPackageName?.trim().orEmpty()
            val ready = screenPackageName.isNotBlank() &&
                screenPackageName != ownPackageName &&
                (expectedPackageName.isNullOrBlank() || screenPackageName.equals(expectedPackageName, ignoreCase = true))

            if (ready) {
                return true
            }

            if (index < attempts - 1 && pollDelayMs > 0) {
                SystemClock.sleep(pollDelayMs)
            }
        }

        return false
    }

    open fun detectManualActionRequired(snapshot: FoodCartSnapshot? = captureScreenSnapshot()): String? {
        return snapshot?.manualActionReason?.displayName()
    }

    open fun fillOrderDetails(target: FoodSearchTarget): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        var performedAction = false
        val snapshot = captureScreenSnapshot()
        val onCheckoutScreen = isCartOrCheckoutScreen(snapshot?.visibleText.orEmpty())

        if (!onCheckoutScreen) {
            performedAction = tapSearchField("search") || performedAction
            performedAction = tapSearchField("search restaurant") || performedAction
            performedAction = tapSearchField("search food") || performedAction
            performedAction = tapSearchField("find dishes") || performedAction

            val searchQuery = target.searchQuery()
            if (searchQuery.isNotBlank()) {
                performedAction = service.typeText(searchQuery) || performedAction
            }

            target.restaurantName?.takeIf { it.isNotBlank() }?.let { restaurant ->
                performedAction = tapCandidate(restaurant) || performedAction
            }

            target.foodItem.takeIf { it.isNotBlank() }?.let { item ->
                performedAction = tapCandidate(item) || performedAction
            }
        }

        if (target.quantity > 1) {
            performedAction = adjustQuantity(target.quantity) || performedAction
        }

        performedAction = tapCartOrCheckout() || performedAction
        return performedAction
    }

    open fun tryApplyCoupon(
        target: FoodSearchTarget,
        couponPreference: String? = null
    ): FoodCouponCandidate? {
        val snapshot = captureScreenSnapshot() ?: return null
        if (snapshot.manualActionReason != null) return null

        val candidates = couponEngine.extractCouponCandidates(snapshot.visibleText)
        val selected = couponEngine.selectBestCoupon(
            candidates = candidates,
            preferredCode = couponPreference ?: target.couponPreference
        ) ?: return null

        val service = NovaAccessibilityService.instance ?: return selected
        val attempted = buildList {
            add("coupon")
            add("offers")
            add("promo")
            add("apply coupon")
            add("view coupons")
            add("apply")
            selected.code?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.any { query -> service.clickByTextOrDescription(query) }

        if (selected.code?.isNotBlank() == true) {
            val editable = service.rootInActiveWindow?.let { root ->
                AccessibilityNodeUtils.findEditableNode(root)
            }
            if (editable != null) {
                val typed = service.typeText(selected.code)
                return selected.copy(applied = attempted || typed)
            }
        }

        return selected.copy(applied = attempted)
    }

    open fun collectPlatformQuote(provider: FoodProvider, target: FoodSearchTarget): FoodPlatformQuote? {
        val snapshot = captureScreenSnapshot() ?: return null
        if (snapshot.manualActionReason != null) return null

        val couponCandidates = couponEngine.extractCouponCandidates(snapshot.visibleText)
        val selectedCoupon = couponEngine.selectBestCoupon(couponCandidates, target.couponPreference)

        val quote = FoodPlatformQuote(
            provider = provider,
            foodItem = target.foodItem,
            restaurantName = snapshot.restaurantText ?: target.restaurantName,
            quantity = target.quantity,
            visiblePriceText = snapshot.visiblePriceText,
            visiblePriceAmount = priceComparator.extractAmount(snapshot.visiblePriceText),
            finalPayableText = snapshot.finalPayableText ?: snapshot.visiblePriceText,
            finalPayableAmount = priceComparator.extractAmount(snapshot.finalPayableText ?: snapshot.visiblePriceText),
            deliveryFeeText = snapshot.deliveryFeeText,
            deliveryFeeAmount = priceComparator.extractAmount(snapshot.deliveryFeeText),
            taxText = snapshot.taxText,
            taxAmount = priceComparator.extractAmount(snapshot.taxText),
            couponText = snapshot.couponText ?: selectedCoupon?.savingsText,
            discountText = snapshot.discountText,
            discountAmount = priceComparator.extractAmount(snapshot.discountText),
            etaText = snapshot.etaText,
            etaMinutes = priceComparator.extractEtaMinutes(snapshot.etaText),
            selectedCoupon = selectedCoupon,
            packageName = snapshot.screenPackageName
        )

        if (
            quote.visiblePriceText.isNullOrBlank() &&
            quote.finalPayableText.isNullOrBlank() &&
            quote.deliveryFeeText.isNullOrBlank() &&
            quote.taxText.isNullOrBlank() &&
            quote.couponText.isNullOrBlank() &&
            quote.discountText.isNullOrBlank() &&
            quote.etaText.isNullOrBlank()
        ) {
            return null
        }

        return quote
    }

    open fun tapFinalPlaceOrderButton(finalUserConfirmed: Boolean): Boolean {
        if (!finalUserConfirmed) return false

        return listOf(
            "place order",
            "confirm order",
            "order now",
            "place your order",
            "submit order",
            "final place order"
        ).any { query -> tapSafeField(query) }
    }

    open fun tapSafeButton(query: String, finalUserConfirmed: Boolean = false): Boolean {
        val normalized = query.lowercase(Locale.US)
        val blockedPaymentKeywords = listOf("pay now", "payment", "upi", "card", "cvv", "wallet")
        if (blockedPaymentKeywords.any { keyword -> normalized.contains(keyword) }) {
            return false
        }

        val finalActionKeywords = listOf("place order", "confirm order", "order now")
        if (!finalUserConfirmed && finalActionKeywords.any { keyword -> normalized.contains(keyword) }) {
            return false
        }

        val service = NovaAccessibilityService.instance ?: return false
        val candidates = listOf(
            query,
            "$query now",
            "$query item",
            "add to cart",
            "add",
            "cart",
            "checkout",
            "continue",
            "view cart"
        ).distinct()

        return candidates.any { candidate -> service.clickByTextOrDescription(candidate) }
    }

    open fun tapSafeField(query: String): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        return service.clickByTextOrDescription(query)
    }

    private fun tapSearchField(query: String): Boolean {
        return listOf(
            query,
            "search",
            "search restaurant",
            "search food",
            "find dishes",
            "find restaurants",
            "restaurants",
            "dishes"
        ).any { tapSafeField(it) }
    }

    private fun tapCandidate(query: String): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        return listOf(
            query,
            "$query restaurant",
            "$query food",
            "$query item",
            "$query now"
        ).any { candidate -> service.clickByTextOrDescription(candidate) }
    }

    private fun tapCartOrCheckout(): Boolean {
        return listOf(
            "view cart",
            "cart",
            "checkout",
            "go to cart",
            "basket",
            "bag"
        ).any { tapSafeButton(it) }
    }

    private fun adjustQuantity(quantity: Int): Boolean {
        if (quantity <= 1) return true

        var performedAction = false
        repeat(quantity - 1) {
            performedAction = listOf(
                "+",
                "plus",
                "increase",
                "add one",
                "more",
                "qty +",
                "quantity plus"
            ).any { query -> tapSafeButton(query) } || performedAction
        }

        return performedAction
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): List<String> {
        val text = mutableListOf<String>()
        collectVisibleText(root, text)
        return text.distinct()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.hintText?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.stateDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectVisibleText(child, out)
        }
    }

    private fun findVisiblePriceText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            priceComparator.extractAmount(text) != null &&
                !normalized.contains("delivery") &&
                !normalized.contains("tax") &&
                !normalized.contains("discount") &&
                !normalized.contains("coupon")
        }
    }

    private fun findFinalPayableText(texts: List<String>): String? {
        val priorities = listOf(
            "grand total",
            "amount to pay",
            "final payable",
            "payable",
            "to pay",
            "total",
            "subtotal"
        )

        priorities.forEach { marker ->
            texts.firstOrNull { text ->
                val normalized = text.lowercase(Locale.US)
                priceComparator.extractAmount(text) != null && containsTextMarker(normalized, marker)
            }?.let { return it }
        }

        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            priceComparator.extractAmount(text) != null &&
                (containsTextMarker(normalized, "final") || containsTextMarker(normalized, "payable"))
        }
    }

    private fun findDeliveryFeeText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("delivery fee") ||
                normalized.contains("delivery charges") ||
                normalized.contains("delivery charge") ||
                normalized.contains("delivery") && priceComparator.extractAmount(text) != null
        }
    }

    private fun findTaxText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("tax") ||
                normalized.contains("gst") ||
                normalized.contains("service charge")
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
                normalized.contains("cashback") ||
                normalized.contains("savings")
        }
    }

    private fun findEtaText(texts: List<String>): String? {
        return texts.firstOrNull { text -> priceComparator.extractEtaMinutes(text) != null }
    }

    private fun findFoodItemText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            text.length <= 60 &&
                priceComparator.extractAmount(text) == null &&
                !normalized.contains("coupon") &&
                !normalized.contains("payment") &&
                !normalized.contains("login") &&
                !normalized.contains("otp") &&
                !normalized.contains("captcha") &&
                !normalized.contains("cart") &&
                !normalized.contains("checkout") &&
                !normalized.contains("basket") &&
                !normalized.contains("bag") &&
                !normalized.contains("total") &&
                !normalized.contains("delivery") &&
                !normalized.contains("search") &&
                !normalized.contains("restaurant")
        }
    }

    private fun findRestaurantText(texts: List<String>): String? {
        return texts.firstOrNull { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("restaurant") ||
                normalized.contains("cafe") ||
                normalized.contains("cafe") ||
                normalized.contains("kitchen") ||
                normalized.contains("eatery") ||
                normalized.contains("bistro")
        }
    }

    private fun detectManualActionReason(texts: List<String>): FoodManualActionReason? {
        val normalized = normalizeForDetection(texts.joinToString(separator = " "))
        val reasons = linkedMapOf(
            "one time password" to FoodManualActionReason.OTP,
            "one-time password" to FoodManualActionReason.OTP,
            "otp" to FoodManualActionReason.OTP,
            "login" to FoodManualActionReason.LOGIN,
            "log in" to FoodManualActionReason.LOGIN,
            "log in to continue" to FoodManualActionReason.LOGIN,
            "sign in" to FoodManualActionReason.LOGIN,
            "sign-in" to FoodManualActionReason.LOGIN,
            "signin" to FoodManualActionReason.LOGIN,
            "password" to FoodManualActionReason.PASSWORD,
            "captcha" to FoodManualActionReason.CAPTCHA,
            "payment" to FoodManualActionReason.PAYMENT,
            "pay now" to FoodManualActionReason.PAYMENT,
            "payment method" to FoodManualActionReason.PAYMENT,
            "payment options" to FoodManualActionReason.PAYMENT,
            "select payment" to FoodManualActionReason.PAYMENT,
            "upi" to FoodManualActionReason.UPI,
            "card" to FoodManualActionReason.PAYMENT,
            "debit card" to FoodManualActionReason.PAYMENT,
            "credit card" to FoodManualActionReason.PAYMENT,
            "cvv" to FoodManualActionReason.PAYMENT,
            "verification" to FoodManualActionReason.VERIFICATION,
            "verification required" to FoodManualActionReason.VERIFICATION,
            "human verification" to FoodManualActionReason.VERIFICATION,
            "manual action" to FoodManualActionReason.MANUAL_CONFIRMATION,
            "manual confirmation" to FoodManualActionReason.MANUAL_CONFIRMATION,
            "permission" to FoodManualActionReason.PERMISSION
        )

        return reasons.entries.firstOrNull { (needle, _) -> normalized.contains(needle) }?.value
    }

    private fun normalizeForDetection(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsTextMarker(normalized: String, marker: String): Boolean {
        val trimmed = marker.trim().lowercase(Locale.US)
        if (trimmed.isBlank()) return false

        return if (trimmed.contains(" ")) {
            normalized.contains(trimmed)
        } else {
            Regex("""\b${Regex.escape(trimmed)}\b""").containsMatchIn(normalized)
        }
    }

    private fun isCartOrCheckoutScreen(texts: List<String>): Boolean {
        val normalized = normalizeForDetection(texts.joinToString(separator = " "))
        return listOf(
            "checkout",
            "order summary",
            "review order",
            "view cart",
            "go to cart",
            "cart",
            "basket",
            "bag",
            "place order",
            "bill summary"
        ).any { normalized.contains(it) }
    }
}
