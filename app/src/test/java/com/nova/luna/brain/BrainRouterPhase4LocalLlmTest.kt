package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRouterPhase4LocalLlmTest {
    private val router = BrainRouter()

    @Test
    fun `fuzzy and multilingual requests route to gemma reasoning`() {
        val fuzzyDecision = router.route(BrainRequest("please help me rewrite this note"))
        val multilingualDecision = router.route(BrainRequest("कृपया मुझे समझाओ"))

        assertEquals(BrainModelRole.GEMMA_REASONING, fuzzyDecision.selectedRole)
        assertEquals(BrainModelRole.GEMMA_REASONING, multilingualDecision.selectedRole)
        assertTrue(fuzzyDecision.reason.contains("local reasoning", ignoreCase = true))
        assertTrue(multilingualDecision.reason.contains("local reasoning", ignoreCase = true))
    }

    @Test
    fun `nonsense text still stays on fallback`() {
        val decision = router.route(BrainRequest("asdf qwerty unknown request"))

        assertEquals(BrainModelRole.MOCK_FALLBACK, decision.selectedRole)
    }
}
