package com.nova.luna.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProfileTest {
    @Test
    fun `nova profile uses a slightly lower pitch and a slightly slower rate`() {
        assertEquals("Nova", VoiceProfile.NOVA.displayName)
        assertEquals(0.88f, VoiceProfile.NOVA.pitch, 0.0001f)
        assertEquals(0.96f, VoiceProfile.NOVA.speechRate, 0.0001f)
        assertTrue(VoiceProfile.NOVA.pitch < 1.0f)
        assertTrue(VoiceProfile.NOVA.speechRate < 1.0f)
    }

    @Test
    fun `luna profile uses a slightly higher pitch and a normal rate`() {
        assertEquals("Luna", VoiceProfile.LUNA.displayName)
        assertEquals(1.12f, VoiceProfile.LUNA.pitch, 0.0001f)
        assertEquals(1.0f, VoiceProfile.LUNA.speechRate, 0.0001f)
        assertTrue(VoiceProfile.LUNA.pitch > 1.0f)
    }

    @Test
    fun `stored voice profile values resolve deterministically`() {
        assertEquals(VoiceProfile.NOVA, VoiceProfile.fromStoredValue(null))
        assertEquals(VoiceProfile.NOVA, VoiceProfile.fromStoredValue("nova"))
        assertEquals(VoiceProfile.LUNA, VoiceProfile.fromStoredValue("LUNA"))
        assertEquals(VoiceProfile.NOVA, VoiceProfile.fromStoredValue("unknown"))
    }
}
