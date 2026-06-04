package com.nova.luna.grocery

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
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

data class GroceryBasket(
    val items: MutableList<GroceryItem> = mutableListOf()
) {
    fun isEmpty(): Boolean = items.isEmpty()

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
    JIOMART,
    INSTAMART
}

enum class GroceryBookingState {
    IDLE,
    PARSING_REQUEST,
    NEED_ITEMS,
    NEED_BRAND,
    CHECKING_PROVIDERS,
    OPENING_PROVIDER,
    SEARCHING_PROVIDER,
    ADDING_ITEMS,
    APPLYING_COUPON,
    COLLECTING_CART,
    SHOWING_COMPARISON,
    WAITING_FOR_PROVIDER_CHOICE,
    WAITING_FOR_FINAL_CONFIRMATION,
    BOOKING,
    COMPLETED,
    CANCELLED,
    FAILED,
    MANUAL_ACTION_REQUIRED
}

enum class GroceryManualActionReason(val displayText: String) {
    LOGIN("login"),
    OTP("OTP"),
    PAYMENT("payment"),
    CAPTCHA("captcha"),
    ADDRESS("address"),
    REPLACEMENT("replacement"),
    UNAVAILABLE_ITEMS("unavailable items"),
    MANUAL_SCREEN("manual screen"),
    PERMISSION("permission"),
    UNKNOWN("manual action")
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
    UNKNOWN
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
    val finalUserConfirmed: Boolean = false
)

data class GroceryIntentParseResult(
    val rawText: String,
    val isGroceryBooking: Boolean,
    val basket: GroceryBasket = GroceryBasket(),
    val providerPreference: GroceryProvider? = null,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val compareRequested: Boolean = false,
    val applyCouponRequested: Boolean = false,
    val couponCode: String? = null,
    val brandPreference: String? = null,
    val requiresBrandQuestion: Boolean = false,
    val requiresQuantityQuestion: Boolean = false,
    val itemCountHint: Int = basket.items.size
) {
    fun toBookingRequest(): GroceryBookingRequest {
        return GroceryBookingRequest(
            rawText = rawText,
            basket = basket,
            preferredProvider = providerPreference,
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
            brandPreference?.takeIf { it.isNotBlank() }?.let { put("brandPreference", it) }
        }
    }
}

data class GroceryBookingRequest(
    val rawText: String,
    val basket: GroceryBasket = GroceryBasket(),
    val preferredProvider: GroceryProvider? = null,
    val wantsCheapest: Boolean = false,
    val wantsFirstOne: Boolean = false,
    val compareRequested: Boolean = false,
    val applyCouponRequested: Boolean = false,
    val couponCode: String? = null,
    val finalUserConfirmed: Boolean = false
) {
    fun toEntities(): Map<String, String> {
        return buildMap {
            put("rawText", rawText)
            put("itemCount", basket.items.size.toString())
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
            put("wantsCheapest", wantsCheapest.toString())
            put("wantsFirstOne", wantsFirstOne.toString())
            put("compareRequested", compareRequested.toString())
            put("applyCouponRequested", applyCouponRequested.toString())
            couponCode?.takeIf { it.isNotBlank() }?.let { put("couponCode", it) }
            put("finalUserConfirmed", finalUserConfirmed.toString())
        }
    }
}

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
    val unavailableItems: List<String> = emptyList(),
    val replacementItems: List<String> = emptyList(),
    val couponCode: String? = null,
    val couponText: String? = null,
    val couponApplied: Boolean = false,
    val packageName: String? = null,
    val sourceText: String? = null
)

data class GroceryCartCandidate(
    val provider: GroceryProvider,
    val basket: GroceryBasket,
    val summary: GroceryCartSummary,
    val searchQueries: List<String> = emptyList(),
    val manualActionReason: GroceryManualActionReason? = null,
    val finalCheckoutReady: Boolean = false
)

data class GroceryComparisonResult(
    val candidates: List<GroceryCartCandidate>,
    val recommendedCandidate: GroceryCartCandidate? = null,
    val cheapestCompleteCandidate: GroceryCartCandidate? = null,
    val fastestCandidate: GroceryCartCandidate? = null
)

data class GroceryCouponResult(
    val provider: GroceryProvider,
    val couponCode: String? = null,
    val applied: Boolean = false,
    val found: Boolean = false,
    val message: String,
    val visibleCouponText: String? = null
)

data class GroceryBookingSession(
    val rawText: String,
    var state: GroceryBookingState = GroceryBookingState.IDLE,
    var basket: GroceryBasket = GroceryBasket(),
    var providerPreference: GroceryProvider? = null,
    var brandPreference: String? = null,
    var wantsCheapest: Boolean = false,
    var wantsFirstOne: Boolean = false,
    var compareRequested: Boolean = false,
    var applyCouponRequested: Boolean = false,
    var couponCode: String? = null,
    var finalConfirmationAsked: Boolean = false,
    var finalUserConfirmed: Boolean = false,
    var currentProvider: GroceryProvider? = null,
    var selectedCandidate: GroceryCartCandidate? = null,
    var comparisonResult: GroceryComparisonResult? = null,
    var availableProviders: List<GroceryProvider> = emptyList(),
    val providerFailures: MutableMap<GroceryProvider, String> = linkedMapOf(),
    val skippedProviders: MutableMap<GroceryProvider, String> = linkedMapOf(),
    var manualActionReason: String? = null,
    var lastProviderText: String? = null,
    var lastPrompt: String? = null
) {
    fun toRequest(): GroceryBookingRequest {
        return GroceryBookingRequest(
            rawText = rawText,
            basket = basket,
            preferredProvider = providerPreference,
            wantsCheapest = wantsCheapest,
            wantsFirstOne = wantsFirstOne,
            compareRequested = compareRequested,
            applyCouponRequested = applyCouponRequested,
            couponCode = couponCode,
            finalUserConfirmed = finalUserConfirmed
        )
    }
}

data class GroceryBookingResult(
    val state: GroceryBookingState,
    val message: String,
    val request: GroceryBookingRequest? = null,
    val comparisonResult: GroceryComparisonResult? = null,
    val selectedCandidate: GroceryCartCandidate? = null,
    val availableProviders: List<GroceryProvider> = emptyList(),
    val skippedProviders: Map<GroceryProvider, String> = emptyMap(),
    val providerFailures: Map<GroceryProvider, String> = emptyMap(),
    val currentProviderIndex: Int = 0,
    val finalConfirmationAsked: Boolean = false,
    val manualActionRequired: Boolean = false,
    val manualActionReason: String? = null,
    val finalUserConfirmed: Boolean = false,
    val currentState: GroceryBookingState = state
) {
    fun toEntities(): Map<String, String> {
        return buildMap {
            put("groceryState", state.name)
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
                candidate.summary.deliveryFee?.let { put("selectedDeliveryFee", it.toString()) }
                candidate.summary.handlingFee?.let { put("selectedHandlingFee", it.toString()) }
                candidate.summary.couponDiscount?.let { put("selectedCouponDiscount", it.toString()) }
                candidate.summary.etaText?.takeIf { it.isNotBlank() }?.let { put("selectedEtaText", it) }
                candidate.summary.etaMinutes?.let { put("selectedEtaMinutes", it.toString()) }
                if (candidate.summary.unavailableItems.isNotEmpty()) {
                    put("selectedUnavailableItems", candidate.summary.unavailableItems.joinToString(separator = ","))
                }
                candidate.summary.couponCode?.takeIf { it.isNotBlank() }?.let { put("selectedCouponCode", it) }
                candidate.summary.couponText?.takeIf { it.isNotBlank() }?.let { put("selectedCouponText", it) }
                put("selectedFinalCheckoutReady", candidate.finalCheckoutReady.toString())
            }
            comparisonResult?.let { comparison ->
                if (comparison.candidates.isNotEmpty()) {
                    put("comparisonProviders", comparison.candidates.joinToString(separator = ",") { it.provider.name })
                }
                comparison.recommendedCandidate?.let { put("recommendedProvider", it.provider.name) }
                comparison.cheapestCompleteCandidate?.let { put("cheapestCompleteProvider", it.provider.name) }
                comparison.fastestCandidate?.let { put("fastestProvider", it.provider.name) }
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
        return when (state) {
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
        GroceryProvider.JIOMART -> "JioMart"
        GroceryProvider.INSTAMART -> "Instamart"
    }
}

fun GroceryManualActionReason.displayName(): String {
    return displayText
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
        GroceryProvider.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    return GroceryBookingRequest(
        rawText = rawText,
        basket = basket,
        preferredProvider = providerPreference,
        wantsCheapest = entities["wantsCheapest"]?.toBooleanStrictOrNull() ?: false,
        wantsFirstOne = entities["wantsFirstOne"]?.toBooleanStrictOrNull() ?: false,
        compareRequested = entities["compareRequested"]?.toBooleanStrictOrNull() ?: false,
        applyCouponRequested = entities["applyCouponRequested"]?.toBooleanStrictOrNull() ?: false,
        couponCode = entities["couponCode"]?.takeIf { it.isNotBlank() },
        finalUserConfirmed = entities["finalUserConfirmed"]?.toBooleanStrictOrNull() ?: false
    )
}
