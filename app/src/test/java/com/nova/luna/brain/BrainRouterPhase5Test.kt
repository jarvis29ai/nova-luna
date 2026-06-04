package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRouterPhase5Test {
    private val router = BrainRouter()

    @Test
    fun `stop cancel go home and open app route to lite command`() {
        listOf(
            "stop",
            "cancel",
            "go home",
            "open whatsapp"
        ).forEach { phrase ->
            val decision = router.route(BrainRequest(phrase))
            assertEquals(BrainModelRole.LITE_COMMAND, decision.selectedRole)
        }
    }

    @Test
    fun `cab and food planning route to action json`() {
        listOf(
            "book cheapest auto to DB Mall",
            "order food for dinner",
            "plan my tasks for today"
        ).forEach { phrase ->
            val decision = router.route(BrainRequest(phrase))
            assertEquals(BrainModelRole.ACTION_JSON, decision.selectedRole)
            assertTrue(decision.safetyNotes.isNotEmpty())
        }
    }

    @Test
    fun `normal question routes to gemma reasoning`() {
        val decision = router.route(BrainRequest("why is the sky blue?"))

        assertEquals(BrainModelRole.GEMMA_REASONING, decision.selectedRole)
    }

    @Test
    fun `screen question routes to screen understanding`() {
        val decision = router.route(BrainRequest("what's on my screen?"))

        assertEquals(BrainModelRole.SCREEN_UNDERSTANDING, decision.selectedRole)
        assertTrue(decision.requiresScreenContext)
    }

    @Test
    fun `unknown text routes to mock fallback`() {
        val decision = router.route(BrainRequest("asdf qwerty unknown request"))

        assertEquals(BrainModelRole.MOCK_FALLBACK, decision.selectedRole)
    }
}
