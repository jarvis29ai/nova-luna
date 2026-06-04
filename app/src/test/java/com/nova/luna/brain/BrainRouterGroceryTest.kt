package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrainRouterGroceryTest {
    private val router = BrainRouter()

    @Test
    fun `grocery requests route to action json`() {
        val decision = router.route(BrainRequest("buy milk and bread"))

        assertEquals(BrainModelRole.ACTION_JSON, decision.selectedRole)
        assertFalse(decision.requiresInternet)
    }

    @Test
    fun `active grocery sessions stay on action json continuity`() {
        val decision = router.route(
            BrainRequest(
                rawText = "yes",
                activeGrocerySession = true
            )
        )

        assertEquals(BrainModelRole.ACTION_JSON, decision.selectedRole)
        assertFalse(decision.requiresInternet)
    }
}
