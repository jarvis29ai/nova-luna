package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRouterPhase5OnlineAiTest {
    @Test
    fun `online research requests route to the online helper when it is enabled`() {
        val router = onlineBrainRouter(
            providerType = OnlineAiProviderType.FAKE,
            enabled = true,
            internetAvailable = true
        )

        val decision = router.route(BrainRequest("latest phone under 30000"))

        assertEquals(BrainModelRole.ONLINE_AI_HELPER, decision.selectedRole)
        assertTrue(decision.reason.contains("optional online research", ignoreCase = true))
    }

    @Test
    fun `online helper can be disabled for a local fallback route`() {
        val router = onlineBrainRouter(
            providerType = OnlineAiProviderType.FAKE,
            enabled = true,
            internetAvailable = true
        )

        val decision = router.route(
            request = BrainRequest("compare iPhone and Pixel"),
            allowOnlineHelper = false
        )

        assertEquals(BrainModelRole.MOCK_FALLBACK, decision.selectedRole)
        assertTrue(decision.reason.contains("AI brain is not installed yet", ignoreCase = true))
    }

    @Test
    fun `disabled online helper falls back locally`() {
        val router = onlineBrainRouter(
            providerType = OnlineAiProviderType.FAKE,
            enabled = false,
            internetAvailable = true
        )

        val decision = router.route(BrainRequest("latest phone under 30000"))

        assertEquals(BrainModelRole.MOCK_FALLBACK, decision.selectedRole)
        assertTrue(decision.reason.contains("AI brain is not installed yet", ignoreCase = true))
    }
}
