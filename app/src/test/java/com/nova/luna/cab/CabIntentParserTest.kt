package com.nova.luna.cab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabIntentParserTest {
    private val parser = CabIntentParser()

    @Test
    fun `book cab to Bhopal railway station extracts drop`() {
        val request = parser.parse("book cab to Bhopal railway station")

        assertNotNull(request)
        assertTrue(parser.isCabBookingCommand("book cab to Bhopal railway station"))
        assertEquals("Bhopal railway station", request?.dropLocation)
        assertNull(request?.rideType)
    }

    @Test
    fun `book auto to DB Mall extracts ride type AUTO and drop DB Mall`() {
        val request = parser.parse("book auto to DB Mall")

        assertNotNull(request)
        assertEquals(RideType.AUTO, request?.rideType)
        assertEquals("DB Mall", request?.dropLocation)
    }
}
