package com.nova.luna.cab

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
class CabBookingOrchestratorTest {
    private lateinit var packageManager: PackageManager
    private lateinit var context: TestContext
    private lateinit var registry: CabProviderRegistry
    private lateinit var deepLinkBuilder: FakeCabDeepLinkBuilder
    private lateinit var accessibilityService: FakeCabAccessibilityService
    private lateinit var launchedIntents: MutableList<Intent>
    private lateinit var orchestrator: CabBookingOrchestrator

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        context = TestContext(ApplicationProvider.getApplicationContext(), packageManager)
        registry = CabProviderRegistry(packageManager)
        deepLinkBuilder = FakeCabDeepLinkBuilder(context, registry)
        accessibilityService = FakeCabAccessibilityService()
        launchedIntents = mutableListOf()
        orchestrator = CabBookingOrchestrator(
            providerRegistry = registry,
            deepLinkBuilder = deepLinkBuilder,
            accessibilityService = accessibilityService,
            pickupLocationResolver = null,
            providerLauncher = { intent ->
                launchedIntents.add(Intent(intent))
                true
            }
        )
    }

    @Test
    fun `missing pickup asks NEED_PICKUP`() {
        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to Bhopal railway station",
                dropLocation = "Bhopal railway station",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.NEED_PICKUP, result.state)
        assertEquals("What is your pickup location?", result.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `missing ride type asks NEED_RIDE_TYPE`() {
        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall"
            )
        )

        assertEquals(CabBookingState.NEED_RIDE_TYPE, result.state)
        assertEquals("Which ride type do you want? Auto, bike, mini, sedan, SUV, or any ride type?", result.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `missing provider app is skipped and reported`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "ETA 4 min")

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertEquals(listOf(CabProvider.UBER), result.availableProviders)
        assertTrue(result.skippedProviders.containsKey(CabProvider.OLA))
        assertTrue(result.skippedProviders.containsKey(CabProvider.RAPIDO))
        assertTrue(result.skippedProviders.containsKey(CabProvider.INDRIVE))
        assertTrue(result.message.contains("Skipped apps"))
    }

    @Test
    fun `final booking is blocked until user confirmation`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "ETA 4 min")
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, "₹139", "ETA 5 min")

        val comparison = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, comparison.state)
        assertEquals(2, launchedIntents.size)
        assertEquals(0, accessibilityService.finalConfirmTapCount)

        val selection = orchestrator.handleUserInput("Book the cheapest")
        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertNotNull(selection.selectedOption)
        assertEquals(CabProvider.UBER, selection.selectedOption?.provider)
        assertEquals(0, accessibilityService.finalConfirmTapCount)
        assertTrue(selection.message.contains("Should I confirm booking?"))

        val completed = orchestrator.handleUserInput("yes")
        assertEquals(CabBookingState.COMPLETED, completed.state)
        assertEquals(1, accessibilityService.finalConfirmTapCount)
        assertTrue(completed.message.contains("Booking completed"))
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `manual payment or login screen stops automation and asks for help`() {
        installProvider(CabProvider.UBER)
        accessibilityService.manualReason = "OTP"
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "ETA 4 min")

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertTrue(result.manualActionRequired)
        assertTrue(result.message.contains("OTP"))
        assertTrue(orchestrator.isActive())
    }

    private fun installProvider(provider: CabProvider) {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(registry.packageName(provider)))
            .thenReturn(Intent(Intent.ACTION_MAIN))
    }

    private fun fareOption(provider: CabProvider, fareText: String, etaText: String): CabFareOption {
        return CabFareOption(
            provider = provider,
            rideType = RideType.AUTO,
            visibleFareText = fareText,
            finalFareText = fareText,
            etaText = etaText
        )
    }

    private class FakeCabDeepLinkBuilder(
        context: Context,
        registry: CabProviderRegistry
    ) : CabDeepLinkBuilder(context, registry) {
        override fun buildLaunchIntent(
            provider: CabProvider,
            request: CabBookingRequest,
            selectedOption: CabFareOption?
        ): Intent? {
            return Intent(Intent.ACTION_VIEW).apply {
                putExtra("provider", provider.name)
            }
        }
    }

    private class FakeCabAccessibilityService : CabAccessibilityService() {
        var manualReason: String? = null
        var finalConfirmTapCount = 0
        val fareByProvider = mutableMapOf<CabProvider, CabFareOption>()

        override fun detectManualActionRequired(snapshot: CabScreenSnapshot?): String? {
            return manualReason
        }

        override fun fillTripDetails(request: CabBookingRequest): Boolean {
            return true
        }

        override fun collectFareOption(provider: CabProvider, request: CabBookingRequest): CabFareOption? {
            return fareByProvider[provider]
        }

        override fun tapFinalConfirmButton(finalUserConfirmed: Boolean): Boolean {
            if (!finalUserConfirmed || manualReason != null) {
                return false
            }
            finalConfirmTapCount += 1
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
