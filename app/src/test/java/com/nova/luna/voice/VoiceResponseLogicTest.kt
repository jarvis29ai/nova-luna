package com.nova.luna.voice

import com.nova.luna.model.ActionResultStatus
import org.junit.Assert.*
import org.junit.Test

class VoiceResponseTemplatesTest {
    private val templates = VoiceResponseTemplates()

    @Test
    fun `getMessage returns correct listening message`() {
        assertEquals("I am listening.", templates.getMessage(VoiceResponseType.LISTENING))
    }

    @Test
    fun `getMessage returns correct thinking message`() {
        assertEquals("Checking this for you.", templates.getMessage(VoiceResponseType.THINKING))
    }

    @Test
    fun `fromActionResultStatus maps SUCCESS correctly`() {
        assertEquals("Done.", templates.fromActionResultStatus(ActionResultStatus.SUCCESS))
    }

    @Test
    fun `fromActionResultStatus maps NOT_FOUND correctly`() {
        assertTrue(templates.fromActionResultStatus(ActionResultStatus.NOT_FOUND).contains("could not find the button"))
    }
}

class VoiceResponseSanitizerTest {
    private val sanitizer = VoiceResponseSanitizer()

    @Test
    fun `sanitize masks 6-digit OTP`() {
        val raw = "Your code is 123456."
        assertEquals("Your code is a code.", sanitizer.sanitize(raw))
    }

    @Test
    fun `sanitize masks card number`() {
        val raw = "Pay with card 1234-5678-9012-3456"
        assertEquals("Pay with card a card number", sanitizer.sanitize(raw))
    }

    @Test
    fun `sanitize masks email address`() {
        val raw = "Contact me at test@example.com"
        assertEquals("Contact me at an email address", sanitizer.sanitize(raw))
    }

    @Test
    fun `sanitize preserves safe text`() {
        val raw = "Open YouTube"
        assertEquals("Open YouTube", sanitizer.sanitize(raw))
    }
}
