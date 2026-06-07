package com.nova.luna.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiPrivacyFilterTest {
    private val filter = OnlineAiPrivacyFilter()

    @Test
    fun `screen text is blocked by default`() {
        val result = filter.filter(
            request = BrainRequest(
                rawText = "what is on my screen?",
                screenState = sampleScreenState()
            ),
            config = onlineAiConfig(sendScreenText = false)
        )

        assertTrue(result.blocked)
        assertEquals(OnlineAiPolicyDecision.DENY_PRIVACY, result.blockedDecision)
        assertTrue(result.reason.contains("not sent online", ignoreCase = true))
        assertTrue(result.sanitizedText.contains("Mail", ignoreCase = true))
    }

    @Test
    fun `private messages stay local by default`() {
        val result = filter.filter(
            request = BrainRequest("message to mom I am late"),
            config = onlineAiConfig(sendPrivateMessages = false)
        )

        assertTrue(result.blocked)
        assertEquals(OnlineAiPolicyDecision.DENY_PRIVACY, result.blockedDecision)
        assertTrue(result.reason.contains("Private messages", ignoreCase = true))
    }

    @Test
    fun `critical secrets are redacted and blocked`() {
        val result = filter.filter(
            request = BrainRequest("my password is secret123 and my otp is 123456"),
            config = onlineAiConfig()
        )

        assertTrue(result.blocked)
        assertEquals(OnlineAiPolicyDecision.DENY_SENSITIVE, result.blockedDecision)
        assertTrue(result.redactionCount > 0)
        assertFalse(result.sanitizedText.contains("secret123", ignoreCase = true))
    }
}
