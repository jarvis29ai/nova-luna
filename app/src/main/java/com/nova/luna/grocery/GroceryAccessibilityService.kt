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
    private val couponEngine = GroceryCouponEngine(priceComparator)
    private val providerPackages = setOf(
        GroceryProviderRegistry.BLINKIT_PACKAGE_NAME,
        GroceryProviderRegistry.ZEPTO_PACKAGE_NAME,
        GroceryProviderRegistry.JIOMART_PACKAGE_NAME,
        GroceryProviderRegistry.INSTAMART_PACKAGE_NAME,
        GroceryProviderRegistry.SWIGGY_PACKAGE_NAME,
        GroceryProviderRegistry.BIGBASKET_PACKAGE_NAME
    )

    private val groceryBrands = listOf(
        "amul",
        "britannia",
        "aashirvaad",
        "fortune",
        "patanjali",
        "mother dairy",
        "dabur",
        "parle",
        "nestle",
        "sunfeast",
        "maggi",
        "saffola",
        "mdh",
        "everest",
        "tata",
        "surf excel",
        "colgate",
        "vim",
        "borosil",
        "epigamia",
        "lays",
        "kellogg",
        "brooke bond",
        "tetley",
        "fresho",
        "haldiram",
        "mtr"
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
                searchBoxText = findFirstMatchingText(visibleText, listOf("search", "search products", "search groceries", "search store")),
                productText = findFirstMatchingText(visibleText, listOf("product", "item", "add", "add to cart")),
                addButtonText = findFirstMatchingText(visibleText, listOf("add", "+", "add to cart")),
                cartButtonText = findFirstMatchingText(visibleText, listOf("cart", "bag", "view cart", "go to cart")),
                couponButtonText = findFirstMatchingText(visibleText, listOf("apply coupon", "coupon", "offers", "promo code", "apply offer")),
                finalPayableText = findFirstMatchingText(visibleText, listOf("pay", "amount to pay", "final amount", "total payable", "grand total", "place order")),
                itemSubtotalText = findFirstMatchingText(visibleText, listOf("subtotal", "item total", "items total")),
                deliveryFeeText = findFirstMatchingText(visibleText, listOf("delivery fee", "delivery charges", "shipping fee")),
                handlingFeeText = findFirstMatchingText(visibleText, listOf("handling fee", "platform fee")),
                etaText = findFirstMatchingText(visibleText, listOf("eta", "arrives", "delivery by", "delivery in")),
                walletBalanceText = findFirstMatchingText(visibleText, listOf("wallet balance", "available balance", "wallet", "balance")),
                codAvailabilityText = findFirstMatchingText(visibleText, listOf("cash on delivery", "cod", "pay on delivery")),
                orderConfirmationText = findFirstMatchingText(visibleText, listOf("order placed", "order confirmed", "order successful", "thank you for your order")),
                orderIdText = findFirstMatchingText(visibleText, listOf("order id", "order no", "order number", "tracking id")),
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
        totalWaitMs: Long = 5_000L
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
                "Complete order",
                "Pay now"
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

    open fun extractProductOptions(
        texts: List<String>,
        basket: GroceryBasket? = null
    ): List<GroceryProductOption> {
        val normalizedBasketNames = basket?.items?.map { it.name.lowercase(Locale.US) }.orEmpty()
        return texts.mapNotNull { text ->
            val normalized = text.lowercase(Locale.US)
            val hasPrice = priceComparator.extractAmount(text) != null
            val hasPackSize = priceComparator.extractPackSize(text) != null
            val hasRating = priceComparator.extractRating(text) != null
            val hasEta = priceComparator.extractEtaMinutes(text) != null
            val hasItemCue = normalizedBasketNames.isEmpty() || normalizedBasketNames.any { normalized.contains(it) } || normalized.anyItemCue()

            if (!hasPrice && !hasPackSize && !hasRating && !hasEta && !hasItemCue) {
                return@mapNotNull null
            }

            GroceryProductOption(
                itemName = basket?.items?.firstOrNull { normalized.contains(it.name.lowercase(Locale.US)) }?.name
                    ?: text.substringBefore(" - ").trim().ifBlank { text.trim() },
                title = text.trim(),
                priceText = priceComparator.extractAmount(text)?.let { "₹$it" } ?: priceComparator.extractAmount(text)?.toString(),
                priceValue = priceComparator.extractAmount(text),
                packSizeText = priceComparator.extractPackSize(text),
                brand = extractBrand(text),
                ratingText = priceComparator.extractRatingText(text),
                ratingValue = priceComparator.extractRating(text),
                deliveryTimeText = priceComparator.extractEtaText(text),
                deliveryTimeMinutes = priceComparator.extractEtaMinutes(text),
                available = !containsAny(normalized, listOf("out of stock", "unavailable", "not available")),
                deliveryFeeText = if (containsAny(normalized, listOf("delivery fee", "delivery charges"))) text.trim() else null,
                deliveryFeeValue = if (containsAny(normalized, listOf("delivery fee", "delivery charges"))) priceComparator.extractAmount(text) else null,
                couponText = if (containsAny(normalized, listOf("coupon", "offer", "promo", "discount"))) text.trim() else null,
                couponSavingValue = if (containsAny(normalized, listOf("coupon", "offer", "promo", "discount", "save"))) priceComparator.extractCouponSaving(text) else null,
                substitutionOf = if (containsAny(normalized, listOf("replacement", "substitute", "similar item"))) text.trim() else null,
                notes = null
            )
        }
    }

    open fun collectProviderResult(
        provider: GroceryProvider,
        basket: GroceryBasket,
        requirementProfile: GroceryRequirementProfile? = null,
        couponCode: String? = null
    ): GroceryProviderResult {
        val candidate = collectCartCandidate(
            provider = provider,
            basket = basket,
            couponCode = couponCode,
            requirementProfile = requirementProfile
        )

        return candidate.providerResult ?: GroceryProviderResult(
            provider = provider,
            productOptions = candidate.productOptions,
            summary = candidate.summary,
            blocked = candidate.manualActionReason != null,
            partial = candidate.summary.partial,
            blockReason = candidate.manualActionReason?.displayText,
            manualActionReason = candidate.manualActionReason,
            searchQueries = candidate.searchQueries
        )
    }

    open fun collectCartSummary(provider: GroceryProvider): GroceryCartSummary? {
        val snapshot = captureScreenSnapshot() ?: return null
        return buildCartSummary(provider, snapshot)
    }

    open fun collectCartCandidate(
        provider: GroceryProvider,
        basket: GroceryBasket,
        couponCode: String? = null,
        requirementProfile: GroceryRequirementProfile? = null
    ): GroceryCartCandidate {
        val searchedQueries = mutableListOf<String>()
        val unavailable = mutableListOf<String>()

        val initialSnapshot = captureScreenSnapshot()
        initialSnapshot?.manualActionReason?.let { reason ->
            val summary = buildCartSummary(provider, initialSnapshot)
            return GroceryCartCandidate(
                provider = provider,
                basket = basket,
                summary = summary,
                productOptions = extractProductOptions(initialSnapshot.visibleText, basket),
                searchQueries = searchedQueries,
                manualActionReason = reason,
                providerResult = GroceryProviderResult(
                    provider = provider,
                    productOptions = extractProductOptions(initialSnapshot.visibleText, basket),
                    summary = summary,
                    blocked = true,
                    partial = summary.partial,
                    blockReason = reason.displayText,
                    manualActionReason = reason,
                    searchQueries = searchedQueries
                ),
                finalCheckoutReady = false
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
        val productOptions = extractProductOptions(afterSearchSnapshot?.visibleText.orEmpty(), basket)
        val manualReason = afterSearchSnapshot?.manualActionReason ?: detectManualActionReason(afterSearchSnapshot?.visibleText.orEmpty())
        if (manualReason != null) {
            val summary = buildCartSummary(provider, afterSearchSnapshot).copy(
                productOptions = productOptions,
                unavailableItems = (afterSearchSnapshot?.unavailableItems.orEmpty() + unavailable).distinct(),
                partial = true,
                blocked = true,
                blockReason = manualReason.displayText
            )
            return GroceryCartCandidate(
                provider = provider,
                basket = basket,
                summary = summary,
                productOptions = productOptions,
                searchQueries = searchedQueries,
                manualActionReason = manualReason,
                providerResult = GroceryProviderResult(
                    provider = provider,
                    productOptions = productOptions,
                    summary = summary,
                    blocked = true,
                    partial = true,
                    blockReason = manualReason.displayText,
                    manualActionReason = manualReason,
                    searchQueries = searchedQueries
                ),
                finalCheckoutReady = false
            )
        }

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
            unavailableItems = (summarySnapshot?.unavailableItems.orEmpty() + unavailable).distinct(),
            replacementItems = (summarySnapshot?.replacementItems.orEmpty()).distinct(),
            productOptions = productOptions,
            partial = summarySnapshot == null || productOptions.isEmpty() || couponResult.warning != null || summarySnapshot.finalPayableText == null
        )

        val finalCandidate = GroceryCartCandidate(
            provider = provider,
            basket = basket,
            summary = priceComparator.normalize(
                GroceryCartCandidate(
                    provider = provider,
                    basket = basket,
                    summary = summary,
                    productOptions = productOptions,
                    searchQueries = searchedQueries
                )
            ).summary,
            productOptions = productOptions,
            searchQueries = searchedQueries,
            manualActionReason = null,
            providerResult = GroceryProviderResult(
                provider = provider,
                productOptions = productOptions,
                cartOption = GroceryCartOption(
                    provider = provider,
                    items = productOptions,
                    summary = summary
                ),
                summary = summary,
                blocked = false,
                partial = summary.partial,
                blockReason = summary.blockReason,
                manualActionReason = null,
                searchQueries = searchedQueries
            ),
            finalCheckoutReady = true
        )

        return finalCandidate
    }

    open fun detectManualActionReason(texts: List<String>): GroceryManualActionReason? {
        val normalized = texts.joinToString(separator = " ").lowercase(Locale.US)

        return when {
            listOf("otp", "one time password").any { normalized.contains(it) } -> GroceryManualActionReason.OTP
            listOf("payment", "pay now", "proceed to pay", "checkout", "place order", "complete payment").any { normalized.contains(it) } -> GroceryManualActionReason.PAYMENT
            listOf("password").any { normalized.contains(it) } -> GroceryManualActionReason.PASSWORD
            listOf("captcha").any { normalized.contains(it) } -> GroceryManualActionReason.CAPTCHA
            listOf("login", "sign in").any { normalized.contains(it) } -> GroceryManualActionReason.LOGIN
            listOf("upi pin", "upi").any { normalized.contains(it) } -> GroceryManualActionReason.UPI_PIN
            listOf("cvv", "card security code").any { normalized.contains(it) } -> GroceryManualActionReason.CARD_CVV
            listOf("net banking", "internet banking").any { normalized.contains(it) } -> GroceryManualActionReason.NET_BANKING
            listOf("wallet top up", "top up wallet").any { normalized.contains(it) } -> GroceryManualActionReason.WALLET_TOPUP
            listOf("biometric").any { normalized.contains(it) } -> GroceryManualActionReason.BIOMETRIC
            listOf("allow location", "location permission", "enable location").any { normalized.contains(it) } -> GroceryManualActionReason.LOCATION_PERMISSION
            listOf("usage access").any { normalized.contains(it) } -> GroceryManualActionReason.USAGE_ACCESS
            listOf("accessibility").any { normalized.contains(it) } -> GroceryManualActionReason.ACCESSIBILITY
            listOf("delivery address", "address selection", "select address").any { normalized.contains(it) } -> GroceryManualActionReason.ADDRESS
            listOf("replace item", "replacement", "choose replacement").any { normalized.contains(it) } -> GroceryManualActionReason.REPLACEMENT
            listOf("out of stock", "unavailable", "not available").any { normalized.contains(it) } -> GroceryManualActionReason.UNAVAILABLE_ITEMS
            listOf("manual action", "secure screen", "cannot read", "protected screen").any { normalized.contains(it) } -> GroceryManualActionReason.MANUAL_SCREEN
            else -> null
        }
    }

    open fun detectUnsafePaymentScreen(texts: List<String>): GroceryManualActionReason? {
        return detectManualActionReason(texts)
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
                normalized.contains("substitute") ||
                normalized.contains("similar item")
        }
    }

    open fun detectCodAvailability(snapshot: GroceryScreenSnapshot? = null): Boolean? {
        val text = buildString {
            snapshot?.codAvailabilityText?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (isNotBlank()) append(" ")
            append(snapshot?.visibleText.orEmpty().joinToString(separator = " "))
        }.lowercase(Locale.US)

        if (text.isBlank()) return null
        return when {
            listOf("not available", "unavailable", "no cod", "cash on delivery not", "cod not").any { text.contains(it) } -> false
            listOf("cash on delivery", "cod", "pay on delivery").any { text.contains(it) } -> true
            else -> null
        }
    }

    open fun detectWalletBalance(snapshot: GroceryScreenSnapshot? = null): Long? {
        val text = snapshot?.walletBalanceText ?: snapshot?.visibleText?.firstOrNull {
            val normalized = it.lowercase(Locale.US)
            normalized.contains("wallet") || normalized.contains("balance")
        }
        return priceComparator.extractAmount(text)
    }

    open fun detectOrderConfirmation(snapshot: GroceryScreenSnapshot? = null): GroceryOrderConfirmation? {
        val texts = snapshot?.visibleText.orEmpty()
        val joined = texts.joinToString(separator = " ").lowercase(Locale.US)
        val placed = listOf("order placed", "order confirmed", "order successful", "thank you for your order").any { joined.contains(it) }
        val orderId = detectOrderId(snapshot)
        val finalPrice = priceComparator.extractFinalPayable(snapshot?.finalPayableText ?: snapshot?.sourceText)
        val savings = priceComparator.extractCouponSaving(snapshot?.sourceText)
        val deliveryEstimate = snapshot?.etaText ?: priceComparator.extractEtaText(snapshot?.sourceText)

        if (!placed && orderId == null && finalPrice == null && deliveryEstimate == null) return null

        return GroceryOrderConfirmation(
            orderId = orderId,
            provider = snapshot?.sourcePackageName?.let { packageName ->
                when {
                    packageName.contains(GroceryProviderRegistry.BLINKIT_PACKAGE_NAME, ignoreCase = true) -> GroceryProvider.BLINKIT
                    packageName.contains(GroceryProviderRegistry.ZEPTO_PACKAGE_NAME, ignoreCase = true) -> GroceryProvider.ZEPTO
                    packageName.contains(GroceryProviderRegistry.INSTAMART_PACKAGE_NAME, ignoreCase = true) -> GroceryProvider.INSTAMART
                    packageName.contains(GroceryProviderRegistry.JIOMART_PACKAGE_NAME, ignoreCase = true) -> GroceryProvider.JIOMART
                    packageName.contains(GroceryProviderRegistry.BIGBASKET_PACKAGE_NAME, ignoreCase = true) -> GroceryProvider.BIGBASKET
                    else -> null
                }
            },
            deliveryEstimate = deliveryEstimate,
            finalPrice = finalPrice,
            totalSavings = savings,
            placed = placed || orderId != null,
            manualActionNeeded = false,
            manualReason = null
        )
    }

    open fun detectOrderId(snapshot: GroceryScreenSnapshot? = null): String? {
        val texts = buildList {
            snapshot?.orderIdText?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(snapshot?.visibleText.orEmpty())
        }

        val pattern = Regex("""(?:order\s*(?:id|no|number)|tracking\s*id)\s*[:#-]?\s*([A-Z0-9-]{4,})""", RegexOption.IGNORE_CASE)
        texts.forEach { text ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
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

        val productOptions = extractProductOptions(snapshot.visibleText)
        val finalText = snapshot.finalPayableText ?: snapshot.sourceText
        val summary = GroceryCartSummary(
            provider = provider,
            itemSubtotal = priceComparator.extractAmount(snapshot.itemSubtotalText),
            deliveryFee = priceComparator.extractDeliveryFee(snapshot.deliveryFeeText ?: snapshot.sourceText),
            handlingFee = priceComparator.extractAmount(snapshot.handlingFeeText),
            couponDiscount = priceComparator.extractCouponSaving(snapshot.sourceText),
            finalPayableValue = priceComparator.extractFinalPayable(finalText) ?: priceComparator.extractAmount(finalText),
            etaText = snapshot.etaText ?: priceComparator.extractEtaText(snapshot.sourceText),
            etaMinutes = priceComparator.extractEtaMinutes(snapshot.etaText ?: finalText),
            packSizeText = priceComparator.extractPackSize(productOptions.firstOrNull()?.title ?: snapshot.sourceText),
            ratingText = priceComparator.extractRatingText(snapshot.sourceText),
            ratingValue = priceComparator.extractRating(snapshot.sourceText),
            productOptions = productOptions,
            unavailableItems = snapshot.unavailableItems,
            replacementItems = snapshot.replacementItems,
            couponCode = null,
            couponText = snapshot.couponButtonText,
            couponApplied = snapshot.visibleText.any {
                val normalized = it.lowercase(Locale.US)
                normalized.contains("coupon applied") ||
                    normalized.contains("offer applied") ||
                    normalized.contains("promo applied")
            } || snapshot.couponButtonText != null,
            packageName = snapshot.sourcePackageName,
            sourceText = snapshot.sourceText,
            partial = snapshot.visibleText.isEmpty() || snapshot.finalPayableText == null || productOptions.isEmpty(),
            blocked = snapshot.manualActionReason != null,
            blockReason = snapshot.manualActionReason?.displayText
        )

        return if (summary.finalPayableValue == null) {
            val candidate = GroceryCartCandidate(provider = provider, basket = GroceryBasket(), summary = summary, productOptions = productOptions)
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

    private fun extractBrand(text: String): String? {
        val normalized = text.lowercase(Locale.US)
        return groceryBrands.firstOrNull { brand ->
            normalized.contains(brand)
        }?.let { matched ->
            matched.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                }
            }
        }
    }

    private fun String.anyItemCue(): Boolean {
        val normalized = lowercase(Locale.US)
        return listOf("milk", "bread", "rice", "atta", "sugar", "dal", "oil", "ghee", "butter", "egg", "eggs", "tea", "coffee", "soap", "shampoo").any { normalized.contains(it) }
    }

    private fun containsAny(normalized: String, keywords: List<String>): Boolean {
        return keywords.any { normalized.contains(it.lowercase(Locale.US)) }
    }
}
