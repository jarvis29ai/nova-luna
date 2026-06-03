package com.nova.luna.cab

import org.junit.Assert.assertEquals
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
        val parsed = parser.parse("book cab to DB Mall")

        assertNotNull(parsed)
        assertTrue(parsed?.isCabBooking == true)
        assertEquals("DB Mall", parsed?.dropText)
    }

    @Test
    fun `book auto to railway station extracts ride type AUTO and drop`() {
        val parsed = parser.parse("book auto to railway station")

        assertNotNull(parsed)
        assertEquals(RideType.AUTO, parsed?.rideType)
        assertEquals("railway station", parsed?.dropText)
    }

    @Test
    fun `book rapido bike to airport extracts provider ride type and drop`() {
        val parsed = parser.parse("book rapido bike to airport")

        assertNotNull(parsed)
        assertEquals(CabProvider.RAPIDO, parsed?.providerPreference)
        assertEquals(RideType.BIKE, parsed?.rideType)
        assertEquals("airport", parsed?.dropText)
    }

    @Test
    fun `cab from home to office extracts pickup and drop`() {
        val parsed = parser.parse("cab from home to office")

        assertNotNull(parsed)
        assertEquals("home", parsed?.pickupText)
        assertEquals("office", parsed?.dropText)
    }

    @Test
    fun `book cheapest cab to MP Nagar extracts cheapest preference`() {
        val parsed = parser.parse("book cheapest cab to MP Nagar")

        assertNotNull(parsed)
        assertTrue(parsed?.wantsCheapest == true)
        assertEquals("MP Nagar", parsed?.dropText)
    }
}
