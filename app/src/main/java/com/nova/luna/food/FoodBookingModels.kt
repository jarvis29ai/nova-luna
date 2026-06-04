package com.nova.luna.food

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType

enum class FoodProvider {
    SWIGGY,
    ZOMATO,
    TOINGS
}

data class FoodBookingRequest(
    val rawText: String,
    val foodItem: String? = null,
    val restaurantName: String? = null,
    val quantity: Int? = null,
    val preferredProvider: FoodProvider? = null,
    val requestedProviders: List<FoodProvider> = emptyList(),
    val deliveryLocation: String? = null,
    val couponPreference: String? = null,
    val finalUserConfirmed: Boolean = false
) {
    fun toSearchTarget(provider: FoodProvider): FoodSearchTarget {
        return FoodSearchTarget(
            provider = provider,
            foodItem = foodItem.orEmpty().trim(),
            restaurantName = restaurantName?.takeIf { it.isNotBlank() },
            quantity = quantity?.takeIf { it > 0 } ?: 1,
            deliveryLocation = deliveryLocation?.takeIf { it.isNotBlank() },
            couponPreference = couponPreference?.takeIf { it.isNotBlank() }
        )
    }
}

data class FoodBookingSession(
    var request: FoodBookingRequest,
    var state: FoodBookingState = FoodBookingState.IDLE,
    val availableProviders: MutableList<FoodProvider> = mutableListOf(),
    val skippedProviders: MutableMap<FoodProvider, String> = linkedMapOf(),
    val platformQuotes: MutableList<FoodPlatformQuote> = mutableListOf(),
    var selectedQuote: FoodPlatformQuote? = null,
    var manualActionReason: String? = null
)

data class FoodSearchTarget(
    val provider: FoodProvider,
    val foodItem: String,
    val restaurantName: String? = null,
    val quantity: Int = 1,
    val deliveryLocation: String? = null,
    val couponPreference: String? = null
) {
    fun searchQuery(): String {
        val terms = buildList {
            restaurantName?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            foodItem.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        }

        return terms.joinToString(separator = " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

data class FoodCouponCandidate(
    val code: String? = null,
    val description: String? = null,
    val savingsText: String? = null,
    val discountAmount: Long? = null,
    val sourceText: String? = null,
    val applied: Boolean = false
)

data class FoodCartSnapshot(
    val visibleText: List<String>,
    val screenPackageName: String? = null,
    val foodItemText: String? = null,
    val restaurantText: String? = null,
    val visiblePriceText: String? = null,
    val finalPayableText: String? = null,
    val deliveryFeeText: String? = null,
    val taxText: String? = null,
    val couponText: String? = null,
    val discountText: String? = null,
    val etaText: String? = null,
    val manualActionReason: FoodManualActionReason? = null
)

data class FoodPlatformQuote(
    val provider: FoodProvider,
    val foodItem: String,
    val restaurantName: String? = null,
    val quantity: Int = 1,
    val visiblePriceText: String? = null,
    val visiblePriceAmount: Long? = null,
    val finalPayableText: String? = null,
    val finalPayableAmount: Long? = null,
    val deliveryFeeText: String? = null,
    val deliveryFeeAmount: Long? = null,
    val taxText: String? = null,
    val taxAmount: Long? = null,
    val couponText: String? = null,
    val discountText: String? = null,
    val discountAmount: Long? = null,
    val etaText: String? = null,
    val etaMinutes: Int? = null,
    val selectedCoupon: FoodCouponCandidate? = null,
    val packageName: String? = null
)

enum class FoodBookingState {
    IDLE,
    NEED_FOOD_ITEM,
    NEED_RESTAURANT,
    OPENING_PROVIDERS,
    COLLECTING_QUOTES,
    SHOWING_COMPARISON,
    WAITING_FOR_PLATFORM_CHOICE,
    WAITING_FOR_FINAL_CONFIRMATION,
    PREPARING_ORDER,
    PLACING_ORDER,
    COMPLETED,
    FAILED,
    MANUAL_ACTION_REQUIRED
}

enum class FoodManualActionReason {
    LOGIN,
    OTP,
    PAYMENT,
    CAPTCHA,
    PASSWORD,
    UPI,
    VERIFICATION,
    PERMISSION,
    MANUAL_CONFIRMATION,
    UNKNOWN;

    fun displayName(): String {
        return when (this) {
            LOGIN -> "login"
            OTP -> "OTP"
            PAYMENT -> "payment"
            CAPTCHA -> "captcha"
            PASSWORD -> "password"
            UPI -> "UPI"
            VERIFICATION -> "verification"
            PERMISSION -> "permission"
            MANUAL_CONFIRMATION -> "manual confirmation"
            UNKNOWN -> "manual action"
        }
    }
}

data class FoodComparisonResult(
    val state: FoodBookingState,
    val message: String,
    val request: FoodBookingRequest? = null,
    val platformQuotes: List<FoodPlatformQuote> = emptyList(),
    val selectedQuote: FoodPlatformQuote? = null,
    val availableProviders: List<FoodProvider> = emptyList(),
    val skippedProviders: Map<FoodProvider, String> = emptyMap(),
    val manualActionRequired: Boolean = false,
    val manualActionReason: String? = null,
    val finalUserConfirmed: Boolean = false
)

fun FoodProvider.displayName(): String {
    return when (this) {
        FoodProvider.SWIGGY -> "Swiggy"
        FoodProvider.ZOMATO -> "Zomato"
        FoodProvider.TOINGS -> "Toings"
    }
}

fun FoodBookingRequest.toEntities(): Map<String, String> {
    return buildMap {
        put("rawText", rawText)
        foodItem?.takeIf { it.isNotBlank() }?.let { put("foodItem", it) }
        restaurantName?.takeIf { it.isNotBlank() }?.let { put("restaurantName", it) }
        quantity?.let { put("quantity", it.toString()) }
        preferredProvider?.let { put("preferredProvider", it.name) }
        if (requestedProviders.isNotEmpty()) {
            put("requestedProviders", requestedProviders.joinToString(separator = ",") { it.name })
        }
        deliveryLocation?.takeIf { it.isNotBlank() }?.let { put("deliveryLocation", it) }
        couponPreference?.takeIf { it.isNotBlank() }?.let { put("couponPreference", it) }
        put("finalUserConfirmed", finalUserConfirmed.toString())
    }
}

fun FoodComparisonResult.toEntities(): Map<String, String> {
    return buildMap {
        put("foodState", state.name)
        put("manualActionRequired", manualActionRequired.toString())
        manualActionReason?.takeIf { it.isNotBlank() }?.let { put("manualActionReason", it) }
        request?.let { request -> putAll(request.toEntities()) }
        selectedQuote?.let { quote ->
            put("selectedProvider", quote.provider.name)
            put("selectedFoodItem", quote.foodItem)
            quote.restaurantName?.takeIf { it.isNotBlank() }?.let { put("selectedRestaurantName", it) }
            put("selectedQuantity", quote.quantity.toString())
            quote.visiblePriceText?.takeIf { it.isNotBlank() }?.let { put("selectedVisiblePriceText", it) }
            quote.visiblePriceAmount?.let { put("selectedVisiblePriceAmount", it.toString()) }
            quote.finalPayableText?.takeIf { it.isNotBlank() }?.let { put("selectedFinalPayableText", it) }
            quote.finalPayableAmount?.let { put("selectedFinalPayableAmount", it.toString()) }
            quote.deliveryFeeText?.takeIf { it.isNotBlank() }?.let { put("selectedDeliveryFeeText", it) }
            quote.deliveryFeeAmount?.let { put("selectedDeliveryFeeAmount", it.toString()) }
            quote.taxText?.takeIf { it.isNotBlank() }?.let { put("selectedTaxText", it) }
            quote.taxAmount?.let { put("selectedTaxAmount", it.toString()) }
            quote.couponText?.takeIf { it.isNotBlank() }?.let { put("selectedCouponText", it) }
            quote.discountText?.takeIf { it.isNotBlank() }?.let { put("selectedDiscountText", it) }
            quote.discountAmount?.let { put("selectedDiscountAmount", it.toString()) }
            quote.etaText?.takeIf { it.isNotBlank() }?.let { put("selectedEtaText", it) }
            quote.etaMinutes?.let { put("selectedEtaMinutes", it.toString()) }
            quote.selectedCoupon?.code?.takeIf { it.isNotBlank() }?.let { put("selectedCouponCode", it) }
            quote.selectedCoupon?.savingsText?.takeIf { it.isNotBlank() }?.let { put("selectedCouponSavingsText", it) }
            quote.packageName?.takeIf { it.isNotBlank() }?.let { put("selectedPackageName", it) }
        }
        if (platformQuotes.isNotEmpty()) {
            put("platformQuotes", platformQuotes.joinToString(separator = ",") { it.provider.name })
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
        put("finalUserConfirmed", finalUserConfirmed.toString())
    }
}

fun FoodComparisonResult.toCommandResult(): CommandResult {
    val entities = toEntities()
    return if (state == FoodBookingState.FAILED || state == FoodBookingState.MANUAL_ACTION_REQUIRED) {
        CommandResult.failure(
            message = message,
            intentType = IntentType.FOOD_ORDER,
            actionType = ActionType.FOOD_ORDER,
            entities = entities
        )
    } else {
        CommandResult.success(
            message = message,
            intentType = IntentType.FOOD_ORDER,
            actionType = ActionType.FOOD_ORDER,
            entities = entities
        )
    }
}

fun CommandIntent.toFoodBookingRequest(): FoodBookingRequest {
    val preferredProvider = entities["preferredProvider"]?.let { value ->
        FoodProvider.values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    val requestedProviders = entities["requestedProviders"]
        ?.split(",")
        ?.mapNotNull { value ->
            val normalized = value.trim()
            if (normalized.isBlank()) return@mapNotNull null
            FoodProvider.values().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        }
        ?.distinct()
        .orEmpty()

    return FoodBookingRequest(
        rawText = rawText,
        foodItem = entities["foodItem"]?.takeIf { it.isNotBlank() },
        restaurantName = entities["restaurantName"]?.takeIf { it.isNotBlank() },
        quantity = entities["quantity"]?.toIntOrNull(),
        preferredProvider = preferredProvider,
        requestedProviders = requestedProviders,
        deliveryLocation = entities["deliveryLocation"]?.takeIf { it.isNotBlank() },
        couponPreference = entities["couponPreference"]?.takeIf { it.isNotBlank() },
        finalUserConfirmed = entities["finalUserConfirmed"]?.toBooleanStrictOrNull() ?: false
    )
}
