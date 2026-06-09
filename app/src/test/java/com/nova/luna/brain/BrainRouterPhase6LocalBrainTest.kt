package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRouterPhase6LocalBrainTest {
    @Test
    fun `simple commands still use the deterministic rule parser even when local bridge is ready`() {
        val router = BrainRouter(
            localBrainRouterBridge = FixedBrainRouterBridge(
                decision = routeDecision(BrainModelRole.CORE_BRAIN)
            )
        )

        val decision = router.route(BrainRequest("open whatsapp"))

        assertEquals(BrainModelRole.LITE_COMMAND, decision.selectedRole)
    }

    @Test
    fun `complex requests route to core brain when the bridge exposes a ready local model`() {
        val router = BrainRouter(
            localBrainRouterBridge = FixedBrainRouterBridge(
                decision = routeDecision(BrainModelRole.CORE_BRAIN)
            )
        )

        val decision = router.route(BrainRequest("please explain how offline model verification works"))

        assertEquals(BrainModelRole.CORE_BRAIN, decision.selectedRole)
        assertTrue(decision.reason.contains("Core Brain", ignoreCase = true))
    }

    @Test
    fun `multilingual requests can route to the multilingual backup role`() {
        val router = BrainRouter(
            localBrainRouterBridge = FixedBrainRouterBridge(
                decision = routeDecision(BrainModelRole.MULTILINGUAL_BACKUP)
            )
        )

        val decision = router.route(BrainRequest("कृपया मुझे समझाओ"))

        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, decision.selectedRole)
        assertTrue(decision.reason.contains("multilingual", ignoreCase = true))
    }

    @Test
    fun `router falls back to gemma reasoning when no local role is available`() {
        val router = BrainRouter()

        val decision = router.route(BrainRequest("please explain how offline model verification works"))

        assertEquals(BrainModelRole.GEMMA_REASONING, decision.selectedRole)
        assertTrue(decision.reason.contains("local reasoning", ignoreCase = true))
    }

    private class FixedBrainRouterBridge(
        private val decision: BrainRouteDecision?
    ) : BrainRouterBridge {
        override fun selectLocalRoute(
            request: BrainRequest,
            allowOnlineHelper: Boolean
        ): BrainRouteDecision? {
            return decision
        }
    }

    private fun routeDecision(role: BrainModelRole): BrainRouteDecision {
        val displayName = when (role) {
            BrainModelRole.CORE_BRAIN -> "Core Brain"
            BrainModelRole.MULTILINGUAL_BACKUP -> "Multilingual Backup"
            BrainModelRole.LITE_FALLBACK -> "Lite Fallback"
            else -> role.wireValue
        }
        return BrainRouteDecision(
            selectedRole = role,
            reason = "$displayName is ready.",
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf("Safe local route.")
        )
    }
}
