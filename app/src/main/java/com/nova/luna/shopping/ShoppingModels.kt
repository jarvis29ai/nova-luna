package com.nova.luna.shopping

import com.nova.luna.model.CommandIntent

/**
 * Data models for the Nova/Luna Shopping domain.
 */

enum class ShoppingCommandType {
    SEARCH_PRODUCT,
    COMPARE_PRODUCTS,
    COMPARE_DEALS,
    BUY_PRODUCT,
    OPEN_SELECTED_DEAL,
    ADD_TO_CART,
    APPLY_COUPON,
    CONFIRM_PURCHASE,
    ASK_PAYMENT_METHOD,
    PAYMENT_MANUAL,
    COD_ORDER,
    WALLET_ORDER,
    REFINE_SEARCH,
    CANCEL,
    UNKNOWN
}

enum class ShoppingFlowState {
    IDLE,
    PARSING_REQUEST,
    UNDERSTANDING_PRODUCT_CATEGORY,
    ASKING_MISSING_DETAILS,
    CREATING_REQUIREMENT_PROFILE,
    SEARCHING_INTERNET_AND_APPS,
    SEARCHING_LATEST_PRODUCTS,
    SEARCHING_OFFICIAL_BRAND_WEBSITES,
    SEARCHING_TRUSTED_MARKETPLACES,
    SEARCHING_COUPONS_AND_OFFERS,
    READING_REVIEWS_RATINGS_DELIVERY_WARRANTY,
    COLLECTING_PRODUCT_OPTIONS,
    CHECKING_WEBSITE_AND_SELLER_TRUST,
    EXCLUDING_RISKY_OPTIONS,
    COMPARING_PRODUCTS_AND_DEALS,
    CALCULATING_FINAL_PAYABLE,
    CALCULATING_VALUE_SCORE,
    SELECTING_TOP_DEALS,
    SHOWING_USER_SUMMARY,
    WAITING_FOR_DEAL_SELECTION,
    REFINING_SEARCH,
    OPENING_SELECTED_WEBSITE_OR_APP,
    ADDING_PRODUCT_TO_CART,
    APPLYING_BEST_COUPON,
    RECALCULATING_FINAL_AMOUNT,
    SHOWING_FINAL_ORDER_SUMMARY,
    WAITING_FOR_FINAL_CONFIRMATION,
    ASKING_PAYMENT_METHOD,
    OPENING_PAYMENT_PAGE_MANUAL,
    CHECKING_WALLET_BALANCE,
    WAITING_FOR_WALLET_CONFIRMATION,
    CHECKING_COD,
    WAITING_FOR_COD_CONFIRMATION,
    PLACING_COD_ORDER,
    WAITING_FOR_ORDER_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

enum class ShoppingProductCategory {
    PHONE,
    LAPTOP,
    HEADPHONES,
    TELEVISION,
    WATCH,
    ELECTRONICS,
    FASHION,
    HOME,
    OTHER,
    UNKNOWN
}

enum class ShoppingPurpose {
    GAMING,
    WORK,
    PHOTOGRAPHY,
    STUDY,
    BATTERY,
    GENERAL,
    GIFT,
    HOME,
    FASHION,
    UNKNOWN
}

enum class ShoppingProviderType {
    OFFICIAL_BRAND_WEBSITE,
    MARKETPLACE,
    RETAIL_APP,
    BROWSER,
    UNKNOWN
}

enum class ShoppingProvider {
    AMAZON,
    FLIPKART,
    CROMA,
    RELIANCE_DIGITAL,
    OFFICIAL_BRAND,
    OTHER_TRUSTED_MARKETPLACE,
    UNKNOWN_WEBSITE
}

enum class ShoppingPaymentMethod {
    UPI,
    CARD,
    NET_BANKING,
    APP_WALLET,
    COD,
    MANUAL,
    UNKNOWN
}

enum class ShoppingStatus {
    SUCCESS,
    PARTIAL,
    BLOCKED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT,
    NEEDS_CONFIRMATION,
    MANUAL_ACTION_REQUIRED
}

enum class ShoppingTrustLevel {
    SAFE,
    CAUTION,
    RISKY,
    BLOCKED
}

data class ShoppingRequirementProfile(
    val category: ShoppingProductCategory = ShoppingProductCategory.UNKNOWN,
    val productName: String? = null,
    val budget: Double? = null,
    val purpose: ShoppingPurpose = ShoppingPurpose.UNKNOWN,
    val preferredBrand: String? = null,
    val color: String? = null,
    val storage: String? = null,
    val variant: String? = null,
    val deliveryUrgency: String? = null,
    val paymentPreference: ShoppingPaymentMethod = ShoppingPaymentMethod.UNKNOWN,
    val preferredWebsite: String? = null
)

data class ShoppingRequest(
    val commandType: ShoppingCommandType,
    val rawText: String,
    val productName: String? = null,
    val category: ShoppingProductCategory = ShoppingProductCategory.UNKNOWN,
    val budget: Double? = null,
    val purpose: ShoppingPurpose = ShoppingPurpose.UNKNOWN,
    val brand: String? = null,
    val website: String? = null,
    val comparisonIntent: Boolean = false,
    val buyIntent: Boolean = false
)

data class ShoppingProductOption(
    val id: String,
    val name: String,
    val brand: String,
    val category: ShoppingProductCategory,
    val price: Double,
    val imageUrl: String? = null,
    val provider: ShoppingProvider,
    val seller: String,
    val rating: Double,
    val reviewCount: Int,
    val deliveryDate: String? = null,
    val warranty: String? = null,
    val returnPolicy: String? = null,
    val couponOffers: List<String> = emptyList(),
    val bankOffers: List<String> = emptyList(),
    val exchangeOffers: List<String> = emptyList(),
    val availability: Boolean = true,
    val trustLevel: ShoppingTrustLevel = ShoppingTrustLevel.SAFE,
    val sourceUrl: String? = null
)

data class ShoppingDealOption(
    val product: ShoppingProductOption,
    val finalPayablePrice: Double,
    val totalSavings: Double,
    val valueForMoneyScore: Double,
    val rankingReason: String
)

data class ShoppingWebsiteTrustResult(
    val level: ShoppingTrustLevel,
    val signals: List<String> = emptyList(),
    val warningMessage: String? = null
)

data class ShoppingFinalOrderSummary(
    val product: ShoppingProductOption,
    val providerName: String,
    val sellerName: String,
    val finalPrice: Double,
    val totalSavings: Double,
    val deliveryDate: String?,
    val returnWarrantyPolicy: String?,
    val paymentOptions: List<ShoppingPaymentMethod>
)

data class ShoppingSession(
    val id: String,
    val state: ShoppingFlowState = ShoppingFlowState.IDLE,
    val requirementProfile: ShoppingRequirementProfile = ShoppingRequirementProfile(),
    val searchResults: List<ShoppingProductOption> = emptyList(),
    val topDeals: List<ShoppingDealOption> = emptyList(),
    val selectedDeal: ShoppingDealOption? = null,
    val finalOrderSummary: ShoppingFinalOrderSummary? = null,
    val lastResponse: String? = null,
    val lastVoiceResponse: String? = null
)

data class ShoppingResult(
    val status: ShoppingStatus,
    val popupText: String,
    val voiceText: String,
    val session: ShoppingSession? = null
)

fun CommandIntent.toShoppingRequest(): ShoppingRequest {
    // This will be used by ActionExecutor to hand off to ShoppingOrchestrator
    // For now, minimal parsing here, real parsing in ShoppingIntentParser
    return ShoppingRequest(
        commandType = ShoppingCommandType.UNKNOWN,
        rawText = this.rawText
    )
}
