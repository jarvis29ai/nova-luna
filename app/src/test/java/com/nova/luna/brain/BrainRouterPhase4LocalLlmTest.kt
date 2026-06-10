package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRouterPhase4LocalLlmTest {
    private val router = BrainRouter()

    @Test
    fun `fuzzy and multilingual requests ask for model setup when no local role is ready`() {
        val fuzzyDecision = router.route(BrainRequest("please help me rewrite this note"))
        val multilingualDecision = router.route(BrainRequest("कृपया मुझे समझाओ"))

        assertEquals(BrainModelRole.MOCK_FALLBACK, fuzzyDecision.selectedRole)
        assertEquals(BrainModelRole.MOCK_FALLBACK, multilingualDecision.selectedRole)
        assertTrue(fuzzyDecision.reason.contains("AI brain is not installed yet", ignoreCase = true))
        assertTrue(multilingualDecision.reason.contains("AI brain is not installed yet", ignoreCase = true))
    }

    @Test
    fun `nonsense text still stays on fallback`() {
        val decision = router.route(BrainRequest("asdf qwerty unknown request"))

        assertEquals(BrainModelRole.MOCK_FALLBACK, decision.selectedRole)
    }
}
