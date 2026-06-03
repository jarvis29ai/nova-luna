package com.nova.luna.cab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `missing drop asks NEED_DROP`() {
        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab from home",
                pickupLocation = "Home",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.NEED_DROP, result.state)
        assertEquals("Where do you want to go?", result.message)
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
        assertEquals("Which ride type do you want - auto, bike, mini, sedan, SUV, or any?", result.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `current location available skips pickup question`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "₹124", "ETA 5 min")

        orchestrator = createOrchestrator(
            pickupLocationResolver = CabPickupLocationResolver {
                CabPickupLocation("Current location", 23.0, 77.0)
            }
        )

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertEquals("Current location", result.request?.pickupLocation)
        assertFalse(result.message.contains("What is your pickup location?"))
    }

    @Test
    fun `no providers installed returns provider unavailable message`() {
        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.FAILED, result.state)
        assertEquals("I could not find Uber, Ola, Rapido, or inDrive installed on this phone.", result.message)
        assertTrue(result.skippedProviders.isNotEmpty())
    }

    @Test
    fun `provider failures are collected`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = null

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.FAILED, result.state)
        assertTrue(result.providerFailures.containsKey(CabProvider.UBER))
        assertTrue(result.message.contains("Provider issues"))
    }

    @Test
    fun `fare comparison is sorted low to high`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        installProvider(CabProvider.OLA)

        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹139", "₹124 after discount", "ETA 5 min")
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, "₹99", "₹99", "ETA 4 min")
        accessibilityService.fareByProvider[CabProvider.OLA] = fareOption(CabProvider.OLA, "₹188", "₹188", "ETA 6 min")

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertEquals(
            listOf(CabProvider.RAPIDO, CabProvider.UBER, CabProvider.OLA),
            result.fareOptions.map { it.provider }
        )
    }

    @Test
    fun `book cheapest selects lowest fare and waits for final confirmation`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹139", "₹124 after discount", "ETA 5 min")
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, "₹99", "₹99", "ETA 4 min")

        val comparison = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, comparison.state)

        val selection = orchestrator.handleUserInput("book the cheapest")
        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertEquals(CabProvider.RAPIDO, selection.selectedOption?.provider)
        assertEquals(0, accessibilityService.finalConfirmTapCount)

        val completed = orchestrator.handleUserInput("yes")
        assertEquals(CabBookingState.COMPLETED, completed.state)
        assertEquals(1, accessibilityService.finalConfirmTapCount)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `final booking is blocked before yes`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹139", "₹124 after discount", "ETA 5 min")
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, "₹99", "₹99", "ETA 4 min")

        orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        val selection = orchestrator.handleUserInput("Book Uber")
        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertEquals(CabProvider.UBER, selection.selectedOption?.provider)
        assertEquals(0, accessibilityService.finalConfirmTapCount)
        assertTrue(selection.message.contains("Should I confirm booking?"))
    }

    @Test
    fun `no or cancel cancels before final booking`() {
        listOf("no", "cancel").forEach { phrase ->
            setUp()
            installProvider(CabProvider.UBER)
            accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "₹124", "ETA 5 min")

            orchestrator.start(
                CabBookingRequest(
                    rawText = "book cab to DB Mall",
                    pickupLocation = "Home",
                    dropLocation = "DB Mall",
                    rideType = RideType.AUTO
                )
            )

            val result = orchestrator.handleUserInput(phrase)
            assertEquals(CabBookingState.CANCELLED, result.state)
            assertFalse(orchestrator.isActive())
        }
    }

    @Test
    fun `yes after final confirmation allows final booking`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "₹124", "ETA 5 min")

        orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        val selection = orchestrator.handleUserInput("Book Uber")
        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertEquals(0, accessibilityService.finalConfirmTapCount)

        val completed = orchestrator.handleUserInput("yes")
        assertEquals(CabBookingState.COMPLETED, completed.state)
        assertEquals(1, accessibilityService.finalConfirmTapCount)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `otp login payment and captcha set manual action required`() {
        listOf("OTP", "login", "payment", "captcha").forEach { reason ->
            setUp()
            installProvider(CabProvider.UBER)
            accessibilityService.manualReason = reason
            accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, "₹124", "₹124", "ETA 5 min")

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
            assertTrue(result.message.contains(reason, ignoreCase = true))
            assertTrue(orchestrator.isActive())
        }
    }

    private fun createOrchestrator(
        pickupLocationResolver: CabPickupLocationResolver? = null
    ): CabBookingOrchestrator {
        return CabBookingOrchestrator(
            providerRegistry = registry,
            deepLinkBuilder = deepLinkBuilder,
            accessibilityService = accessibilityService,
            pickupLocationResolver = pickupLocationResolver,
            providerLauncher = { intent ->
                launchedIntents.add(Intent(intent))
                true
            }
        )
    }

    private fun installProvider(provider: CabProvider) {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(registry.packageName(provider)))
            .thenReturn(Intent(Intent.ACTION_MAIN))
    }

    private fun fareOption(
        provider: CabProvider,
        visibleFareText: String,
        finalFareText: String,
        etaText: String
    ): CabFareOption {
        return CabFareOption(
            provider = provider,
            rideType = RideType.AUTO,
            visibleFareText = visibleFareText,
            finalFareText = finalFareText,
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
        var fillTripDetailsResult: Boolean = true
        val fareByProvider = mutableMapOf<CabProvider, CabFareOption?>()

        override fun captureScreenSnapshot(): CabScreenSnapshot? {
            return CabScreenSnapshot(
                visibleText = if (manualReason != null) listOf(manualReason!!) else emptyList(),
                manualActionReason = manualReason
            )
        }

        override fun detectManualActionRequired(snapshot: CabScreenSnapshot?): String? {
            return manualReason
        }

        override fun fillTripDetails(request: CabBookingRequest, snapshot: CabScreenSnapshot?): Boolean {
            return fillTripDetailsResult
        }

        override fun collectFareOption(
            provider: CabProvider,
            request: CabBookingRequest,
            snapshot: CabScreenSnapshot?
        ): CabFareOption? {
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
