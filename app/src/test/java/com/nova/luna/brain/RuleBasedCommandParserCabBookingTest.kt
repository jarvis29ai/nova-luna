package com.nova.luna.brain

import com.nova.luna.cab.CabProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuleBasedCommandParserCabBookingTest {
    private val parser = RuleBasedCommandParser()

    @Test
    fun `cab booking phrases route into the cab booking branch`() {
        val result = parser.parse("book auto to DB Mall")

        assertEquals(IntentType.CAB_BOOKING, result.intentType)
        assertEquals(ActionType.CAB_BOOKING, result.actionType)
        assertEquals("DB Mall", result.entities["dropLocation"])
        assertEquals("AUTO", result.entities["rideType"])
        assertNull(result.entities["preferredProvider"])
    }
}
