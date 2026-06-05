package com.nova.luna.grocery

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.AccessibilityNodeUtils
import com.nova.luna.util.FuzzyMatcher
import java.util.Locale

open class GroceryAccessibilityService(
    private val priceComparator: GroceryPriceComparator = GroceryPriceComparator()
) : GroceryCouponAutomation {
    private val providerPackages = setOf(
        GroceryProviderRegistry.BLINKIT_PACKAGE_NAME,
        GroceryProviderRegistry.JIOMART_PACKAGE_NAME,
        GroceryProviderRegistry.INSTAMART_PACKAGE_NAME,
        GroceryProviderRegistry.SWIGGY_PACKAGE_NAME
    )

    open fun captureScreenSnapshot(): GroceryScreenSnapshot? {
        val service = NovaAccessibilityService.instance ?: return null
        val root = service.rootInActiveWindow ?: return null

        return try {
            val visibleText = extractVisibleText(root)
            val sourcePackageName = root.packageName?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val sourceText = visibleText.joinToString(separator = " | ")
            GroceryScreenSnapshot(
                visibleText = visibleText,
                sourceText = sourceText,
                sourcePackageName = sourcePackageName,
                searchBoxText = findFirstMatchingText(visibleText, listOf("search", "search products", "search groceries")),
                productText = findFirstMatchingText(visibleText, listOf("add", "add to cart", "add item")),
                addButtonText = findFirstMatchingText(visibleText, listOf("add", "+", "add to cart")),
                cartButtonText = findFirstMatchingText(visibleText, listOf("cart", "bag", "view cart")),
                couponButtonText = findFirstMatchingText(visibleText, listOf("apply coupon", "coupon", "offers", "promo code")),
                finalPayableText = findFirstMatchingText(visibleText, listOf("pay", "amount to pay", "final amount", "total payable", "grand total")),
                itemSubtotalText = findFirstMatchingText(visibleText, listOf("subtotal", "item total", "items total")),
                deliveryFeeText = findFirstMatchingText(visibleText, listOf("delivery fee", "delivery charges")),
                handlingFeeText = findFirstMatchingText(visibleText, listOf("handling fee", "platform fee")),
                etaText = findFirstMatchingText(visibleText, listOf("eta", "arrives", "delivery by")),
                unavailableItems = detectUnavailableItems(visibleText),
                replacementItems = detectReplacementItems(visibleText),
                manualActionReason = detectManualActionReason(visibleText)
            )
        } finally {
            root.recycle()
        }
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
        repeat(attempts) {
            val currentPackage = currentForegroundPackageName()
            if (!currentPackage.isNullOrBlank() && expectedPackageNames.contains(currentPackage)) {
                GroceryLogger.d(
                    "foreground_package_matched",
                    mapOf("packageName" to currentPackage)
                )
                return currentPackage
            }
            sleep(delayMs)
        }

        GroceryLogger.w(
            "foreground_package_timeout",
            mapOf("expected" to expectedPackageNames.joinToString(separator = ","))
        )
        return null
    }

    override fun clickTextOrDescriptionAnyOf(candidates: List<String>): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        val root = service.rootInActiveWindow ?: return false

        return try {
            candidates.firstOrNull { candidate ->
                AccessibilityNodeUtils.findClickableNode(root, candidate)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            } != null
        } finally {
            root.recycle()
        }
    }

    override fun typeIntoFocusedField(text: String): Boolean {
        val service = NovaAccessibilityService.instance ?: return false
        val root = service.rootInActiveWindow ?: return false

        return try {
            val focusedNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: AccessibilityNodeUtils.findEditableNode(root)
                ?: return false

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }

            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            root.recycle()
        }
    }

    open fun tapCartButton(): Boolean {
        return clickTextOrDescriptionAnyOf(
            listOf("Cart", "Bag", "View cart", "Go to cart", "View Bag")
        )
    }

    open fun tapCouponButton(): Boolean {
        return clickTextOrDescriptionAnyOf(
            listOf("Apply coupon", "Coupon", "Offers", "Promo code", "Apply offer")
        )
    }

    open fun tapAddButton(): Boolean {
        return clickTextOrDescriptionAnyOf(
            listOf("Add", "ADD", "+", "Add item", "Add to cart")
        )
    }

    open fun tapFinalOrderButton(): Boolean {
        return clickTextOrDescriptionAnyOf(
            listOf(
                "Place order",
                "Confirm order",
                "Review order",
                "Proceed to checkout",
                "Continue to checkout",
                "Checkout",
                "Place my order",
                "Order now",
                "Complete order"
            )
        )
    }

    open fun searchAndAddItem(item: GroceryItem): Boolean {
        val query = item.displayText()
        val searchCandidates = listOf(
            "Search",
            "Search products",
            "Search groceries",
            "What are you looking for?",
            "Search for items",
            "Search store"
        )

        val searchOpened = clickTextOrDescriptionAnyOf(searchCandidates)
        if (!searchOpened) {
            GroceryLogger.w("search_field_not_found", mapOf("query" to query))
            return false
        }

        sleep(150)
        if (!typeIntoFocusedField(query)) {
            GroceryLogger.w("search_input_failed", mapOf("query" to query))
            return false
        }

        sleep(150)
        clickTextOrDescriptionAnyOf(listOf("Search", "Done", "Go", "Enter", "Submit"))
        sleep(100)

        val added = tapAddButton()
        if (!added) {
            GroceryLogger.w("add_button_not_found", mapOf("query" to query))
        }
        return added
    }

    open fun collectCartSummary(provider: GroceryProvider): GroceryCartSummary? {
        val snapshot = captureScreenSnapshot() ?: return null
        return buildCartSummary(provider, snapshot)
    }

    open fun collectCartCandidate(
        provider: GroceryProvider,
        basket: GroceryBasket,
        couponCode: String? = null
    ): GroceryCartCandidate {
        val searchedQueries = mutableListOf<String>()
        val unavailable = mutableListOf<String>()

        val initialSnapshot = captureScreenSnapshot()
        initialSnapshot?.manualActionReason?.let { reason ->
            return GroceryCartCandidate(
                provider = provider,
                basket = basket,
                summary = buildCartSummary(provider, initialSnapshot),
                searchQueries = searchedQueries,
                manualActionReason = reason
            )
        }

        basket.items.forEach { item ->
            searchedQueries.add(item.displayText())
            val added = searchAndAddItem(item)
            if (!added) {
                unavailable.add(item.displayText())
            }
            sleep(100)
        }

        val afterSearchSnapshot = captureScreenSnapshot()
        val manualReason = afterSearchSnapshot?.manualActionReason ?: detectManualActionReason(afterSearchSnapshot?.visibleText.orEmpty())
        if (manualReason != null) {
            return GroceryCartCandidate(
                provider = provider,
                basket = basket,
                summary = buildCartSummary(provider, afterSearchSnapshot),
                searchQueries = searchedQueries,
                manualActionReason = manualReason,
                finalCheckoutReady = false
            )
        }

        val couponEngine = GroceryCouponEngine()
        val couponResult = couponEngine.applyVisibleCoupon(
            provider = provider,
            snapshot = afterSearchSnapshot,
            automation = this,
            userCouponCode = couponCode
        )

        val summarySnapshot = captureScreenSnapshot()
        val summary = buildCartSummary(provider, summarySnapshot).copy(
            couponApplied = couponResult.applied,
            couponCode = couponResult.couponCode ?: summarySnapshot?.sourceText?.takeIf { couponResult.applied }?.take(20),
            couponText = couponResult.visibleCouponText,
            unavailableItems = (summarySnapshot?.unavailableItems.orEmpty() + unavailable).distinct()
        )

        return GroceryCartCandidate(
            provider = provider,
            basket = basket,
            summary = priceComparator.normalize(
                GroceryCartCandidate(
                    provider = provider,
                    basket = basket,
                    summary = summary
                )
            ).summary,
            searchQueries = searchedQueries,
            manualActionReason = null,
            finalCheckoutReady = true
        )
    }

    open fun detectManualActionReason(texts: List<String>): GroceryManualActionReason? {
        val normalized = texts.joinToString(separator = " ").lowercase(Locale.US)

        return when {
            listOf("otp", "one time password").any { normalized.contains(it) } -> GroceryManualActionReason.OTP
            listOf("payment", "pay now", "proceed to pay", "checkout", "place order", "complete payment").any { normalized.contains(it) } -> GroceryManualActionReason.PAYMENT
            listOf("captcha").any { normalized.contains(it) } -> GroceryManualActionReason.CAPTCHA
            listOf("login", "sign in").any { normalized.contains(it) } -> GroceryManualActionReason.LOGIN
            listOf("upi", "card", "cvv", "pin").any { normalized.contains(it) } -> GroceryManualActionReason.PAYMENT
            listOf("allow location", "permission", "location permission").any { normalized.contains(it) } -> GroceryManualActionReason.PERMISSION
            listOf("delivery address", "address selection", "select address").any { normalized.contains(it) } -> GroceryManualActionReason.ADDRESS
            listOf("replace item", "replacement", "choose replacement").any { normalized.contains(it) } -> GroceryManualActionReason.REPLACEMENT
            listOf("out of stock", "unavailable", "not available").any { normalized.contains(it) } -> GroceryManualActionReason.UNAVAILABLE_ITEMS
            listOf("manual action", "secure screen", "cannot read", "protected screen").any { normalized.contains(it) } -> GroceryManualActionReason.MANUAL_SCREEN
            else -> null
        }
    }

    open fun detectUnavailableItems(texts: List<String>): List<String> {
        return texts.filter { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("out of stock") ||
                normalized.contains("unavailable") ||
                normalized.contains("not available")
        }
    }

    open fun detectReplacementItems(texts: List<String>): List<String> {
        return texts.filter { text ->
            val normalized = text.lowercase(Locale.US)
            normalized.contains("replace") ||
                normalized.contains("replacement") ||
                normalized.contains("substitute")
        }
    }

    open fun sleep(delayMs: Long) {
        runCatching {
            Thread.sleep(delayMs)
        }
    }

    private fun buildCartSummary(provider: GroceryProvider, snapshot: GroceryScreenSnapshot?): GroceryCartSummary {
        if (snapshot == null) {
            return GroceryCartSummary(provider = provider)
        }

        val finalText = snapshot.finalPayableText ?: snapshot.sourceText
        val summary = GroceryCartSummary(
            provider = provider,
            itemSubtotal = priceComparator.extractAmount(snapshot.itemSubtotalText),
            deliveryFee = priceComparator.extractAmount(snapshot.deliveryFeeText),
            handlingFee = priceComparator.extractAmount(snapshot.handlingFeeText),
            couponDiscount = null,
            finalPayableValue = priceComparator.extractAmount(finalText),
            etaText = snapshot.etaText,
            etaMinutes = priceComparator.extractEtaMinutes(snapshot.etaText ?: finalText),
            unavailableItems = snapshot.unavailableItems,
            replacementItems = snapshot.replacementItems,
            couponCode = null,
            couponText = snapshot.couponButtonText,
            couponApplied = snapshot.visibleText.any {
                val normalized = it.lowercase(Locale.US)
                normalized.contains("coupon applied") ||
                    normalized.contains("offer applied") ||
                    normalized.contains("promo applied")
            },
            packageName = snapshot.sourcePackageName,
            sourceText = snapshot.sourceText
        )

        return if (summary.finalPayableValue == null) {
            val candidate = GroceryCartCandidate(provider = provider, basket = GroceryBasket(), summary = summary)
            priceComparator.normalize(candidate).summary
        } else {
            summary
        }
    }

    private fun extractVisibleText(root: AccessibilityNodeInfo): List<String> {
        val collected = mutableListOf<String>()
        collectVisibleText(root, collected)
        return collected.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, sink: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectVisibleText(child, sink)
        }
    }

    private fun findFirstMatchingText(texts: List<String>, candidates: List<String>): String? {
        val normalizedTexts = texts.map { it.lowercase(Locale.US) }
        return candidates.firstOrNull { candidate ->
            val normalizedCandidate = candidate.lowercase(Locale.US)
            normalizedTexts.any { text ->
                text.contains(normalizedCandidate) || FuzzyMatcher.similarity(text, normalizedCandidate) >= 80
            }
        }
    }
}
