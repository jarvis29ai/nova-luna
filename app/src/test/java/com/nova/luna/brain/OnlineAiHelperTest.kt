package com.nova.luna.brain

import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiHelperTest {
    @Test
    fun `permission prompt is returned when consent is missing`() {
        val helper = OnlineAiHelper(
            config = onlineAiConfig(),
            provider = FakeOnlineAiProvider(),
            networkStatusProvider = StaticNetworkStatusProvider(true)
        )

        val result = helper.generate(
            request = BrainRequest("latest phone under 30000"),
            routeDecision = onlineRouteDecision()
        )

        assertTrue(result.available)
        assertNotNull(result.candidateAction)
        assertEquals("online_ai_permission", result.candidateAction?.intent)
        assertEquals(BrainActionType.PREPARE, result.candidateAction?.actionType)
        assertEquals(OnlineAiStatus.ASK_USER_PERMISSION, result.onlineTrace?.status)
        assertTrue(result.onlineTrace?.used == false)
    }

    @Test
    fun `safe provider output is sanitized into a read only draft`() {
        val helper = OnlineAiHelper(
            config = onlineAiConfig(),
            provider = FakeOnlineAiProvider(),
            networkStatusProvider = StaticNetworkStatusProvider(true)
        )

        val result = helper.generate(
            request = BrainRequest(
                rawText = "compare iPhone and Pixel",
                onlineConsentGiven = true
            ),
            routeDecision = onlineRouteDecision()
        )

        assertTrue(result.available)
        assertNotNull(result.candidateAction)
        assertEquals(BrainActionType.READ_ONLY, result.candidateAction?.actionType)
        assertFalse(result.candidateAction?.finalActionAllowed ?: true)
        assertEquals(OnlineAiStatus.SANITIZED, result.onlineTrace?.status)
        assertTrue(result.onlineTrace?.used == true)
    }

    @Test
    fun `unsafe provider output is rejected`() {
        val helper = OnlineAiHelper(
            config = onlineAiConfig(),
            provider = UnsafeOnlineAiProvider(),
            networkStatusProvider = StaticNetworkStatusProvider(true)
        )

        val result = helper.generate(
            request = BrainRequest(
                rawText = "compare iPhone and Pixel",
                onlineConsentGiven = true
            ),
            routeDecision = onlineRouteDecision()
        )

        assertFalse(result.available)
        assertTrue("trace=${result.onlineTrace}", result.onlineTrace?.failed == true)
        assertTrue(
            "trace=${result.onlineTrace}",
            result.onlineTrace?.status == OnlineAiStatus.REJECTED || result.onlineTrace?.status == OnlineAiStatus.FAILED
        )
        assertTrue("result=$result", result.reason.contains("unsafe", ignoreCase = true))
    }

    private fun onlineRouteDecision(): BrainRouteDecision {
        return BrainRouteDecision(
            selectedRole = BrainModelRole.ONLINE_AI_HELPER,
            reason = "Online helper candidate.",
            requiresInternet = true,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf("Keep the helper read only.")
        )
    }

    private class UnsafeOnlineAiProvider : OnlineAiProvider {
        override val providerType: OnlineAiProviderType = OnlineAiProviderType.FAKE
        override val available: Boolean = true

        override fun generate(prompt: String, timeoutMs: Long): OnlineAiResult {
            return OnlineAiResult(
                providerType = providerType,
                status = OnlineAiStatus.READY,
                available = true,
                rawResponse = """{"intent":"cab_booking","reply":"I paid and booked it.","actionType":"external_action","riskLevel":"confirmation_required","requiresConfirmation":true,"finalActionAllowed":true,"params":{"source":"unsafe"}}""",
                reason = "Unsafe output.",
                promptBuilt = true,
                providerName = "UnsafeOnlineAiProvider",
                latencyMillis = 0L
            )
        }
    }
}
