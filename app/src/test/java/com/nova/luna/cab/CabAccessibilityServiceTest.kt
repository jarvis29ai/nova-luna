package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabAccessibilityServiceTest {
    private val service = CabAccessibilityService()

    @Test
    fun `debug package snapshot is ignored for fare parsing`() {
        val snapshot = CabScreenSnapshot(
            visibleText = listOf("permission"),
            sourceText = "permission",
            sourcePackageName = "com.nova.luna.debug",
            manualActionReason = "permission"
        )

        val fare = service.collectFareOption(
            provider = CabProvider.UBER,
            request = CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            ),
            snapshot = snapshot
        )

        assertEquals("provider_screen_unavailable", fare.failureReason)
        assertNull(fare.fareOption)
    }

    @Test
    fun `no fare visible on provider screen returns no fare visible`() {
        val snapshot = CabScreenSnapshot(
            visibleText = listOf("Home", "DB Mall"),
            sourceText = "Home | DB Mall",
            sourcePackageName = CabProviderRegistry.OLA_PACKAGE_NAME
        )

        val fare = service.collectFareOption(
            provider = CabProvider.OLA,
            request = CabBookingRequest(
                rawText = "book cab to DB Mall",
                pickupLocation = "Home",
                dropLocation = "DB Mall",
                rideType = RideType.AUTO
            ),
            snapshot = snapshot
        )

        assertEquals("no_fare_visible", fare.failureReason)
        assertNull(fare.fareOption)
        assertEquals(CabProviderRegistry.OLA_PACKAGE_NAME, fare.snapshot?.sourcePackageName)
    }

    @Test
    fun `debug package snapshot does not trigger manual action detection`() {
        val snapshot = CabScreenSnapshot(
            visibleText = listOf("permission"),
            sourceText = "permission",
            sourcePackageName = "com.nova.luna.debug",
            manualActionReason = "permission"
        )

        assertNull(service.detectManualActionRequired(snapshot))
    }

    @Test
    fun `ola destination fill falls back when generic field labels are missing`() {
        val recordingService = RecordingCabAccessibilityService()
        recordingService.clickShouldSucceed = false
        recordingService.setSnapshots(
            CabScreenSnapshot(
                visibleText = listOf(
                    "Showing a Map created with MapLibre.",
                    "Home",
                    "Parcel",
                    "Electric",
                    "Loans"
                ),
                sourceText = "Showing a Map created with MapLibre. | Home | Parcel | Electric | Loans",
                sourcePackageName = CabProviderRegistry.OLA_PACKAGE_NAME
            ),
            CabScreenSnapshot(
                visibleText = listOf("DB Mall", "Auto"),
                sourceText = "DB Mall | Auto",
                sourcePackageName = CabProviderRegistry.OLA_PACKAGE_NAME
            )
        )

        val result = recordingService.fillTripForProvider(
            provider = CabProvider.OLA,
            pickup = LocationValue(
                rawText = "current location",
                isCurrentLocation = true,
                displayName = "Current location"
            ),
            drop = LocationValue(
                rawText = "DB Mall",
                displayName = "DB Mall"
            ),
            rideType = RideType.AUTO
        )

        assertTrue(result.filledPickup)
        assertFalse(result.filledDrop)
        assertFalse(result.canContinueToFareScreen)
        assertEquals(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, result.failureReason)
        assertFalse(recordingService.typedTexts.contains("DB Mall"))
        assertTrue(
            recordingService.clickQueries.any { queries ->
                queries.any { query ->
                    query.contains("Where do you want to go", ignoreCase = true) ||
                        query.contains("Where to", ignoreCase = true) ||
                        query.contains("Search destination", ignoreCase = true) ||
                        query.contains("Destination", ignoreCase = true)
                }
            }
        )
    }

    @Test
    fun `rapido destination fill stops when the destination field is not exposed`() {
        val recordingService = RecordingCabAccessibilityService()
        recordingService.clickShouldSucceed = false
        recordingService.clickResultSelector = { candidates ->
            candidates.any { query -> query.contains("Auto", ignoreCase = true) }
        }

        val result = recordingService.fillTripForProvider(
            provider = CabProvider.RAPIDO,
            pickup = LocationValue(
                rawText = "current location",
                isCurrentLocation = true,
                displayName = "Current location"
            ),
            drop = LocationValue(
                rawText = "DB Mall",
                displayName = "DB Mall"
            ),
            rideType = RideType.AUTO
        )

        assertTrue(result.filledPickup)
        assertFalse(result.filledDrop)
        assertFalse(result.canContinueToFareScreen)
        assertEquals(CabFailureReasons.DESTINATION_FIELD_NOT_FOUND, result.failureReason)
        assertTrue(
            recordingService.clickQueries.any { queries ->
                queries.any { query -> query.contains("Where do you want to go", ignoreCase = true) || query.contains("Where to", ignoreCase = true) }
            }
        )
        assertFalse(recordingService.typedTexts.contains("DB Mall"))
    }

    @Test
    fun `rapido current location skips pickup click and still selects destination and ride type`() {
        val recordingService = RecordingCabAccessibilityService()

        val result = recordingService.fillTripForProvider(
            provider = CabProvider.RAPIDO,
            pickup = LocationValue(
                rawText = "current location",
                isCurrentLocation = true,
                displayName = "Current location"
            ),
            drop = LocationValue(
                rawText = "DB Mall",
                displayName = "DB Mall"
            ),
            rideType = RideType.AUTO
        )

        assertTrue(result.filledPickup)
        assertTrue(result.filledDrop)
        assertTrue(result.canContinueToFareScreen)
        assertEquals(0, recordingService.pickupFillCount)
        assertTrue(
            recordingService.clickQueries.any { queries ->
                queries.any { query -> query.contains("Where do you want to go", ignoreCase = true) || query.contains("Where to", ignoreCase = true) }
            }
        )
        assertTrue(
            recordingService.clickQueries.any { queries ->
                queries.any { query -> query.contains("Auto", ignoreCase = true) }
            }
        )
        assertTrue(recordingService.typedTexts.contains("DB Mall"))
        assertFalse(
            recordingService.clickQueries.any { queries ->
                queries.any { query -> query.contains("current location", ignoreCase = true) }
            }
        )
    }

    private class RecordingCabAccessibilityService : CabAccessibilityService() {
        val clickQueries = mutableListOf<List<String>>()
        val typedTexts = mutableListOf<String>()
        var pickupFillCount = 0
        var clickShouldSucceed = true
        var clickResultSelector: (List<String>) -> Boolean = { clickShouldSucceed }
        var rideTypeShouldSucceed = true
        private val snapshotSequence = mutableListOf<CabScreenSnapshot>()
        private var snapshotIndex = 0

        fun setSnapshots(vararg snapshots: CabScreenSnapshot) {
            snapshotSequence.clear()
            snapshotSequence.addAll(snapshots)
            snapshotIndex = 0
        }

        override fun captureScreenSnapshot(): CabScreenSnapshot? {
            if (snapshotSequence.isNotEmpty()) {
                val snapshot = snapshotSequence.getOrElse(snapshotIndex) {
                    snapshotSequence.lastOrNull()
                }
                if (snapshotIndex < snapshotSequence.lastIndex) {
                    snapshotIndex += 1
                }
                return snapshot
            }

            return CabScreenSnapshot(
                visibleText = listOf("Where do you want to go?", "DB Mall", "Auto"),
                sourceText = "Where do you want to go? | DB Mall | Auto",
                sourcePackageName = CabProviderRegistry.RAPIDO_PACKAGE_NAME
            )
        }

        override fun clickTextOrDescriptionAnyOf(candidates: List<String>): Boolean {
            clickQueries.add(candidates)
            return clickResultSelector(candidates)
        }

        override fun typeIntoFocusedField(text: String): Boolean {
            typedTexts.add(text)
            return true
        }

        override fun fillPickupIfNeeded(pickup: LocationValue?): Boolean {
            pickupFillCount += 1
            return true
        }

        override fun selectRideType(rideType: RideType?): Boolean {
            if (rideType == null || rideType == RideType.ANY) {
                return true
            }

            val keywords = listOf(
                rideType.displayName(),
                rideType.displayName().lowercase(),
                rideType.name.lowercase(),
                when (rideType) {
                    RideType.AUTO -> "auto"
                    RideType.BIKE -> "bike"
                    RideType.MINI -> "mini"
                    RideType.SEDAN -> "sedan"
                    RideType.SUV -> "suv"
                    RideType.ANY -> "any"
                }
            ).distinct()

            clickQueries.add(keywords)
            return rideTypeShouldSucceed
        }

        override fun sleep(delayMs: Long) {
            // Keep the test fast.
        }
    }
}
