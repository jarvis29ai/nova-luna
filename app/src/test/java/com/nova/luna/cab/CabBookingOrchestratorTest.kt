package com.nova.luna.cab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        orchestrator = createOrchestrator()
    }

    @Test
    fun `current location reply advances when location is available`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)
        orchestrator = createOrchestrator(
            locationResolver = object : CabLocationResolver {
                override fun hasLocationPermission(): Boolean = true
                override fun getCurrentLocationDisplay(): String? = "Current location"
                override fun getCurrentLatLng(): Pair<Double, Double>? = 23.0 to 77.0
            }
        )

        val start = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.NEED_PICKUP, start.state)

        val reply = orchestrator.handleUserInput("current location")

        assertEquals(CabBookingState.SHOWING_COMPARISON, reply.state)
        assertEquals("Current location", reply.request?.pickupLocation)
        assertEquals(PickupMode.CURRENT_LOCATION, reply.request?.pickupMode)
        assertTrue(reply.message.contains("I found these fares"))
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `current location reply asks for manual pickup when location access is missing`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)
        orchestrator = createOrchestrator(
            locationResolver = object : CabLocationResolver {
                override fun hasLocationPermission(): Boolean = false
                override fun getCurrentLocationDisplay(): String? = null
                override fun getCurrentLatLng(): Pair<Double, Double>? = null
            }
        )

        val start = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.NEED_PICKUP, start.state)

        val reply = orchestrator.handleUserInput("current location")

        assertEquals(CabBookingState.NEED_PICKUP, reply.state)
        assertEquals(PickupMode.CURRENT_LOCATION, reply.request?.pickupMode)
        assertNull(reply.request?.pickupLocation)
        assertEquals(CabFailureReasons.BLOCKED_BY_LOCATION_PERMISSION, reply.pickupBlockedReason)
        assertEquals(CabBookingVoiceResponses.currentLocationUnavailable(), reply.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `use my current location reply also asks for manual pickup when resolver is unavailable`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)

        orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        val reply = orchestrator.handleUserInput("Use my current location")

        assertEquals(CabBookingState.NEED_PICKUP, reply.state)
        assertEquals(PickupMode.CURRENT_LOCATION, reply.request?.pickupMode)
        assertNull(reply.request?.pickupLocation)
        assertNull(reply.pickupBlockedReason)
        assertEquals(CabBookingVoiceResponses.currentLocationUnavailable(), reply.message)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `wake words are ignored in pickup replies`() {
        val start = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.NEED_PICKUP, start.state)

        listOf("Luna", "Nova", "hey Luna", "okay Luna", "hello Luna").forEach { text ->
            val reply = orchestrator.handleUserInput(text)

            assertEquals(CabBookingState.NEED_PICKUP, reply.state)
            assertNull(reply.request?.pickupLocation)
            assertEquals(CabBookingVoiceResponses.askPickup(), reply.message)
        }
    }

    @Test
    fun `provider automation attempts pickup and drop before failing`() {
        installProvider(CabProvider.UBER)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)
        accessibilityService.pickupShouldSucceed = false
        accessibilityService.dropShouldSucceed = false

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertTrue(accessibilityService.pickupFillCount > 0)
        assertTrue(accessibilityService.dropFillCount > 0)
        assertTrue(result.manualActionRequired)
        assertEquals(CabFailureReasons.PICKUP_FIELD_NOT_FOUND + " and " + CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, result.manualActionReason)
        assertTrue(result.message.contains("destination field is not accessible", ignoreCase = true))
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `provider with no fare visible fails deterministically`() {
        installProvider(CabProvider.OLA)
        accessibilityService.setForegroundPackages(CabProviderRegistry.OLA_PACKAGE_NAME)

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertEquals("no_fare_visible", result.providerFailures[CabProvider.OLA])
        assertEquals(CabFailureReasons.NO_FARE_VISIBLE, result.manualActionReason)
        assertTrue(result.message.contains("no fare", ignoreCase = true))
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `ola destination field missing returns manual destination handoff`() {
        installProvider(CabProvider.OLA)

        val localService = FakeCabAccessibilityService().apply {
            dropShouldSucceed = false
            fareByProvider[CabProvider.OLA] = fareOption(CabProvider.OLA, 124L)
            setForegroundPackages(CabProviderRegistry.OLA_PACKAGE_NAME)
        }
        val localOrchestrator = createOrchestrator(localService)

        val result = localOrchestrator.start(
            CabBookingRequest(
                rawText = "book Ola to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.OLA
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertEquals(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, result.manualActionReason)
        assertTrue(result.message.contains("destination field is not accessible", ignoreCase = true))
        assertTrue(localOrchestrator.isActive())
    }

    @Test
    fun `rapido destination field missing returns manual destination handoff`() {
        installProvider(CabProvider.RAPIDO)

        val localService = FakeCabAccessibilityService().apply {
            dropShouldSucceed = false
            fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, 99L)
            setForegroundPackages(CabProviderRegistry.RAPIDO_PACKAGE_NAME)
        }
        val localOrchestrator = createOrchestrator(localService)

        val result = localOrchestrator.start(
            CabBookingRequest(
                rawText = "book Rapido to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.RAPIDO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertEquals(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, result.manualActionReason)
        assertTrue(result.message.contains("destination field is not accessible", ignoreCase = true))
        assertTrue(localOrchestrator.isActive())
    }

    @Test
    fun `done resumes fare collection after manual destination handoff`() {
        installProvider(CabProvider.OLA)

        val localService = FakeCabAccessibilityService().apply {
            dropShouldSucceed = false
            fareByProvider[CabProvider.OLA] = fareOption(CabProvider.OLA, 124L, finalFareAmount = 118L, etaText = "ETA 5 min")
            setForegroundPackages(CabProviderRegistry.OLA_PACKAGE_NAME)
        }
        val localOrchestrator = createOrchestrator(localService)

        val start = localOrchestrator.start(
            CabBookingRequest(
                rawText = "book Ola to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.OLA
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, start.state)
        assertEquals(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, start.manualActionReason)

        localService.dropShouldSucceed = true
        localService.setSnapshotSequence(
            CabScreenSnapshot(
                visibleText = listOf("DB Mall", "₹118", "ETA 5 min", "Coupon applied"),
                sourceText = "DB Mall | ₹118 | ETA 5 min | Coupon applied",
                sourcePackageName = CabProviderRegistry.OLA_PACKAGE_NAME,
                visibleFareText = "₹118",
                finalFareText = "₹118 after coupon",
                etaText = "ETA 5 min",
                couponText = "Coupon applied",
                discountText = "Discounted fare"
            )
        )

        val resumed = localOrchestrator.handleUserInput("done")

        assertEquals(CabBookingState.SHOWING_COMPARISON, resumed.state)
        assertTrue(resumed.fareOptions.any { it.provider == CabProvider.OLA })
        assertTrue(resumed.message.contains("I found these fares", ignoreCase = true))
        assertTrue(localOrchestrator.isActive())
    }

    @Test
    fun `compare now resumes fare collection after manual destination handoff`() {
        installProvider(CabProvider.RAPIDO)

        val localService = FakeCabAccessibilityService().apply {
            dropShouldSucceed = false
            fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, 89L, finalFareAmount = 84L, etaText = "ETA 4 min")
            setForegroundPackages(CabProviderRegistry.RAPIDO_PACKAGE_NAME)
        }
        val localOrchestrator = createOrchestrator(localService)

        val start = localOrchestrator.start(
            CabBookingRequest(
                rawText = "book Rapido to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.RAPIDO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, start.state)
        assertEquals(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, start.manualActionReason)

        localService.dropShouldSucceed = true
        localService.setSnapshotSequence(
            CabScreenSnapshot(
                visibleText = listOf("DB Mall", "₹84", "ETA 4 min", "Save ₹5"),
                sourceText = "DB Mall | ₹84 | ETA 4 min | Save ₹5",
                sourcePackageName = CabProviderRegistry.RAPIDO_PACKAGE_NAME,
                visibleFareText = "₹84",
                finalFareText = "₹84 after discount",
                etaText = "ETA 4 min",
                couponText = "Save ₹5",
                discountText = "Discounted fare"
            )
        )

        val resumed = localOrchestrator.handleUserInput("compare now")

        assertEquals(CabBookingState.SHOWING_COMPARISON, resumed.state)
        assertTrue(resumed.fareOptions.any { it.provider == CabProvider.RAPIDO })
        assertTrue(resumed.message.contains("I found these fares", ignoreCase = true))
        assertTrue(localOrchestrator.isActive())
    }

    @Test
    fun `generic cheapest keeps going after a provider foreground timeout`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)

        val localService = FakeCabAccessibilityService().apply {
            fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, 99L, finalFareAmount = 99L, etaText = "ETA 4 min")
            setForegroundPackages(
                *MutableList(10) { "com.nova.luna.debug" }.apply {
                    add(CabProviderRegistry.RAPIDO_PACKAGE_NAME)
                }.toTypedArray()
            )
        }
        val localOrchestrator = createOrchestrator(localService)

        val result = localOrchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertEquals(CabFailureReasons.PROVIDER_FOREGROUND_TIMEOUT, result.providerFailures[CabProvider.UBER])
        assertEquals(listOf(CabProvider.RAPIDO), result.fareOptions.map { it.provider })
        assertTrue(localOrchestrator.isActive())
    }

    @Test
    fun `manual action screens still stop safely for payment login captcha and otp`() {
        listOf("payment", "OTP", "login", "captcha", "manual action required").forEach { reason ->
            installProvider(CabProvider.UBER)

            val localService = FakeCabAccessibilityService().apply {
                manualReasonByPackage[CabProviderRegistry.UBER_PACKAGE_NAME] = reason
                setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)
                fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
            }
            val localOrchestrator = createOrchestrator(localService)

            val result = localOrchestrator.start(
                CabBookingRequest(
                    rawText = "book Uber to DB Mall",
                    pickupLocation = "Home",
                    dropLocation = "DB Mall",
                    rideType = RideType.AUTO,
                    preferredProvider = CabProvider.UBER
                )
            )

            assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
            assertEquals(reason, result.manualActionReason)
            assertTrue(result.message.contains(reason, ignoreCase = true))
            assertTrue(localOrchestrator.isActive())
        }
    }

    @Test
    fun `fare comparison is sorted low to high`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        installProvider(CabProvider.OLA)
        accessibilityService.setForegroundPackages(
            CabProviderRegistry.UBER_PACKAGE_NAME,
            CabProviderRegistry.OLA_PACKAGE_NAME,
            CabProviderRegistry.RAPIDO_PACKAGE_NAME
        )

        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(
            CabProvider.UBER,
            139L,
            finalFareAmount = 124L,
            finalFareText = "₹124 after discount",
            etaText = "ETA 5 min"
        )
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(
            CabProvider.RAPIDO,
            99L,
            finalFareAmount = 99L,
            etaText = "ETA 4 min"
        )
        accessibilityService.fareByProvider[CabProvider.OLA] = fareOption(
            CabProvider.OLA,
            188L,
            finalFareAmount = 188L,
            etaText = "ETA 6 min"
        )

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
    fun `rapido payment screens still verify trip fill before manual action`() {
        installProvider(CabProvider.RAPIDO)
        accessibilityService.setForegroundPackages(CabProviderRegistry.RAPIDO_PACKAGE_NAME)
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(
            CabProvider.RAPIDO,
            69L,
            finalFareAmount = 69L,
            etaText = "ETA 4 min"
        )
        accessibilityService.setSnapshotSequence(
            CabScreenSnapshot(
                visibleText = listOf(
                    "Bike ride. Estimated fare ₹69",
                    "Double tap to buy.",
                    "Buy Pass Now"
                ),
                sourceText = "Bike ride. Estimated fare ₹69 | Double tap to buy | Buy Pass Now",
                sourcePackageName = CabProviderRegistry.RAPIDO_PACKAGE_NAME,
                visibleFareText = "Bike ride. Estimated fare ₹69",
                finalFareText = "Bike ride. Estimated fare ₹69",
                etaText = "Cab Premium ride. Estimated fare ₹163 in 4 mins",
                rideTypeText = "Bike ride",
                couponText = "Offers Double tap to check coupons.",
                discountText = "UNLIMITED Discounts! Buy Pass Now",
                manualActionReason = "payment"
            ),
            CabScreenSnapshot(
                visibleText = listOf(
                    "DB Mall",
                    "Bike ride. Estimated fare ₹69",
                    "Double tap to buy."
                ),
                sourceText = "DB Mall | Bike ride. Estimated fare ₹69 | Double tap to buy",
                sourcePackageName = CabProviderRegistry.RAPIDO_PACKAGE_NAME,
                visibleFareText = "Bike ride. Estimated fare ₹69",
                finalFareText = "Bike ride. Estimated fare ₹69",
                etaText = "Cab Premium ride. Estimated fare ₹163 in 4 mins",
                rideTypeText = "Bike ride",
                couponText = "Offers Double tap to check coupons.",
                discountText = "UNLIMITED Discounts! Buy Pass Now",
                manualActionReason = "payment"
            )
        )
        orchestrator = createOrchestrator(
            accessibilityService = accessibilityService,
            locationResolver = object : CabLocationResolver {
                override fun hasLocationPermission(): Boolean = true

                override fun getCurrentLocationDisplay(): String? = "Current location"

                override fun getCurrentLatLng(): Pair<Double, Double>? = 23.0 to 77.0
            }
        )

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book Rapido from current location to DB Mall",
                pickupLocation = "Current location",
                pickupMode = PickupMode.CURRENT_LOCATION,
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.RAPIDO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertEquals("payment", result.manualActionReason)
        assertTrue(accessibilityService.dropFillCount > 0)
        assertEquals(0, accessibilityService.pickupFillCount)
        assertTrue(accessibilityService.rideTypeSelectCount > 0)
        assertTrue(accessibilityService.capturedPackages.size >= 2)
    }

    @Test
    fun `book cheapest selects lowest fare and waits for final confirmation`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        accessibilityService.setForegroundPackages(
            CabProviderRegistry.UBER_PACKAGE_NAME,
            CabProviderRegistry.RAPIDO_PACKAGE_NAME,
            CabProviderRegistry.RAPIDO_PACKAGE_NAME
        )

        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 139L, finalFareAmount = 124L, etaText = "ETA 5 min")
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, 99L, finalFareAmount = 99L, etaText = "ETA 4 min")

        orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        val selection = orchestrator.handleUserInput("book the cheapest")

        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertEquals(CabProvider.RAPIDO, selection.selectedFare?.provider)
        assertEquals(0, accessibilityService.finalTapCount)
        assertTrue(selection.message.contains("Should I confirm booking?"))

        val completed = orchestrator.handleUserInput("yes")

        assertEquals(CabBookingState.COMPLETED, completed.state)
        assertEquals(1, accessibilityService.finalTapCount)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `book first one selects the first sorted fare`() {
        installProvider(CabProvider.UBER)
        installProvider(CabProvider.RAPIDO)
        accessibilityService.setForegroundPackages(
            CabProviderRegistry.UBER_PACKAGE_NAME,
            CabProviderRegistry.RAPIDO_PACKAGE_NAME,
            CabProviderRegistry.RAPIDO_PACKAGE_NAME
        )

        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 139L, finalFareAmount = 124L, etaText = "ETA 5 min")
        accessibilityService.fareByProvider[CabProvider.RAPIDO] = fareOption(CabProvider.RAPIDO, 99L, finalFareAmount = 99L, etaText = "ETA 4 min")

        orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        val selection = orchestrator.handleUserInput("book first one")

        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, selection.state)
        assertEquals(CabProvider.RAPIDO, selection.selectedFare?.provider)
        assertEquals(0, accessibilityService.finalTapCount)
    }

    @Test
    fun `cancel cancels the active session`() {
        installProvider(CabProvider.UBER)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)

        orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        val result = orchestrator.handleUserInput("cancel")

        assertEquals(CabBookingState.CANCELLED, result.state)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `manual action screen produces manual action required`() {
        installProvider(CabProvider.UBER)
        accessibilityService.manualReasonByPackage[CabProviderRegistry.UBER_PACKAGE_NAME] = "OTP"
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)

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
        assertTrue(result.message.contains("OTP", ignoreCase = true))
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `provider attempt waits for actual foreground package before reading fare`() {
        installProvider(CabProvider.OLA)
        accessibilityService.fareByProvider[CabProvider.OLA] = fareOption(CabProvider.OLA, 124L)
        accessibilityService.manualReasonByPackage["com.nova.luna.debug"] = "permission"
        accessibilityService.setForegroundPackages(
            "com.nova.luna.debug",
            CabProviderRegistry.OLA_PACKAGE_NAME
        )

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.OLA
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertTrue(accessibilityService.foregroundWaitChecks >= 2)
        assertEquals(CabProviderRegistry.OLA_PACKAGE_NAME, accessibilityService.capturedPackages.first())
        assertEquals(CabProviderRegistry.OLA_PACKAGE_NAME, accessibilityService.fareCollectionPackages.first())
        assertTrue(result.fareOptions.isNotEmpty())
    }

    @Test
    fun `ola falls back to package launch when geo intent never foregrounds`() {
        installProvider(CabProvider.OLA)
        deepLinkBuilder.supportsDirectTripIntent = false
        accessibilityService.fareByProvider[CabProvider.OLA] = fareOption(CabProvider.OLA, 124L)

        val fallbackTimeline = MutableList(10) { "com.nova.luna.debug" }.apply {
            add(CabProviderRegistry.OLA_PACKAGE_NAME)
        }.toTypedArray()
        accessibilityService.setForegroundPackages(*fallbackTimeline)

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.OLA
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertEquals(1, deepLinkBuilder.packageLaunchIntentCount)
        assertEquals(2, launchedIntents.size)
        assertEquals(Intent.ACTION_VIEW, launchedIntents[0].action)
        assertEquals(Intent.ACTION_MAIN, launchedIntents[1].action)
        assertEquals(CabProviderRegistry.OLA_PACKAGE_NAME, launchedIntents[1].`package`)
        assertTrue(accessibilityService.foregroundWaitChecks >= 11)
        assertTrue(result.fareOptions.isNotEmpty())
    }

    @Test
    fun `provider never foregrounding returns foreground failure reason`() {
        installProvider(CabProvider.RAPIDO)
        accessibilityService.setForegroundPackages(
            "com.nova.luna.debug",
            "com.nova.luna.debug",
            "com.nova.luna.debug"
        )

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO,
                preferredProvider = CabProvider.RAPIDO
            )
        )

        assertEquals(CabBookingState.MANUAL_ACTION_REQUIRED, result.state)
        assertEquals(CabFailureReasons.PROVIDER_FOREGROUND_TIMEOUT, result.providerFailures[CabProvider.RAPIDO])
        assertTrue(result.message.contains("come to the foreground", ignoreCase = true))
        assertEquals(CabFailureReasons.PROVIDER_FOREGROUND_TIMEOUT, result.manualActionReason)
        assertTrue(orchestrator.isActive())
    }

    @Test
    fun `session continuity keeps cab ownership across replies`() {
        installProvider(CabProvider.UBER)
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
        accessibilityService.setForegroundPackages(
            CabProviderRegistry.UBER_PACKAGE_NAME,
            CabProviderRegistry.UBER_PACKAGE_NAME
        )
        orchestrator = createOrchestrator(
            locationResolver = object : CabLocationResolver {
                override fun hasLocationPermission(): Boolean = true
                override fun getCurrentLocationDisplay(): String? = "Current location"
                override fun getCurrentLatLng(): Pair<Double, Double>? = 23.0 to 77.0
            }
        )

        val start = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                dropLocation = "DB Mall"
            )
        )

        assertEquals(CabBookingState.NEED_PICKUP, start.state)

        val pickup = orchestrator.handleUserInput("current location")
        assertEquals(CabBookingState.NEED_RIDE_TYPE, pickup.state)

        val rideType = orchestrator.handleUserInput("auto")
        assertEquals(CabBookingState.SHOWING_COMPARISON, rideType.state)

        val cheapest = orchestrator.handleUserInput("book cheapest")
        assertEquals(CabBookingState.WAITING_FOR_FINAL_CONFIRMATION, cheapest.state)
        assertEquals(CabProvider.UBER, cheapest.selectedFare?.provider)

        val completed = orchestrator.handleUserInput("yes")
        assertEquals(CabBookingState.COMPLETED, completed.state)
        assertFalse(orchestrator.isActive())
    }

    @Test
    fun `deep link fallback still attempts accessibility fill`() {
        installProvider(CabProvider.UBER)
        deepLinkBuilder.supportsDirectTripIntent = false
        accessibilityService.fareByProvider[CabProvider.UBER] = fareOption(CabProvider.UBER, 124L)
        accessibilityService.setForegroundPackages(CabProviderRegistry.UBER_PACKAGE_NAME)

        val result = orchestrator.start(
            CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            )
        )

        assertEquals(CabBookingState.SHOWING_COMPARISON, result.state)
        assertFalse(deepLinkBuilder.lastLaunchPlan?.supportsDirectTripIntent ?: true)
        assertTrue(accessibilityService.pickupFillCount > 0)
        assertTrue(accessibilityService.dropFillCount > 0)
    }

    private fun createOrchestrator(
        accessibilityService: CabAccessibilityService = this.accessibilityService,
        locationResolver: CabLocationResolver? = null
    ): CabBookingOrchestrator {
        return CabBookingOrchestrator(
            providerRegistry = registry,
            deepLinkBuilder = deepLinkBuilder,
            accessibilityService = accessibilityService,
            locationResolver = locationResolver,
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
        visibleFareAmount: Long,
        finalFareAmount: Long = visibleFareAmount,
        finalFareText: String? = null,
        etaText: String = "ETA 5 min"
    ): CabFareOption {
        return CabFareOption(
            provider = provider,
            rideType = RideType.AUTO,
            visibleFareText = "₹$visibleFareAmount",
            visibleFareAmount = visibleFareAmount,
            originalFareAmount = visibleFareAmount,
            finalFareText = finalFareText ?: "₹$finalFareAmount",
            finalFareAmount = finalFareAmount,
            etaText = etaText
        )
    }

    private class FakeCabDeepLinkBuilder(
        context: Context,
        private val registry: CabProviderRegistry
    ) : CabDeepLinkBuilder(context, registry) {
        var supportsDirectTripIntent: Boolean = true
        var lastLaunchPlan: CabDeepLinkResult? = null
        var packageLaunchIntentCount: Int = 0

        override fun buildLaunchPlan(
            provider: CabProvider,
            request: CabBookingRequest,
            selectedOption: CabFareOption?
        ): CabDeepLinkResult {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                putExtra("provider", provider.name)
            }

            val result = CabDeepLinkResult(
                provider = provider,
                intent = intent,
                launched = true,
                supportsDirectTripIntent = supportsDirectTripIntent,
                needsAccessibilityFill = !supportsDirectTripIntent || selectedOption == null,
                launchMode = when {
                    supportsDirectTripIntent -> "direct_trip"
                    provider == CabProvider.OLA -> "geo"
                    else -> "package"
                }
            )
            lastLaunchPlan = result
            return result
        }

        override fun buildLaunchIntent(
            provider: CabProvider,
            request: CabBookingRequest,
            selectedOption: CabFareOption?
        ): Intent? {
            return buildLaunchPlan(provider, request, selectedOption).intent
        }

        override fun buildPackageLaunchIntent(
            provider: CabProvider,
            request: CabBookingRequest,
            selectedOption: CabFareOption?
        ): Intent? {
            packageLaunchIntentCount += 1
            return Intent(Intent.ACTION_MAIN).apply {
                setPackage(registry.packageName(provider))
                putExtra("provider", provider.name)
            }
        }
    }

    private class FakeCabAccessibilityService : CabAccessibilityService() {
        var manualReason: String? = null
        val manualReasonByPackage = mutableMapOf<String, String>()
        var pickupShouldSucceed: Boolean = true
        var dropShouldSucceed: Boolean = true
        var rideTypeShouldSucceed: Boolean = true
        var finalTapShouldSucceed: Boolean = true
        var foregroundPackage: String? = null
        var foregroundPackageTimeline: MutableList<String?> = mutableListOf()
        var foregroundWaitChecks: Int = 0
        var pickupFillCount = 0
        var dropFillCount = 0
        var rideTypeSelectCount = 0
        var finalTapCount = 0
        val capturedPackages = mutableListOf<String?>()
        val fareCollectionPackages = mutableListOf<String?>()
        val fareByProvider = mutableMapOf<CabProvider, CabFareOption?>()
        private val snapshotSequence = mutableListOf<CabScreenSnapshot>()
        private var snapshotSequenceIndex = 0
        private var snapshotSequenceEnabled = false

        fun setForegroundPackages(vararg packages: String?) {
            foregroundPackageTimeline.clear()
            foregroundPackageTimeline.addAll(packages)
            foregroundPackage = packages.lastOrNull()
        }

        fun setSnapshotSequence(vararg snapshots: CabScreenSnapshot) {
            snapshotSequence.clear()
            snapshotSequence.addAll(snapshots)
            snapshotSequenceIndex = 0
            snapshotSequenceEnabled = true
        }

        override fun currentForegroundPackageName(): String? {
            return foregroundPackage
        }

        override fun waitForForegroundPackage(
            expectedPackageNames: Set<String>,
            attempts: Int,
            totalWaitMs: Long
        ): String? {
            repeat(attempts) {
                foregroundWaitChecks += 1
                val nextPackage = if (foregroundPackageTimeline.isNotEmpty()) {
                    foregroundPackageTimeline.removeAt(0)
                } else {
                    foregroundPackage
                }
                if (!nextPackage.isNullOrBlank()) {
                    foregroundPackage = nextPackage
                }
                if (!nextPackage.isNullOrBlank() && expectedPackageNames.contains(nextPackage)) {
                    return nextPackage
                }
            }
            return null
        }

        override fun captureScreenSnapshot(): CabScreenSnapshot? {
            if (snapshotSequenceEnabled) {
                if (snapshotSequence.isEmpty()) {
                    return null
                }

                val snapshot = snapshotSequence.getOrElse(snapshotSequenceIndex) {
                    snapshotSequence.last()
                }
                if (snapshotSequenceIndex < snapshotSequence.lastIndex) {
                    snapshotSequenceIndex += 1
                }
                capturedPackages.add(snapshot.sourcePackageName)
                return snapshot
            }

            val packageName = foregroundPackage
            capturedPackages.add(packageName)
            val resolvedManualReason = packageName?.let { manualReasonByPackage[it] }
                ?: if (packageName?.contains("com.nova.luna", ignoreCase = true) == true) manualReason else null
            return CabScreenSnapshot(
                visibleText = if (manualReason != null) listOf(manualReason!!) else emptyList(),
                sourceText = resolvedManualReason ?: "screen",
                sourcePackageName = packageName,
                manualActionReason = resolvedManualReason
            )
        }

        override fun fillPickupIfNeeded(pickup: LocationValue?): Boolean {
            pickupFillCount += 1
            return pickupShouldSucceed
        }

        override fun fillDropIfNeeded(drop: LocationValue?): Boolean {
            dropFillCount += 1
            return dropShouldSucceed
        }

        override fun selectRideType(rideType: RideType?): Boolean {
            rideTypeSelectCount += 1
            return rideTypeShouldSucceed
        }

        override fun fillTripForProvider(
            provider: CabProvider,
            pickup: LocationValue?,
            drop: LocationValue?,
            rideType: RideType?
        ): CabTripFillResult {
            return when (provider) {
                CabProvider.RAPIDO -> {
                    val pickupFilled = if (pickup?.isCurrentLocation == true) {
                        true
                    } else {
                        fillPickupIfNeeded(pickup)
                    }
                    val dropFilled = if (drop == null) false else fillDropIfNeeded(drop)
                    val rideTypeFilled = selectRideType(rideType)
                    CabTripFillResult(
                        filledPickup = pickupFilled,
                        filledDrop = dropFilled,
                        selectedRideType = rideTypeFilled,
                        canContinueToFareScreen = pickupFilled && dropFilled,
                        failureReason = if (pickupFilled && dropFilled) null else buildString {
                            val reasons = buildList {
                                if (!pickupFilled) add(CabFailureReasons.PICKUP_FIELD_NOT_FOUND)
                                if (!dropFilled) add(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND)
                                if (!rideTypeFilled) add(CabFailureReasons.RIDE_TYPE_NOT_SELECTED)
                            }
                            append(reasons.joinToString(separator = " and "))
                        },
                        warningReason = if (!rideTypeFilled && pickupFilled && dropFilled) "could not select ride type" else null
                    )
                }

                else -> {
                    val pickupFilled = fillPickupIfNeeded(pickup)
                    val dropFilled = fillDropIfNeeded(drop)
                    val rideTypeFilled = selectRideType(rideType)
                    val canContinue = pickupFilled && dropFilled && rideTypeFilled
                    CabTripFillResult(
                        filledPickup = pickupFilled,
                        filledDrop = dropFilled,
                        selectedRideType = rideTypeFilled,
                        canContinueToFareScreen = canContinue,
                        failureReason = if (canContinue) null else buildString {
                            val reasons = buildList {
                                if (!pickupFilled) add(CabFailureReasons.PICKUP_FIELD_NOT_FOUND)
                                if (!dropFilled) add(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND)
                                if (!rideTypeFilled) add(CabFailureReasons.RIDE_TYPE_NOT_SELECTED)
                            }
                            append(reasons.joinToString(separator = " and "))
                        }
                    )
                }
            }
        }

        override fun collectFareOption(
            provider: CabProvider,
            request: CabBookingRequest,
            snapshot: CabScreenSnapshot?,
            attempts: Int,
            totalWaitMs: Long
        ): CabFareCollectionResult {
            val packageName = snapshot?.sourcePackageName
            fareCollectionPackages.add(packageName)
            val fareOption = if (packageName != null &&
                packageName == foregroundPackage &&
                !packageName.contains("com.nova.luna", ignoreCase = true)
            ) {
                fareByProvider[provider]
            } else {
                null
            }

            return when {
                fareOption != null -> CabFareCollectionResult(
                    fareOption = fareOption,
                    snapshot = snapshot
                )

                packageName?.contains("com.nova.luna", ignoreCase = true) == true -> CabFareCollectionResult(
                    failureReason = CabFailureReasons.PROVIDER_SCREEN_UNAVAILABLE,
                    snapshot = snapshot
                )

                else -> CabFareCollectionResult(
                    failureReason = CabFailureReasons.NO_FARE_VISIBLE,
                    snapshot = snapshot
                )
            }
        }

        override fun tapFinalBookButtonOnlyIfConfirmed(finalUserConfirmed: Boolean): Boolean {
            if (!finalUserConfirmed || manualReason != null || !finalTapShouldSucceed) {
                return false
            }
            finalTapCount += 1
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
