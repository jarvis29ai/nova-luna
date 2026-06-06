package com.nova.luna.grocery

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.util.AccessibilityReadiness
import java.util.Locale

data class GroceryItem(
    val name: String,
    val quantityText: String? = null,
    val quantityValue: Double? = null,
    val unit: String? = null,
    val brand: String? = null,
    val rawText: String = name
) {
    fun displayText(): String {
        return buildString {
            quantityText?.takeIf { it.isNotBlank() }?.let {
                append(it.trim())
                append(' ')
            }
            brand?.takeIf { it.isNotBlank() }?.let {
                append(it.trim())
                append(' ')
            }
            append(name.trim())
        }.trim()
    }

    fun normalizedKey(): String {
        return listOfNotNull(brand, quantityText, unit, name)
            .joinToString(separator = " ")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

fun GroceryItem.toItemRequest(): GroceryItemRequest {
    return GroceryItemRequest(
        name = name,
        quantityText = quantityText,
        quantityValue = quantityValue,
        unit = unit,
        preferredBrand = brand,
        rawText = rawText
    )
}

data class GroceryBasket(
    val items: MutableList<GroceryItem> = mutableListOf()
) {
    fun isEmpty(): Boolean = items.isEmpty()

    fun clear() {
        items.clear()
    }

    fun add(item: GroceryItem) {
        items.add(item)
    }

    fun addAll(itemsToAdd: Iterable<GroceryItem>) {
        items.addAll(itemsToAdd)
    }

    fun removeMatching(query: String): GroceryItem? {
        if (query.isBlank()) return null

        val normalized = query.lowercase(Locale.US).trim()
        val matchIndex = items.indexOfFirst { item ->
            item.normalizedKey().contains(normalized) ||
                normalized.contains(item.normalizedKey())
        }

        if (matchIndex < 0) return null
        return items.removeAt(matchIndex)
    }

    fun displayText(): String {
        if (items.isEmpty()) return "empty basket"
        return items.joinToString(separator = ", ") { it.displayText() }
    }
}

enum class GroceryProvider {
    BLINKIT,
    ZEPTO,
    INSTAMART,
    JIOMART,
    BIGBASKET
}

enum class GroceryBudgetPreference {
    CHEAPEST,
    BEST_QUALITY,
    FAST_DELIVERY,
    BEST_OVERALL
}

enum class GroceryDeliveryUrgency {
    NOW,
    TODAY,
    SCHEDULED
}

enum class GroceryPaymentMethod {
    UPI,
    CARD,
    NET_BANKING,
    WALLET,
    COD,
    MANUAL;

    fun displayName(): String {
        return when (this) {
            UPI -> "UPI"
            CARD -> "card"
            NET_BANKING -> "net banking"
            WALLET -> "wallet"
            COD -> "cash on delivery"
            MANUAL -> "manual"
        }
    }

    fun isSensitive(): Boolean {
        return this in setOf(UPI, CARD, NET_BANKING)
    }
}

enum class GroceryBookingStatus {
    IDLE,
    IN_PROGRESS,
    WAITING_FOR_USER,
    MANUAL_ACTION_REQUIRED,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class GroceryBookingState {
    IDLE,
    PARSING_REQUEST,
    NEED_ITEMS,
    NEED_PREVIOUS_LIST,
    NEED_QUANTITY,
    NEED_BRAND,
    NEED_BUDGET_PREFERENCE,
    NEED_DELIVERY_URGENCY,
    NEED_DELIVERY_LOCATION,
    NEED_REPLACEMENT_PREFERENCE,
    CHECKING_PERMISSIONS,
    PERMISSION_BLOCKED,
    CHECKING_PROVIDERS,
    NO_PROVIDER_AVAILABLE,
    OPENING_PROVIDER,
    SETTING_DELIVERY_LOCATION,
    SEARCHING_ITEMS,
    READING_PRODUCT_OPTIONS,
    MATCHING_PRODUCTS,
    COMPARING_OPTIONS,
    SHOWING_COMPARISON,
    WAITING_FOR_PROVIDER_CHOICE,
    REFINING_SEARCH,
    OPENING_SELECTED_PROVIDER,
    ADDING_ITEMS,
    HANDLING_UNAVAILABLE_ITEMS,
    APPLYING_COUPON,
    RECALCULATING_TOTAL,
    SHOWING_FINAL_SUMMARY,
    WAITING_FOR_FINAL_CONFIRMATION,
    ASKING_PAYMENT_METHOD,
    CHECKING_WALLET_BALANCE,
    WAITING_FOR_WALLET_CONFIRMATION,
    CHECKING_COD,
    WAITING_FOR_COD_CONFIRMATION,
    OPENING_PAYMENT_PAGE,
    WAITING_FOR_ORDER_RESPONSE,
    BOOKING,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

typealias GroceryFlowState = GroceryBookingState

enum class GroceryManualActionReason(val displayText: String) {
    LOGIN("login"),
    OTP("OTP"),
    PAYMENT("payment"),
    PASSWORD("password"),
    CAPTCHA("captcha"),
    UPI_PIN("UPI PIN"),
    CARD_CVV("card CVV"),
    NET_BANKING("net banking"),
    WALLET_TOPUP("wallet top-up"),
    BIOMETRIC("biometric"),
    ADDRESS("address"),
    LOCATION_PERMISSION("location permission"),
    USAGE_ACCESS("usage access"),
    ACCESSIBILITY("accessibility"),
    REPLACEMENT("replacement"),
    UNAVAILABLE_ITEMS("unavailable items"),
    MANUAL_SCREEN("manual screen"),
    PERMISSION("permission"),
    UNKNOWN("manual action")
}

object GroceryFailureReasons {
    const val BLOCKED_BY_ACCESSIBILITY_NOT_READY = AccessibilityReadiness.BLOCKED_BY_ACCESSIBILITY_NOT_READY
    const val BLOCKED_BY_LOCATION_PERMISSION = "blocked_by_location_permission"
    const val BLOCKED_BY_USAGE_ACCESS = "blocked_by_usage_access"
    const val BLOCKED_BY_PROVIDER_UI = "blocked_by_provider_ui"
}

enum class GroceryFollowUpType {
    ADD_ITEM,
    REMOVE_ITEM,
    COMPARE,
    BOOK_CHEAPEST,
    BOOK_PROVIDER,
    APPLY_COUPON,
    CANCEL,
    CONFIRM,
    BRAND_PREFERENCE,
    REGULAR,
    REFINEMENT,
    SELECTION,
    REPLACEMENT,
    SEARCH_AGAIN,
    REORDER,
    UNKNOWN
}

enum class GrocerySelectionPreference {
    FIRST,
    SECOND,
    THIRD,
    CHEAPEST,
    FASTEST,
    BEST_QUALITY,
    BEST_OVERALL,
    PROVIDER
}

enum class GroceryRefineTarget {
    ITEM,
    BRAND,
    QUANTITY,
    BUDGET,
    DELIVERY_TIME,
    PROVIDER,
    LOCATION,
    SEARCH
}

enum class GroceryPendingItemField {
    QUANTITY,
    BRAND,
    REPLACEMENT
}

enum class GroceryRankingCategory {
    CHEAPEST,
    FASTEST,
    BEST_QUALITY,
    BEST_OVERALL,
    RECOMMENDED
}

data class GroceryItemRequest(
    val name: String,
    val quantityText: String? = null,
    val quantityValue: Double? = null,
    val unit: String? = null,
    val preferredBrand: String? = null,
    val acceptableSubstitutions: List<String> = emptyList(),
    val allowSubstitution: Boolean = true,
    val rawText: String = name
) {
    fun toItem(): GroceryItem {
        return GroceryItem(
            name = name,
            quantityText = quantityText,
            quantityValue = quantityValue,
            unit = unit,
            brand = preferredBrand,
            rawText = rawText
        )
    }
}

data class GroceryRequirementProfile(
    val items: List<GroceryItemRequest> = emptyList(),
    val brandPreference: String? = null,
    val budgetPreference: GroceryBudgetPreference = GroceryBudgetPreference.BEST_OVERALL,
    val providerPreference: GroceryProvider? = null,
    val deliveryUrgency: GroceryDeliveryUrgency = GroceryDeliveryUrgency.TODAY,
    val scheduledTime: String? = null,
    val deliveryLocation: String? = null,
    val useCurrentLocation: Boolean = false,
    val allowComparison: Boolean = true,
    val reorderMode: Boolean = false,
    val previousListMode: Boolean = false,
    val requiresFinalConfirmation: Boolean = true,
    val paymentPreference: GroceryPaymentMethod? = null,
    val safetyNotes: String? = null,
    val allowSubstitutions: Boolean = true
)

data class GroceryPermissionStatus(
    val accessibilityReady: Boolean = true,
    val locationPermissionGranted: Boolean = true,
    val usageAccessGranted: Boolean = true,
    val locationPermissionRequired: Boolean = false,
    val usageAccessRequired: Boolean = false
) {
    fun blockedReason(): String? {
        return when {
            !accessibilityReady -> GroceryFailureReasons.BLOCKED_BY_ACCESSIBILITY_NOT_READY
            locationPermissionRequired && !locationPermissionGranted -> GroceryFailureReasons.BLOCKED_BY_LOCATION_PERMISSION
            usageAccessRequired && !usageAccessGranted -> GroceryFailureReasons.BLOCKED_BY_USAGE_ACCESS
            else -> null
        }
    }

    fun isReady(): Boolean = blockedReason() == null
}

data class GroceryFollowUpCommand(
    val rawText: String,
    val type: GroceryFollowUpType,
    val providerPreference: GroceryProvider? = null,
    val couponCode: String? = null,
    val brandPreference: String? = null,
    val item: GroceryItem? = null,
    val itemQuery: String? = null,
    val compareRequested: Boolean = false,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val wantsFastest: Boolean = false,
    val wantsBestQuality: Boolean = false,
    val wantsBestOverall: Boolean = false,
    val selectionIndex: Int? = null,
    val selectionPreference: GrocerySelectionPreference? = null,
    val refineTarget: GroceryRefineTarget? = null,
    val replacementRequested: Boolean = false,
    val removeRequested: Boolean = false,
    val searchAgainRequested: Boolean = false,
    val reorderRequested: Boolean = false,
    val paymentPreference: GroceryPaymentMethod? = null,
    val finalUserConfirmed: Boolean = false
)

data class GroceryIntentParseResult(
    val rawText: String,
    val isGroceryBooking: Boolean,
    val basket: GroceryBasket = GroceryBasket(),
    val requirementProfile: GroceryRequirementProfile? = null,
    val providerPreference: GroceryProvider? = null,
    val budgetPreference: GroceryBudgetPreference? = null,
    val deliveryUrgency: GroceryDeliveryUrgency? = null,
    val scheduledTime: String? = null,
    val deliveryLocation: String? = null,
    val useCurrentLocation: Boolean = false,
    val paymentPreference: GroceryPaymentMethod? = null,
    val allowComparison: Boolean = true,
    val reorderMode: Boolean = false,
    val previousListMode: Boolean = false,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val compareRequested: Boolean = false,
    val applyCouponRequested: Boolean = false,
    val couponCode: String? = null,
    val brandPreference: String? = null,
    val requiresBrandQuestion: Boolean = false,
    val requiresQuantityQuestion: Boolean = false,
    val requiresBudgetQuestion: Boolean = false,
    val requiresDeliveryUrgencyQuestion: Boolean = false,
    val requiresLocationQuestion: Boolean = false,
    val itemCountHint: Int = basket.items.size
) {
    fun toBookingRequest(): GroceryBookingRequest {
        return GroceryBookingRequest(
            rawText = rawText,
            basket = basket,
            requirementProfile = requirementProfile,
            preferredProvider = providerPreference,
            brandPreference = brandPreference,
            budgetPreference = budgetPreference,
            deliveryUrgency = deliveryUrgency,
            scheduledTime = scheduledTime,
            deliveryLocation = deliveryLocation,
            useCurrentLocation = useCurrentLocation,
            paymentPreference = paymentPreference,
            allowComparison = allowComparison,
            reorderMode = reorderMode,
            previousListMode = previousListMode,
            wantsCheapest = wantsCheapest,
            wantsFirstOne = wantsFirstOne,
            compareRequested = compareRequested,
            applyCouponRequested = applyCouponRequested,
            couponCode = couponCode
        )
    }

    fun toEntities(): Map<String, String> {
        return toBookingRequest().toEntities() + buildMap {
            put("isGroceryBooking", isGroceryBooking.toString())
            put("requiresBrandQuestion", requiresBrandQuestion.toString())
            put("requiresQuantityQuestion", requiresQuantityQuestion.toString())
            put("requiresBudgetQuestion", requiresBudgetQuestion.toString())
            put("requiresDeliveryUrgencyQuestion", requiresDeliveryUrgencyQuestion.toString())
            put("requiresLocationQuestion", requiresLocationQuestion.toString())
            brandPreference?.takeIf { it.isNotBlank() }?.let { put("brandPreference", it) }
        }
    }
}

data class GroceryBookingRequest(
    val rawText: String,
    val basket: GroceryBasket = GroceryBasket(),
    val requirementProfile: GroceryRequirementProfile? = null,
    val preferredProvider: GroceryProvider? = null,
    val brandPreference: String? = null,
    val budgetPreference: GroceryBudgetPreference? = null,
    val deliveryUrgency: GroceryDeliveryUrgency? = null,
    val scheduledTime: String? = null,
    val deliveryLocation: String? = null,
    val useCurrentLocation: Boolean = false,
    val paymentPreference: GroceryPaymentMethod? = null,
    val allowComparison: Boolean = true,
    val reorderMode: Boolean = false,
    val previousListMode: Boolean = false,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val compareRequested: Boolean = false,
    val applyCouponRequested: Boolean = false,
    val couponCode: String? = null,
    val requiresFinalConfirmation: Boolean = true,
    val safetyNotes: String? = null,
    val finalUserConfirmed: Boolean = false
) {
    fun toRequirementProfile(): GroceryRequirementProfile {
        val profileItems = requirementProfile?.items.orEmpty().ifEmpty {
            basket.items.map { it.toItemRequest() }
        }

        val preferredBrand = brandPreference ?: requirementProfile?.brandPreference
        val brandedItems = profileItems.map { item ->
            if (item.preferredBrand.isNullOrBlank() && !preferredBrand.isNullOrBlank()) {
                item.copy(preferredBrand = preferredBrand)
            } else {
                item
            }
        }

        return GroceryRequirementProfile(
            items = brandedItems,
            brandPreference = preferredBrand,
            budgetPreference = budgetPreference ?: requirementProfile?.budgetPreference ?: when {
                wantsCheapest -> GroceryBudgetPreference.CHEAPEST
                prefersFastDelivery() -> GroceryBudgetPreference.FAST_DELIVERY
                else -> GroceryBudgetPreference.BEST_OVERALL
            },
            providerPreference = preferredProvider ?: requirementProfile?.providerPreference,
            deliveryUrgency = deliveryUrgency ?: requirementProfile?.deliveryUrgency ?: when {
                compareRequested -> GroceryDeliveryUrgency.TODAY
                else -> GroceryDeliveryUrgency.TODAY
            },
            scheduledTime = scheduledTime ?: requirementProfile?.scheduledTime,
            deliveryLocation = deliveryLocation ?: requirementProfile?.deliveryLocation,
            useCurrentLocation = useCurrentLocation || requirementProfile?.useCurrentLocation == true,
            allowComparison = allowComparison || compareRequested || preferredProvider == null,
            reorderMode = reorderMode || requirementProfile?.reorderMode == true,
            previousListMode = previousListMode || requirementProfile?.previousListMode == true,
            requiresFinalConfirmation = requiresFinalConfirmation && (requirementProfile?.requiresFinalConfirmation ?: true),
            paymentPreference = paymentPreference ?: requirementProfile?.paymentPreference,
            safetyNotes = safetyNotes ?: requirementProfile?.safetyNotes,
            allowSubstitutions = requirementProfile?.allowSubstitutions ?: true
        )
    }

    fun toEntities(): Map<String, String> {
        val profile = toRequirementProfile()
        return buildMap {
            put("rawText", rawText)
            put("itemCount", basket.items.size.toString())
            if (basket.items.isNotEmpty()) {
                put("items", basket.items.joinToString(separator = ", ") { item -> item.name })
            }
            basket.items.forEachIndexed { index, item ->
                val prefix = "item${index + 1}"
                put("${prefix}Name", item.name)
                item.quantityText?.takeIf { it.isNotBlank() }?.let { put("${prefix}QuantityText", it) }
                item.quantityValue?.let { put("${prefix}QuantityValue", it.toString()) }
                item.unit?.takeIf { it.isNotBlank() }?.let { put("${prefix}Unit", it) }
                item.brand?.takeIf { it.isNotBlank() }?.let { put("${prefix}Brand", it) }
                put("${prefix}RawText", item.rawText)
            }
            preferredProvider?.let {
                put("preferredProvider", it.name)
                put("providerPreference", it.name)
            }
            brandPreference?.takeIf { it.isNotBlank() }?.let { put("brandPreference", it) }
            budgetPreference?.let { put("budgetPreference", it.name) }
            deliveryUrgency?.let { put("deliveryUrgency", it.name) }
            scheduledTime?.takeIf { it.isNotBlank() }?.let { put("scheduledTime", it) }
            deliveryLocation?.takeIf { it.isNotBlank() }?.let { put("deliveryLocation", it) }
            put("useCurrentLocation", useCurrentLocation.toString())
            paymentPreference?.let { put("paymentPreference", it.name) }
            put("allowComparison", allowComparison.toString())
            put("reorderMode", reorderMode.toString())
            put("previousListMode", previousListMode.toString())
            put("wantsCheapest", wantsCheapest.toString())
            put("wantsFirstOne", wantsFirstOne.toString())
            put("compareRequested", compareRequested.toString())
            put("applyCouponRequested", applyCouponRequested.toString())
            couponCode?.takeIf { it.isNotBlank() }?.let { put("couponCode", it) }
            put("requiresFinalConfirmation", requiresFinalConfirmation.toString())
            safetyNotes?.takeIf { it.isNotBlank() }?.let { put("safetyNotes", it) }
            put("finalUserConfirmed", finalUserConfirmed.toString())
            if (profile.items.isNotEmpty()) {
                put(
                    "requirementItems",
                    profile.items.joinToString(separator = "|") { item ->
                        buildString {
                            append(item.name)
                            item.quantityText?.takeIf { it.isNotBlank() }?.let { append(" @ ").append(it) }
                            item.preferredBrand?.takeIf { it.isNotBlank() }?.let { append(" brand=").append(it) }
                        }
                    }
                )
            }
            put("requirementBudgetPreference", profile.budgetPreference.name)
            put("requirementDeliveryUrgency", profile.deliveryUrgency.name)
            profile.providerPreference?.let { put("requirementProviderPreference", it.name) }
            profile.brandPreference?.takeIf { it.isNotBlank() }?.let { put("requirementBrandPreference", it) }
            profile.deliveryLocation?.takeIf { it.isNotBlank() }?.let { put("requirementDeliveryLocation", it) }
            profile.paymentPreference?.let { put("requirementPaymentPreference", it.name) }
        }
    }

    private fun prefersFastDelivery(): Boolean {
        val text = buildString {
            append(rawText)
            append(' ')
            append(couponCode.orEmpty())
        }.lowercase(Locale.US)
        return listOf("fast", "fastest", "quick", "urgent", "asap", "now").any { text.contains(it) }
    }
}

data class GroceryProductOption(
    val itemName: String,
    val title: String,
    val priceText: String? = null,
    val priceValue: Long? = null,
    val packSizeText: String? = null,
    val brand: String? = null,
    val ratingText: String? = null,
    val ratingValue: Double? = null,
    val deliveryTimeText: String? = null,
    val deliveryTimeMinutes: Int? = null,
    val available: Boolean = true,
    val deliveryFeeText: String? = null,
    val deliveryFeeValue: Long? = null,
    val couponText: String? = null,
    val couponSavingValue: Long? = null,
    val substitutionOf: String? = null,
    val notes: String? = null
)

data class GroceryCartOption(
    val provider: GroceryProvider,
    val items: List<GroceryProductOption> = emptyList(),
    val summary: GroceryCartSummary = GroceryCartSummary(provider = provider),
    val rankingReason: GroceryRankingReason? = null
)

data class GroceryProviderResult(
    val provider: GroceryProvider,
    val productOptions: List<GroceryProductOption> = emptyList(),
    val cartOption: GroceryCartOption? = null,
    val summary: GroceryCartSummary? = null,
    val blocked: Boolean = false,
    val partial: Boolean = false,
    val blockReason: String? = null,
    val manualActionReason: GroceryManualActionReason? = null,
    val searchQueries: List<String> = emptyList()
)

data class GroceryRankingReason(
    val category: GroceryRankingCategory,
    val summary: String,
    val details: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val score: Double = 0.0
)

data class GroceryOrderFinalSummary(
    val appName: String? = null,
    val provider: GroceryProvider? = null,
    val items: List<GroceryProductOption> = emptyList(),
    val itemTotal: Long? = null,
    val deliveryFee: Long? = null,
    val handlingFee: Long? = null,
    val couponSaving: Long? = null,
    val finalPrice: Long? = null,
    val deliveryAddress: String? = null,
    val deliveryTime: String? = null,
    val paymentMethod: GroceryPaymentMethod? = null,
    val paymentOptions: List<GroceryPaymentMethod> = GroceryPaymentMethod.values().toList(),
    val unavailableItems: List<String> = emptyList(),
    val replacedItems: List<String> = emptyList(),
    val bestCartSuggestion: String? = null,
    val bestCartReason: String? = null,
    val orderId: String? = null,
    val warning: String? = null
)

data class GroceryOrderConfirmation(
    val orderId: String? = null,
    val provider: GroceryProvider? = null,
    val deliveryEstimate: String? = null,
    val finalPrice: Long? = null,
    val totalSavings: Long? = null,
    val placed: Boolean = false,
    val manualActionNeeded: Boolean = false,
    val manualReason: String? = null
)

data class GroceryScreenSnapshot(
    val visibleText: List<String>,
    val sourceText: String? = null,
    val sourcePackageName: String? = null,
    val searchBoxText: String? = null,
    val productText: String? = null,
    val addButtonText: String? = null,
    val cartButtonText: String? = null,
    val couponButtonText: String? = null,
    val finalPayableText: String? = null,
    val itemSubtotalText: String? = null,
    val deliveryFeeText: String? = null,
    val handlingFeeText: String? = null,
    val etaText: String? = null,
    val walletBalanceText: String? = null,
    val codAvailabilityText: String? = null,
    val orderConfirmationText: String? = null,
    val orderIdText: String? = null,
    val unavailableItems: List<String> = emptyList(),
    val replacementItems: List<String> = emptyList(),
    val manualActionReason: GroceryManualActionReason? = null
)

data class GroceryProviderAvailability(
    val provider: GroceryProvider,
    val installed: Boolean,
    val packageName: String? = null,
    val label: String? = null,
    val reason: String? = null
)

data class GroceryCartSummary(
    val provider: GroceryProvider,
    val itemSubtotal: Long? = null,
    val deliveryFee: Long? = null,
    val handlingFee: Long? = null,
    val couponDiscount: Long? = null,
    val finalPayableValue: Long? = null,
    val etaText: String? = null,
    val etaMinutes: Int? = null,
    val packSizeText: String? = null,
    val ratingText: String? = null,
    val ratingValue: Double? = null,
    val productOptions: List<GroceryProductOption> = emptyList(),
    val unavailableItems: List<String> = emptyList(),
    val replacementItems: List<String> = emptyList(),
    val couponCode: String? = null,
    val couponText: String? = null,
    val couponApplied: Boolean = false,
    val packageName: String? = null,
    val sourceText: String? = null,
    val partial: Boolean = false,
    val blocked: Boolean = false,
    val blockReason: String? = null
)

data class GroceryCartCandidate(
    val provider: GroceryProvider,
    val basket: GroceryBasket,
    val summary: GroceryCartSummary,
    val productOptions: List<GroceryProductOption> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val manualActionReason: GroceryManualActionReason? = null,
    val providerResult: GroceryProviderResult? = null,
    val rankingReason: GroceryRankingReason? = null,
    val finalCheckoutReady: Boolean = false
)

data class GroceryComparisonResult(
    val candidates: List<GroceryCartCandidate>,
    val recommendedCandidate: GroceryCartCandidate? = null,
    val cheapestCompleteCandidate: GroceryCartCandidate? = null,
    val fastestCandidate: GroceryCartCandidate? = null,
    val bestQualityCandidate: GroceryCartCandidate? = null,
    val bestOverallCandidate: GroceryCartCandidate? = null,
    val providerResults: List<GroceryProviderResult> = emptyList(),
    val rankingReasons: List<GroceryRankingReason> = emptyList()
)

data class GroceryCouponResult(
    val provider: GroceryProvider,
    val couponCode: String? = null,
    val applied: Boolean = false,
    val found: Boolean = false,
    val message: String,
    val visibleCouponText: String? = null,
    val savingsAmount: Long? = null,
    val warning: String? = null
)

data class GroceryBookingSession(
    var request: GroceryBookingRequest,
    var state: GroceryBookingState = GroceryBookingState.IDLE,
    var basket: GroceryBasket = request.basket,
    var requirementProfile: GroceryRequirementProfile = request.toRequirementProfile(),
    var providerPreference: GroceryProvider? = request.preferredProvider,
    var brandPreference: String? = request.brandPreference,
    var budgetPreference: GroceryBudgetPreference? = request.budgetPreference,
    var deliveryUrgency: GroceryDeliveryUrgency? = request.deliveryUrgency,
    var deliveryLocation: String? = request.deliveryLocation,
    var useCurrentLocation: Boolean = request.useCurrentLocation,
    var paymentMethod: GroceryPaymentMethod? = request.paymentPreference,
    var wantsCheapest: Boolean = request.wantsCheapest,
    var wantsFirstOne: Boolean = request.wantsFirstOne,
    var compareRequested: Boolean = request.compareRequested,
    var applyCouponRequested: Boolean = request.applyCouponRequested,
    var couponCode: String? = request.couponCode,
    var finalConfirmationAsked: Boolean = false,
    var finalUserConfirmed: Boolean = request.finalUserConfirmed,
    var currentProvider: GroceryProvider? = null,
    var selectedCandidate: GroceryCartCandidate? = null,
    var comparisonResult: GroceryComparisonResult? = null,
    var finalSummary: GroceryOrderFinalSummary? = null,
    var orderConfirmation: GroceryOrderConfirmation? = null,
    var walletBalanceText: String? = null,
    var availableProviders: List<GroceryProvider> = emptyList(),
    val providerFailures: MutableMap<GroceryProvider, String> = linkedMapOf(),
    val skippedProviders: MutableMap<GroceryProvider, String> = linkedMapOf(),
    val providerResults: MutableList<GroceryProviderResult> = mutableListOf(),
    var manualActionReason: String? = null,
    var lastPrompt: String? = null,
    var pendingItemIndex: Int? = null,
    var pendingItemField: GroceryPendingItemField? = null,
    var activeSelectionIndex: Int? = null
) {
    fun toRequest(): GroceryBookingRequest {
        return request.copy(
            basket = basket,
            requirementProfile = requirementProfile,
            preferredProvider = providerPreference,
            brandPreference = brandPreference,
            budgetPreference = budgetPreference,
            deliveryUrgency = deliveryUrgency,
            deliveryLocation = deliveryLocation,
            useCurrentLocation = useCurrentLocation,
            paymentPreference = paymentMethod,
            wantsCheapest = wantsCheapest,
            wantsFirstOne = wantsFirstOne,
            compareRequested = compareRequested,
            applyCouponRequested = applyCouponRequested,
            couponCode = couponCode,
            finalUserConfirmed = finalUserConfirmed,
            reorderMode = requirementProfile.reorderMode,
            previousListMode = requirementProfile.previousListMode,
            allowComparison = requirementProfile.allowComparison,
            requiresFinalConfirmation = requirementProfile.requiresFinalConfirmation,
            safetyNotes = requirementProfile.safetyNotes
        )
    }
}

data class GroceryBookingResult(
    val state: GroceryBookingState,
    val message: String,
    val request: GroceryBookingRequest? = null,
    val comparisonResult: GroceryComparisonResult? = null,
    val selectedCandidate: GroceryCartCandidate? = null,
    val finalSummary: GroceryOrderFinalSummary? = null,
    val orderConfirmation: GroceryOrderConfirmation? = null,
    val providerResults: List<GroceryProviderResult> = emptyList(),
    val availableProviders: List<GroceryProvider> = emptyList(),
    val skippedProviders: Map<GroceryProvider, String> = emptyMap(),
    val providerFailures: Map<GroceryProvider, String> = emptyMap(),
    val currentProviderIndex: Int = 0,
    val finalConfirmationAsked: Boolean = false,
    val manualActionRequired: Boolean = false,
    val manualActionReason: String? = null,
    val finalUserConfirmed: Boolean = false,
    val bookingStatus: GroceryBookingStatus = state.toBookingStatus(),
    val currentState: GroceryBookingState = state
) {
    fun toEntities(): Map<String, String> {
        return buildMap {
            put("groceryState", state.name)
            put("bookingStatus", bookingStatus.name)
            put("currentState", currentState.name)
            put("finalConfirmationAsked", finalConfirmationAsked.toString())
            put("manualActionRequired", manualActionRequired.toString())
            put("currentProviderIndex", currentProviderIndex.toString())
            put("finalUserConfirmed", finalUserConfirmed.toString())
            manualActionReason?.takeIf { it.isNotBlank() }?.let { put("manualActionReason", it) }
            request?.let { putAll(it.toEntities()) }
            selectedCandidate?.let { candidate ->
                put("selectedProvider", candidate.provider.name)
                candidate.summary.finalPayableValue?.let { put("selectedFinalPayableValue", it.toString()) }
                candidate.summary.itemSubtotal?.let { put("selectedItemSubtotal", it.toString()) }
                candidate.summary.deliveryFee?.let { put("selectedDeliveryFee", it.toString()) }
                candidate.summary.handlingFee?.let { put("selectedHandlingFee", it.toString()) }
                candidate.summary.couponDiscount?.let { put("selectedCouponDiscount", it.toString()) }
                candidate.summary.etaText?.takeIf { it.isNotBlank() }?.let { put("selectedEtaText", it) }
                candidate.summary.etaMinutes?.let { put("selectedEtaMinutes", it.toString()) }
                candidate.summary.ratingText?.takeIf { it.isNotBlank() }?.let { put("selectedRatingText", it) }
                candidate.summary.ratingValue?.let { put("selectedRatingValue", it.toString()) }
                candidate.summary.packSizeText?.takeIf { it.isNotBlank() }?.let { put("selectedPackSizeText", it) }
                if (candidate.summary.unavailableItems.isNotEmpty()) {
                    put("selectedUnavailableItems", candidate.summary.unavailableItems.joinToString(separator = ","))
                }
                if (candidate.summary.replacementItems.isNotEmpty()) {
                    put("selectedReplacementItems", candidate.summary.replacementItems.joinToString(separator = ","))
                }
                candidate.summary.couponCode?.takeIf { it.isNotBlank() }?.let { put("selectedCouponCode", it) }
                candidate.summary.couponText?.takeIf { it.isNotBlank() }?.let { put("selectedCouponText", it) }
                put("selectedFinalCheckoutReady", candidate.finalCheckoutReady.toString())
                if (candidate.productOptions.isNotEmpty()) {
                    put("selectedProducts", candidate.productOptions.joinToString(separator = "|") { option ->
                        buildString {
                            append(option.title)
                            option.priceValue?.let { append(" @ ₹").append(it) }
                            option.packSizeText?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                        }
                    })
                }
            }
            finalSummary?.let { summary ->
                summary.appName?.takeIf { it.isNotBlank() }?.let { put("finalAppName", it) }
                summary.provider?.let { put("finalProvider", it.name) }
                summary.itemTotal?.let { put("finalItemTotal", it.toString()) }
                summary.deliveryFee?.let { put("finalDeliveryFee", it.toString()) }
                summary.handlingFee?.let { put("finalHandlingFee", it.toString()) }
                summary.couponSaving?.let { put("finalCouponSaving", it.toString()) }
                summary.finalPrice?.let { put("finalPrice", it.toString()) }
                summary.deliveryAddress?.takeIf { it.isNotBlank() }?.let { put("finalDeliveryAddress", it) }
                summary.deliveryTime?.takeIf { it.isNotBlank() }?.let { put("finalDeliveryTime", it) }
                summary.paymentMethod?.let { put("finalPaymentMethod", it.name) }
                summary.paymentOptions.joinToString(separator = ",") { it.name }.takeIf { it.isNotBlank() }?.let {
                    put("finalPaymentOptions", it)
                }
                if (summary.unavailableItems.isNotEmpty()) {
                    put("finalUnavailableItems", summary.unavailableItems.joinToString(separator = ","))
                }
                if (summary.replacedItems.isNotEmpty()) {
                    put("finalReplacedItems", summary.replacedItems.joinToString(separator = ","))
                }
                summary.bestCartSuggestion?.takeIf { it.isNotBlank() }?.let { put("bestCartSuggestion", it) }
                summary.bestCartReason?.takeIf { it.isNotBlank() }?.let { put("bestCartReason", it) }
                summary.orderId?.takeIf { it.isNotBlank() }?.let { put("finalOrderId", it) }
                summary.warning?.takeIf { it.isNotBlank() }?.let { put("finalWarning", it) }
            }
            orderConfirmation?.let { confirmation ->
                confirmation.orderId?.takeIf { it.isNotBlank() }?.let { put("orderId", it) }
                confirmation.provider?.let { put("orderConfirmationProvider", it.name) }
                confirmation.deliveryEstimate?.takeIf { it.isNotBlank() }?.let { put("deliveryEstimate", it) }
                confirmation.finalPrice?.let { put("confirmedFinalPrice", it.toString()) }
                confirmation.totalSavings?.let { put("confirmedTotalSavings", it.toString()) }
                put("orderPlaced", confirmation.placed.toString())
            }
            providerResults.takeIf { it.isNotEmpty() }?.let { results ->
                put("providerResults", results.joinToString(separator = ",") { it.provider.name })
            }
            if (availableProviders.isNotEmpty()) {
                put("availableProviders", availableProviders.joinToString(separator = ",") { it.name })
            }
            if (skippedProviders.isNotEmpty()) {
                put(
                    "skippedProviders",
                    skippedProviders.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
                )
            }
            if (providerFailures.isNotEmpty()) {
                put(
                    "providerFailures",
                    providerFailures.entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }
                )
            }
        }
    }

    fun toGroceryCommandResult(): CommandResult {
        val entities = toEntities()
        val blockedReason = manualActionReason
        val isPermissionBlocked = blockedReason == GroceryFailureReasons.BLOCKED_BY_ACCESSIBILITY_NOT_READY ||
            blockedReason == GroceryFailureReasons.BLOCKED_BY_LOCATION_PERMISSION ||
            blockedReason == GroceryFailureReasons.BLOCKED_BY_USAGE_ACCESS ||
            state == GroceryBookingState.PERMISSION_BLOCKED

        if (isPermissionBlocked) {
            return CommandResult.blocked(
                message = message,
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = entities
            )
        }

        return when (state) {
            GroceryBookingState.SHOWING_FINAL_SUMMARY,
            GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION -> CommandResult.confirmationRequired(
                message = message,
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = entities
            )

            GroceryBookingState.FAILED,
            GroceryBookingState.MANUAL_ACTION_REQUIRED -> CommandResult.failure(
                message = message,
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = entities
            )

            else -> CommandResult.success(
                message = message,
                intentType = IntentType.GROCERY_BOOKING,
                actionType = ActionType.GROCERY_BOOKING,
                entities = entities
            )
        }
    }
}

fun GroceryProvider.displayName(): String {
    return when (this) {
        GroceryProvider.BLINKIT -> "Blinkit"
        GroceryProvider.ZEPTO -> "Zepto"
        GroceryProvider.INSTAMART -> "Instamart"
        GroceryProvider.JIOMART -> "JioMart"
        GroceryProvider.BIGBASKET -> "BigBasket"
    }
}

fun GroceryManualActionReason.displayName(): String {
    return displayText
}

fun GroceryPaymentMethod.displayNameLabel(): String {
    return displayName()
}

fun GroceryBudgetPreference.displayName(): String {
    return when (this) {
        GroceryBudgetPreference.CHEAPEST -> "cheapest"
        GroceryBudgetPreference.BEST_QUALITY -> "best quality"
        GroceryBudgetPreference.FAST_DELIVERY -> "fast delivery"
        GroceryBudgetPreference.BEST_OVERALL -> "best overall"
    }
}

fun GroceryDeliveryUrgency.displayName(): String {
    return when (this) {
        GroceryDeliveryUrgency.NOW -> "now"
        GroceryDeliveryUrgency.TODAY -> "today"
        GroceryDeliveryUrgency.SCHEDULED -> "scheduled"
    }
}

fun GroceryBookingState.toBookingStatus(): GroceryBookingStatus {
    return when (this) {
        GroceryBookingState.IDLE -> GroceryBookingStatus.IDLE
        GroceryBookingState.COMPLETED -> GroceryBookingStatus.COMPLETED
        GroceryBookingState.FAILED -> GroceryBookingStatus.FAILED
        GroceryBookingState.CANCELLED -> GroceryBookingStatus.CANCELLED
        GroceryBookingState.PERMISSION_BLOCKED -> GroceryBookingStatus.BLOCKED
        GroceryBookingState.MANUAL_ACTION_REQUIRED -> GroceryBookingStatus.MANUAL_ACTION_REQUIRED
        GroceryBookingState.WAITING_FOR_FINAL_CONFIRMATION,
        GroceryBookingState.PARSING_REQUEST,
        GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE,
        GroceryBookingState.NEED_ITEMS,
        GroceryBookingState.NEED_PREVIOUS_LIST,
        GroceryBookingState.NEED_QUANTITY,
        GroceryBookingState.NEED_BRAND,
        GroceryBookingState.NEED_BUDGET_PREFERENCE,
        GroceryBookingState.NEED_DELIVERY_URGENCY,
        GroceryBookingState.NEED_DELIVERY_LOCATION,
        GroceryBookingState.NEED_REPLACEMENT_PREFERENCE,
        GroceryBookingState.CHECKING_PERMISSIONS,
        GroceryBookingState.CHECKING_PROVIDERS,
        GroceryBookingState.OPENING_PROVIDER,
        GroceryBookingState.SEARCHING_ITEMS,
        GroceryBookingState.READING_PRODUCT_OPTIONS,
        GroceryBookingState.MATCHING_PRODUCTS,
        GroceryBookingState.COMPARING_OPTIONS,
        GroceryBookingState.SHOWING_COMPARISON,
        GroceryBookingState.REFINING_SEARCH,
        GroceryBookingState.OPENING_SELECTED_PROVIDER,
        GroceryBookingState.ADDING_ITEMS,
        GroceryBookingState.HANDLING_UNAVAILABLE_ITEMS,
        GroceryBookingState.APPLYING_COUPON,
        GroceryBookingState.RECALCULATING_TOTAL,
        GroceryBookingState.SHOWING_FINAL_SUMMARY,
        GroceryBookingState.ASKING_PAYMENT_METHOD,
        GroceryBookingState.CHECKING_WALLET_BALANCE,
        GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION,
        GroceryBookingState.CHECKING_COD,
        GroceryBookingState.WAITING_FOR_COD_CONFIRMATION,
        GroceryBookingState.OPENING_PAYMENT_PAGE,
        GroceryBookingState.WAITING_FOR_ORDER_RESPONSE,
        GroceryBookingState.BOOKING,
        GroceryBookingState.SETTING_DELIVERY_LOCATION,
        GroceryBookingState.NO_PROVIDER_AVAILABLE -> GroceryBookingStatus.IN_PROGRESS
    }
}

fun CommandIntent.toGroceryBookingRequest(): GroceryBookingRequest {
    val itemCount = entities["itemCount"]?.toIntOrNull() ?: 0
    val basket = GroceryBasket()

    repeat(itemCount) { index ->
        val prefix = "item${index + 1}"
        val name = entities["${prefix}Name"]?.takeIf { it.isNotBlank() } ?: return@repeat
        basket.add(
            GroceryItem(
                name = name,
                quantityText = entities["${prefix}QuantityText"]?.takeIf { it.isNotBlank() },
                quantityValue = entities["${prefix}QuantityValue"]?.toDoubleOrNull(),
                unit = entities["${prefix}Unit"]?.takeIf { it.isNotBlank() },
                brand = entities["${prefix}Brand"]?.takeIf { it.isNotBlank() },
                rawText = entities["${prefix}RawText"]?.takeIf { it.isNotBlank() } ?: name
            )
        )
    }

    val providerPreference = (entities["providerPreference"] ?: entities["preferredProvider"])?.let { value ->
        GroceryProvider.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    val brandPreference = entities["brandPreference"]?.takeIf { it.isNotBlank() }

    val budgetPreference = entities["budgetPreference"]?.let { value ->
        GroceryBudgetPreference.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    val deliveryUrgency = entities["deliveryUrgency"]?.let { value ->
        GroceryDeliveryUrgency.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    val paymentPreference = entities["paymentPreference"]?.let { value ->
        GroceryPaymentMethod.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    val requirementProfile = entities["requirementBudgetPreference"]
        ?.let { GroceryBudgetPreference.entries.firstOrNull { value -> value.name.equals(it, ignoreCase = true) } }
        ?.let { requirementBudget ->
            GroceryRequirementProfile(
                items = basket.items.map { it.toItemRequest() },
                brandPreference = brandPreference,
                budgetPreference = requirementBudget,
                providerPreference = providerPreference,
                deliveryUrgency = entities["requirementDeliveryUrgency"]
                    ?.let { value -> GroceryDeliveryUrgency.entries.firstOrNull { urgency -> urgency.name.equals(value, ignoreCase = true) } }
                    ?: GroceryDeliveryUrgency.TODAY,
                scheduledTime = entities["scheduledTime"]?.takeIf { it.isNotBlank() },
                deliveryLocation = entities["deliveryLocation"]?.takeIf { it.isNotBlank() },
                useCurrentLocation = entities["useCurrentLocation"]?.toBooleanStrictOrNull() ?: false,
                allowComparison = entities["allowComparison"]?.toBooleanStrictOrNull() ?: true,
                reorderMode = entities["reorderMode"]?.toBooleanStrictOrNull() ?: false,
                previousListMode = entities["previousListMode"]?.toBooleanStrictOrNull() ?: false,
                requiresFinalConfirmation = entities["requiresFinalConfirmation"]?.toBooleanStrictOrNull() ?: true,
                paymentPreference = paymentPreference,
                safetyNotes = entities["safetyNotes"]?.takeIf { it.isNotBlank() }
            )
        }

    return GroceryBookingRequest(
        rawText = rawText,
        basket = basket,
        requirementProfile = requirementProfile,
        preferredProvider = providerPreference,
        brandPreference = brandPreference,
        budgetPreference = budgetPreference,
        deliveryUrgency = deliveryUrgency,
        scheduledTime = entities["scheduledTime"]?.takeIf { it.isNotBlank() },
        deliveryLocation = entities["deliveryLocation"]?.takeIf { it.isNotBlank() },
        useCurrentLocation = entities["useCurrentLocation"]?.toBooleanStrictOrNull() ?: false,
        paymentPreference = paymentPreference,
        allowComparison = entities["allowComparison"]?.toBooleanStrictOrNull() ?: true,
        reorderMode = entities["reorderMode"]?.toBooleanStrictOrNull() ?: false,
        previousListMode = entities["previousListMode"]?.toBooleanStrictOrNull() ?: false,
        wantsCheapest = entities["wantsCheapest"]?.toBooleanStrictOrNull() ?: false,
        wantsFirstOne = entities["wantsFirstOne"]?.toBooleanStrictOrNull() ?: false,
        compareRequested = entities["compareRequested"]?.toBooleanStrictOrNull() ?: false,
        applyCouponRequested = entities["applyCouponRequested"]?.toBooleanStrictOrNull() ?: false,
        couponCode = entities["couponCode"]?.takeIf { it.isNotBlank() },
        requiresFinalConfirmation = entities["requiresFinalConfirmation"]?.toBooleanStrictOrNull() ?: true,
        safetyNotes = entities["safetyNotes"]?.takeIf { it.isNotBlank() },
        finalUserConfirmed = entities["finalUserConfirmed"]?.toBooleanStrictOrNull() ?: false
    )
}
