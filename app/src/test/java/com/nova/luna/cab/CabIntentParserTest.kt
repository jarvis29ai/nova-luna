package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabIntentParserTest {
    private val parser = CabIntentParser()

    @Test
    fun `book cab to DB Mall extracts drop`() {
        val parsed = parser.parseInitialCabRequest("book cab to DB Mall")

        assertNotNull(parsed)
        assertTrue(parsed?.isCabBooking == true)
        assertEquals("DB Mall", parsed?.dropText)
        assertEquals(PickupMode.UNKNOWN, parsed?.pickupMode)
        assertEquals(null, parsed?.rideType)
    }

    @Test
    fun `luna book app to TV Mall normalizes app to auto inside cab context`() {
        val parsed = parser.parseInitialCabRequest("Luna book app to TV Mall")

        assertNotNull(parsed)
        assertTrue(parsed?.isCabBooking == true)
        assertEquals(RideType.AUTO, parsed?.rideType)
        assertEquals("TV Mall", parsed?.dropText)
    }

    @Test
    fun `book cab to App Mall keeps destination literal without ride type correction`() {
        val parsed = parser.parseInitialCabRequest("book cab to App Mall")

        assertNotNull(parsed)
        assertTrue(parsed?.isCabBooking == true)
        assertEquals("App Mall", parsed?.dropText)
        assertEquals(null, parsed?.rideType)
    }

    @Test
    fun `book auto to railway station extracts ride type AUTO and drop`() {
        val parsed = parser.parseInitialCabRequest("book auto to railway station")

        assertNotNull(parsed)
        assertEquals(RideType.AUTO, parsed?.rideType)
        assertEquals("railway station", parsed?.dropText)
    }

    @Test
    fun `book rapido bike to airport extracts provider ride type and drop`() {
        val parsed = parser.parseInitialCabRequest("book rapido bike to airport")

        assertNotNull(parsed)
        assertEquals(CabProvider.RAPIDO, parsed?.providerPreference)
        assertEquals(RideType.BIKE, parsed?.rideType)
        assertEquals("airport", parsed?.dropText)
    }

    @Test
    fun `cab from home to office extracts pickup and drop`() {
        val parsed = parser.parseInitialCabRequest("cab from home to office")

        assertNotNull(parsed)
        assertEquals("home", parsed?.pickupText)
        assertEquals(PickupMode.USER_TEXT, parsed?.pickupMode)
        assertEquals("office", parsed?.dropText)
    }

    @Test
    fun `book cab from current location to DB Mall extracts current location pickup`() {
        val parsed = parser.parseInitialCabRequest("book cab from current location to DB Mall")

        assertNotNull(parsed)
        assertEquals(PickupMode.CURRENT_LOCATION, parsed?.pickupMode)
        assertEquals("Current location", parsed?.pickupText)
        assertEquals("DB Mall", parsed?.dropText)
    }

    @Test
    fun `current location replies resolve as pickup values`() {
        listOf("current location", "use current location", "my location", "from here", "here").forEach { text ->
            val reply = parser.parsePickupReply(text)
            assertNotNull(reply)
            assertTrue(reply?.isCurrentLocation == true)
        }
    }

    @Test
    fun `book cheapest cab to MP Nagar extracts cheapest preference`() {
        val parsed = parser.parseInitialCabRequest("book cheapest cab to MP Nagar")

        assertNotNull(parsed)
        assertTrue(parsed?.wantsCheapest == true)
        assertEquals("MP Nagar", parsed?.dropText)
    }

    @Test
    fun `book the cheapest and book first one are recognized as selection commands`() {
        assertTrue(parser.isCheapestChoice("book the cheapest"))
        assertTrue(parser.isFirstChoice("book first one"))
        assertTrue(parser.parseInitialCabRequest("book the cheapest")?.wantsCheapest == true)
        assertTrue(parser.parseInitialCabRequest("book first one")?.wantsFirstOne == true)
    }

    @Test
    fun `provider choice and final confirmation helpers work`() {
        assertEquals(CabProvider.UBER, parser.parseProviderChoiceReply("use Uber"))
        assertEquals(CabProvider.RAPIDO, parser.parseProviderChoiceReply("book Rapido"))
        assertEquals(null, parser.parseRideTypeReply("book cab"))
        assertEquals(RideType.AUTO, parser.parseRideTypeReply("app"))
        assertEquals(RideType.AUTO, parser.parseRideTypeReply("Otto"))
        assertEquals(RideType.AUTO, parser.parseRideTypeReply("rickshaw"))
        assertEquals(null, parser.parsePickupReply("Luna"))
        assertEquals(CabFinalConfirmationReply.CONFIRM, parser.parseFinalConfirmationReply("book it"))
        assertEquals(CabFinalConfirmationReply.DECLINE, parser.parseFinalConfirmationReply("no"))
        assertTrue(parser.isCancel("cancel"))
        assertFalse(parser.isCancel("yes"))
    }
}
