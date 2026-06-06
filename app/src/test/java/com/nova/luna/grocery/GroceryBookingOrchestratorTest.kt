package com.nova.luna.grocery

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroceryBookingOrchestratorTest {
    private lateinit var packageManager: PackageManager
    private lateinit var context: TestContext
    private lateinit var registry: GroceryProviderRegistry
    private lateinit var deepLinkBuilder: FakeGroceryDeepLinkBuilder
    private lateinit var accessibilityService: FakeGroceryAccessibilityService
    private lateinit var launchedIntents: MutableList<Intent>
    private lateinit var orchestrator: GroceryBookingOrchestrator

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        context = TestContext(ApplicationProvider.getApplicationContext(), packageManager)
        registry = GroceryProviderRegistry(packageManager)
        deepLinkBuilder = FakeGroceryDeepLinkBuilder(context, registry)
        accessibilityService = FakeGroceryAccessibilityService()
        launchedIntents = mutableListOf()
        orchestrator = GroceryBookingOrchestrator(
            providerRegistry = registry,
            deepLinkBuilder = deepLinkBuilder,
            accessibilityService = accessibilityService,
            accessibilityReadyProvider = { true },
            permissionStatusProvider = { GroceryPermissionStatus() },
            providerLauncher = GroceryProviderLauncher { intent ->
                launchedIntents.add(Intent(intent))
                true
            }
        )
    }

    @Test
    fun `reorder flow asks for the previous list before continuing`() {
        val result = orchestrator.start(
            GroceryBookingRequest(
                rawText = "reorder previous grocery list",
                basket = GroceryBasket(),
                budgetPreference = GroceryBudgetPreference.BEST_OVERALL,
                deliveryUrgency = GroceryDeliveryUrgency.TODAY,
                deliveryLocation = "Home",
                reorderMode = true,
                previousListMode = true
            )
        )

        assertEquals(GroceryBookingState.NEED_PREVIOUS_LIST, result.state)
        assertTrue(result.message.contains("previous", ignoreCase = true))
        assertNotNull(orchestrator.currentSession())
        assertEquals(GroceryBookingState.NEED_PREVIOUS_LIST, orchestrator.currentState())
    }

    @Test
    fun `comparison flow can move through coupon wallet confirmation and still stop before final purchase`() {
        installProvider(GroceryProvider.BLINKIT)
        installProvider(GroceryProvider.JIOMART)

        accessibilityService.setCandidate(
            GroceryProvider.BLINKIT,
            groceryCandidate(
                provider = GroceryProvider.BLINKIT,
                itemSubtotal = 320L,
                deliveryFee = 20L,
                handlingFee = 10L,
                couponDiscount = 20L,
                finalPayableValue = 330L,
                etaMinutes = 35,
                couponCode = "SAVE20"
            )
        )
        accessibilityService.setCandidate(
            GroceryProvider.JIOMART,
            groceryCandidate(
                provider = GroceryProvider.JIOMART,
                itemSubtotal = 280L,
                deliveryFee = 10L,
                handlingFee = 10L,
                couponDiscount = 30L,
                finalPayableValue = 270L,
                etaMinutes = 45,
                couponCode = "SAVE30"
            )
        )
        accessibilityService.walletBalance = 1_000L
        accessibilityService.tapFinalOrderButtonResult = false

        val startResult = orchestrator.start(
            GroceryBookingRequest(
                rawText = "compare prices for milk and bread",
                basket = groceryBasket("milk", "bread"),
                brandPreference = "regular",
                budgetPreference = GroceryBudgetPreference.BEST_OVERALL,
                deliveryUrgency = GroceryDeliveryUrgency.TODAY,
                deliveryLocation = "Home",
                compareRequested = true,
                applyCouponRequested = true,
                couponCode = "SAVE30"
            )
        )

        assertEquals(GroceryBookingState.WAITING_FOR_PROVIDER_CHOICE, startResult.state)
        assertNotNull(startResult.comparisonResult)
        assertEquals(2, startResult.availableProviders.size)
        assertEquals(2, startResult.providerResults.size)
        assertEquals(2, startResult.comparisonResult?.candidates?.size)
        assertEquals(2, launchedIntents.size)
        assertTrue(startResult.message.contains("comparison", ignoreCase = true))

        val cheapest = orchestrator.handleUserInput("cheapest")
        assertEquals(GroceryBookingState.SHOWING_FINAL_SUMMARY, cheapest.state)
        assertNotNull(cheapest.finalSummary)
        assertEquals(GroceryProvider.JIOMART, cheapest.finalSummary?.provider)
        assertEquals(30L, cheapest.finalSummary?.couponSaving)
        assertTrue(cheapest.message.contains("confirm", ignoreCase = true))

        val paymentPrompt = orchestrator.handleUserInput("yes")
        assertEquals(GroceryBookingState.ASKING_PAYMENT_METHOD, paymentPrompt.state)
        assertTrue(paymentPrompt.message.contains("payment", ignoreCase = true))

        val walletPrompt = orchestrator.handleUserInput("pay with wallet")
        assertEquals(GroceryBookingState.WAITING_FOR_WALLET_CONFIRMATION, walletPrompt.state)
        assertNotNull(walletPrompt.finalSummary)
        assertEquals("₹1000", orchestrator.currentSession()?.walletBalanceText)

        val manualBoundary = orchestrator.handleUserInput("yes")
        assertEquals(GroceryBookingState.MANUAL_ACTION_REQUIRED, manualBoundary.state)
        assertTrue(manualBoundary.manualActionRequired)
        assertNull(manualBoundary.orderConfirmation)
        assertTrue(manualBoundary.message.contains("manual", ignoreCase = true))
        assertTrue(manualBoundary.message.contains("order", ignoreCase = true) || manualBoundary.message.contains("payment", ignoreCase = true))
        assertTrue(orchestrator.currentState() == GroceryBookingState.MANUAL_ACTION_REQUIRED)
    }

    @Test
    fun `provider specific requests stay on the chosen provider`() {
        installProvider(GroceryProvider.BLINKIT)

        accessibilityService.setCandidate(
            GroceryProvider.BLINKIT,
            groceryCandidate(
                provider = GroceryProvider.BLINKIT,
                itemSubtotal = 410L,
                deliveryFee = 20L,
                handlingFee = 10L,
                couponDiscount = 0L,
                finalPayableValue = 440L,
                etaMinutes = 30,
                couponCode = null
            )
        )

        val result = orchestrator.start(
            GroceryBookingRequest(
                rawText = "order from Blinkit",
                basket = groceryBasket("milk", "bread"),
                brandPreference = "regular",
                preferredProvider = GroceryProvider.BLINKIT,
                budgetPreference = GroceryBudgetPreference.BEST_OVERALL,
                deliveryUrgency = GroceryDeliveryUrgency.TODAY,
                deliveryLocation = "Home"
            )
        )

        assertEquals(GroceryBookingState.SHOWING_FINAL_SUMMARY, result.state)
        assertNotNull(result.finalSummary)
        assertEquals(GroceryProvider.BLINKIT, result.finalSummary?.provider)
        assertEquals(1, result.providerResults.size)
        assertTrue(result.message.contains("confirm", ignoreCase = true))
    }

    private fun installProvider(provider: GroceryProvider) {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(registry.packageName(provider)))
            .thenReturn(Intent(Intent.ACTION_MAIN))
    }

    private fun groceryBasket(vararg names: String): GroceryBasket {
        return GroceryBasket(
            names.map { name ->
                GroceryItem(
                    name = name,
                    quantityText = "1",
                    quantityValue = 1.0,
                    rawText = name
                )
            }.toMutableList()
        )
    }

    private fun groceryCandidate(
        provider: GroceryProvider,
        itemSubtotal: Long,
        deliveryFee: Long,
        handlingFee: Long,
        couponDiscount: Long,
        finalPayableValue: Long,
        etaMinutes: Int,
        couponCode: String?
    ): GroceryCartCandidate {
        val options = listOf(
            GroceryProductOption(
                itemName = "milk",
                title = "milk",
                priceText = "₹180",
                priceValue = 180L
            ),
            GroceryProductOption(
                itemName = "bread",
                title = "bread",
                priceText = "₹140",
                priceValue = 140L
            )
        )
        val summary = GroceryCartSummary(
            provider = provider,
            itemSubtotal = itemSubtotal,
            deliveryFee = deliveryFee,
            handlingFee = handlingFee,
            couponDiscount = couponDiscount,
            finalPayableValue = finalPayableValue,
            etaText = "$etaMinutes min",
            etaMinutes = etaMinutes,
            productOptions = options,
            couponCode = couponCode,
            couponText = couponCode?.let { "Coupon $it" },
            couponApplied = couponCode != null,
            partial = false,
            blocked = false
        )
        return GroceryCartCandidate(
            provider = provider,
            basket = groceryBasket("milk", "bread"),
            summary = summary,
            productOptions = options,
            searchQueries = listOf("milk", "bread"),
            providerResult = GroceryProviderResult(
                provider = provider,
                productOptions = options,
                summary = summary,
                blocked = false,
                partial = false,
                searchQueries = listOf("milk", "bread")
            ),
            finalCheckoutReady = true
        )
    }

    private class FakeGroceryDeepLinkBuilder(
        context: Context,
        providerRegistry: GroceryProviderRegistry
    ) : GroceryDeepLinkBuilder(context, providerRegistry) {
        override fun buildLaunchPlan(
            provider: GroceryProvider,
            request: GroceryBookingRequest,
            selectedCandidate: GroceryCartCandidate?
        ): GroceryDeepLinkResult {
            return GroceryDeepLinkResult(
                provider = provider,
                intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(provider.name.lowercase())
                },
                searchIntents = request.basket.items.map { item ->
                    Intent(Intent.ACTION_SEARCH).apply {
                        putExtra("query", item.displayText())
                    }
                },
                launched = true,
                supportsDirectBasketIntent = false,
                needsAccessibilityFill = true,
                failureReason = null,
                launchMode = "test"
            )
        }
    }

    private class FakeGroceryAccessibilityService : GroceryAccessibilityService() {
        private val candidateByProvider = linkedMapOf<GroceryProvider, GroceryCartCandidate>()
        var walletBalance: Long? = 1_000L
        var codAvailable: Boolean? = true
        var tapFinalOrderButtonResult: Boolean = false

        fun setCandidate(provider: GroceryProvider, candidate: GroceryCartCandidate) {
            candidateByProvider[provider] = candidate
        }

        override fun collectCartCandidate(
            provider: GroceryProvider,
            basket: GroceryBasket,
            couponCode: String?,
            requirementProfile: GroceryRequirementProfile?
        ): GroceryCartCandidate {
            val candidate = candidateByProvider.getValue(provider)
            if (couponCode.isNullOrBlank()) return candidate

            val adjustedSummary = candidate.summary.copy(
                couponCode = couponCode,
                couponText = "Coupon $couponCode",
                couponApplied = true,
                couponDiscount = candidate.summary.couponDiscount ?: 0L
            )
            return candidate.copy(
                summary = adjustedSummary,
                providerResult = candidate.providerResult?.copy(summary = adjustedSummary)
            )
        }

        override fun detectWalletBalance(snapshot: GroceryScreenSnapshot?): Long? {
            return walletBalance
        }

        override fun detectCodAvailability(snapshot: GroceryScreenSnapshot?): Boolean? {
            return codAvailable
        }

        override fun tapFinalOrderButton(): Boolean {
            return tapFinalOrderButtonResult
        }

        override fun waitForForegroundPackage(
            expectedPackageNames: Set<String>,
            attempts: Int,
            totalWaitMs: Long
        ): String? {
            return expectedPackageNames.firstOrNull()
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
