package com.nova.luna.food

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodBookingOrchestratorTest {
    private lateinit var packageManager: PackageManager
    private lateinit var context: TestContext
    private lateinit var registry: FoodProviderRegistry
    private lateinit var deepLinkBuilder: FakeFoodDeepLinkBuilder
    private lateinit var accessibilityService: FakeFoodAccessibilityService
    private lateinit var launchedIntents: MutableList<Intent>
    private lateinit var orchestrator: FoodBookingOrchestrator

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        context = TestContext(ApplicationProvider.getApplicationContext(), packageManager)
        registry = FoodProviderRegistry(packageManager)
        deepLinkBuilder = FakeFoodDeepLinkBuilder(context, registry)
        accessibilityService = FakeFoodAccessibilityService()
        launchedIntents = mutableListOf()
        orchestrator = FoodBookingOrchestrator(
            providerRegistry = registry,
            deepLinkBuilder = deepLinkBuilder,
            accessibilityService = accessibilityService,
            providerLauncher = { intent ->
                launchedIntents.add(Intent(intent))
                true
            }
        )
    }

    @Test
    fun `missing food item asks what would you like to order`() {
        val result = orchestrator.start(
            FoodBookingRequest(
                rawText = "order from XYZ restaurant",
                restaurantName = "XYZ restaurant"
            )
        )

        assertEquals(FoodBookingState.NEED_FOOD_ITEM, result.state)
        assertEquals("What would you like to order?", result.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `missing restaurant asks which restaurant should I search for`() {
        val result = orchestrator.start(
            FoodBookingRequest(
                rawText = "order burger",
                foodItem = "burger"
            )
        )

        assertEquals(FoodBookingState.NEED_RESTAURANT, result.state)
        assertEquals("Which restaurant should I search for?", result.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `comparison sorts prices and explicit confirmation is required before the final place order tap`() {
        installProvider(FoodProvider.SWIGGY)
        installProvider(FoodProvider.ZOMATO)
        installProvider(FoodProvider.TOINGS)

        accessibilityService.quoteByProvider[FoodProvider.SWIGGY] = quote(
            provider = FoodProvider.SWIGGY,
            finalPayable = 240L,
            couponCode = "SAVE50",
            etaMinutes = 28,
            deliveryFee = 25L,
            tax = 18L
        )
        accessibilityService.quoteByProvider[FoodProvider.ZOMATO] = quote(
            provider = FoodProvider.ZOMATO,
            finalPayable = 220L,
            couponCode = "WELCOME",
            etaMinutes = 31,
            deliveryFee = 20L,
            tax = 16L
        )
        accessibilityService.quoteByProvider[FoodProvider.TOINGS] = quote(
            provider = FoodProvider.TOINGS,
            finalPayable = 250L,
            couponCode = null,
            etaMinutes = 25,
            deliveryFee = 22L,
            tax = 19L
        )

        val comparison = orchestrator.start(
            FoodBookingRequest(
                rawText = "order burger",
                foodItem = "burger",
                restaurantName = "Burger House"
            )
        )

        assertEquals(FoodBookingState.SHOWING_COMPARISON, comparison.state)
        assertTrue(comparison.message.contains("Zomato: \u20B9220 final"))
        assertTrue(comparison.message.contains("Swiggy: \u20B9240 final"))
        assertTrue(comparison.message.contains("Toings: \u20B9250 final"))
        assertEquals(listOf(FoodProvider.ZOMATO, FoodProvider.SWIGGY, FoodProvider.TOINGS), comparison.platformQuotes.map { it.provider })
        assertEquals(3, launchedIntents.size)

        val selection = orchestrator.handleUserInput("Book cheapest")

        assertEquals(FoodBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertNotNull(selection.selectedQuote)
        assertEquals(FoodProvider.ZOMATO, selection.selectedQuote?.provider)
        assertTrue(selection.message.contains("Confirm placing order on Zomato for \u20B9220?"))
        assertEquals(0, accessibilityService.finalPlaceOrderTapCount)

        val completed = orchestrator.handleUserInput("yes confirm")

        assertEquals(FoodBookingState.COMPLETED, completed.state)
        assertTrue(completed.message.contains("Order handoff completed"))
        assertEquals(1, accessibilityService.finalPlaceOrderTapCount)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `single requested platform stays scoped to that platform only`() {
        installProvider(FoodProvider.SWIGGY)
        installProvider(FoodProvider.ZOMATO)
        installProvider(FoodProvider.TOINGS)

        accessibilityService.quoteByProvider[FoodProvider.SWIGGY] = quote(
            provider = FoodProvider.SWIGGY,
            finalPayable = 240L,
            couponCode = "SAVE50",
            etaMinutes = 28,
            deliveryFee = 25L,
            tax = 18L
        )
        accessibilityService.quoteByProvider[FoodProvider.ZOMATO] = quote(
            provider = FoodProvider.ZOMATO,
            finalPayable = 220L,
            couponCode = "WELCOME",
            etaMinutes = 31,
            deliveryFee = 20L,
            tax = 16L
        )
        accessibilityService.quoteByProvider[FoodProvider.TOINGS] = quote(
            provider = FoodProvider.TOINGS,
            finalPayable = 250L,
            couponCode = null,
            etaMinutes = 25,
            deliveryFee = 22L,
            tax = 19L
        )

        val comparison = orchestrator.start(
            FoodBookingRequest(
                rawText = "order burger from McDonalds on Swiggy",
                foodItem = "burger",
                restaurantName = "McDonalds",
                requestedProviders = listOf(FoodProvider.SWIGGY)
            )
        )

        assertEquals(FoodBookingState.SHOWING_COMPARISON, comparison.state)
        assertEquals(listOf(FoodProvider.SWIGGY), comparison.platformQuotes.map { it.provider })
        assertEquals(1, launchedIntents.size)
    }

    @Test
    fun `manual payment or login screen stops automation and asks for help`() {
        installProvider(FoodProvider.SWIGGY)
        accessibilityService.manualReason = "OTP"
        accessibilityService.quoteByProvider[FoodProvider.SWIGGY] = quote(
            provider = FoodProvider.SWIGGY,
            finalPayable = 240L,
            couponCode = "SAVE50",
            etaMinutes = 28
        )

        val result = orchestrator.start(
            FoodBookingRequest(
                rawText = "order burger",
                foodItem = "burger",
                restaurantName = "Burger House"
            )
        )

        assertEquals(FoodBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertTrue(result.manualActionRequired)
        assertTrue(result.message.contains("OTP"))
        assertTrue(orchestrator.isActive())
    }

    private fun installProvider(provider: FoodProvider) {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(registry.packageName(provider)))
            .thenReturn(Intent(Intent.ACTION_MAIN))
    }

    private fun quote(
        provider: FoodProvider,
        finalPayable: Long,
        couponCode: String?,
        etaMinutes: Int,
        deliveryFee: Long? = null,
        tax: Long? = null,
        discount: Long? = null
    ): FoodPlatformQuote {
        return FoodPlatformQuote(
            provider = provider,
            foodItem = "burger",
            restaurantName = "Burger House",
            quantity = 1,
            visiblePriceText = "\u20B9$finalPayable",
            visiblePriceAmount = finalPayable,
            finalPayableText = "\u20B9$finalPayable",
            finalPayableAmount = finalPayable,
            deliveryFeeText = deliveryFee?.let { "\u20B9$it" },
            deliveryFeeAmount = deliveryFee,
            taxText = tax?.let { "\u20B9$it" },
            taxAmount = tax,
            discountText = discount?.let { "\u20B9$it" },
            discountAmount = discount,
            etaText = "ETA $etaMinutes min",
            etaMinutes = etaMinutes,
            selectedCoupon = couponCode?.let {
                FoodCouponCandidate(
                    code = it,
                    savingsText = "coupon $it applied",
                    applied = true
                )
            },
            couponText = couponCode?.let { "coupon $it" }
        )
    }

    private class FakeFoodDeepLinkBuilder(
        context: Context,
        registry: FoodProviderRegistry
    ) : FoodDeepLinkBuilder(context, registry) {
        override fun buildLaunchIntent(
            provider: FoodProvider,
            request: FoodBookingRequest,
            selectedQuote: FoodPlatformQuote?
        ): Intent? {
            return Intent(Intent.ACTION_VIEW).apply {
                putExtra("provider", provider.name)
            }
        }
    }

    private class FakeFoodAccessibilityService : FoodAccessibilityService() {
        var manualReason: String? = null
        var finalPlaceOrderTapCount = 0
        val quoteByProvider = mutableMapOf<FoodProvider, FoodPlatformQuote>()

        override fun detectManualActionRequired(snapshot: FoodCartSnapshot?): String? {
            return manualReason
        }

        override fun fillOrderDetails(target: FoodSearchTarget): Boolean {
            return true
        }

        override fun tryApplyCoupon(target: FoodSearchTarget, couponPreference: String?): FoodCouponCandidate? {
            return quoteByProvider[target.provider]?.selectedCoupon
        }

        override fun collectPlatformQuote(provider: FoodProvider, target: FoodSearchTarget): FoodPlatformQuote? {
            return quoteByProvider[provider]
        }

        override fun awaitProviderForeground(
            expectedPackageName: String?,
            retries: Int,
            pollDelayMs: Long
        ): Boolean {
            return true
        }

        override fun tapFinalPlaceOrderButton(finalUserConfirmed: Boolean): Boolean {
            if (!finalUserConfirmed || manualReason != null) {
                return false
            }
            finalPlaceOrderTapCount += 1
            return true
        }
    }

    private class TestContext(
        baseContext: Context,
        private val packageManager: PackageManager
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this
        override fun getPackageManager(): PackageManager = packageManager
    }
}
