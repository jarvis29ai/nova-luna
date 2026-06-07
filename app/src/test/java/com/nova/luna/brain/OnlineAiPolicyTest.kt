package com.nova.luna.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiPolicyTest {
    private val policy = OnlineAiPolicy()

    @Test
    fun `research queries can request online helper permission`() {
        val request = BrainRequest("latest phone under 30000")
        val result = policy.evaluate(
            request = request,
            config = onlineAiConfig(),
            networkStatusProvider = StaticNetworkStatusProvider(true),
            userConsentGiven = false
        )

        assertTrue(policy.isPotentialCandidate(request))
        assertEquals(OnlineAiPolicyDecision.ASK_USER_PERMISSION, result.decision)
        assertFalse(result.shouldUseOnline)
        assertFalse(result.shouldFallbackLocal)
        assertTrue(result.requiresUserConsent)
        assertTrue(result.sanitizedText.contains("latest", ignoreCase = true))
    }

    @Test
    fun `consented requests are allowed when the provider is ready`() {
        val result = policy.evaluate(
            request = BrainRequest("compare iPhone and Pixel"),
            config = onlineAiConfig(),
            networkStatusProvider = StaticNetworkStatusProvider(true),
            userConsentGiven = true
        )

        assertEquals(OnlineAiPolicyDecision.ALLOW, result.decision)
        assertTrue(result.shouldUseOnline)
        assertFalse(result.requiresUserConsent)
    }

    @Test
    fun `sensitive secrets stay local and blocked`() {
        val result = policy.evaluate(
            request = BrainRequest("my password is secret123"),
            config = onlineAiConfig(),
            networkStatusProvider = StaticNetworkStatusProvider(true),
            userConsentGiven = false
        )

        assertEquals(OnlineAiPolicyDecision.DENY_SENSITIVE, result.decision)
        assertTrue(result.privacyBlocked)
        assertFalse(result.shouldUseOnline)
        assertTrue(result.shouldFallbackLocal)
    }
}
