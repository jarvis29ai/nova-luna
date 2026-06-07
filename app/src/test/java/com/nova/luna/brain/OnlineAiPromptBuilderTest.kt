package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiPromptBuilderTest {
    private val builder = OnlineAiPromptBuilder()

    @Test
    fun `prompt keeps the helper read only and uses sanitized text`() {
        val request = BrainRequest("write a reply with my password secret123")
        val config = onlineAiConfig()
        val privacyResult = OnlineAiPrivacyFilter().filter(request, config)
        val policyResult = OnlineAiPolicy().evaluate(
            request = request,
            config = config,
            networkStatusProvider = StaticNetworkStatusProvider(true),
            userConsentGiven = true
        )
        val routeDecision = BrainRouteDecision(
            selectedRole = BrainModelRole.ONLINE_AI_HELPER,
            reason = "Online helper draft request.",
            requiresInternet = true,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf("Stay read only.")
        )

        val prompt = builder.buildBrainActionPrompt(
            request = request,
            routeDecision = routeDecision,
            config = config,
            policyResult = policyResult,
            privacyResult = privacyResult
        )

        assertTrue(prompt.contains("You do not control the phone"))
        assertTrue(prompt.contains("Return one strict BrainAction JSON object only"))
        assertTrue(prompt.contains("Sanitized input"))
        assertFalse(prompt.contains("secret123", ignoreCase = true))
        assertFalse(prompt.contains("password is secret123", ignoreCase = true))
    }
}
