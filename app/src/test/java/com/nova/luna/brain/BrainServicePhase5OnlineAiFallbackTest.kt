package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase5OnlineAiFallbackTest {
    @Test
    fun `unavailable online providers fall back safely to the local path`() {
        val service = onlineBrainService(
            provider = UnavailableOnlineAiProvider(
                reason = "ChatGPT integration pending.",
                providerType = OnlineAiProviderType.CHATGPT
            ),
            config = onlineAiConfig(
                providerType = OnlineAiProviderType.CHATGPT
            ),
            internetAvailable = true
        )

        val diagnostics = service.diagnose("latest phone under 30000")

        assertEquals(BrainModelRole.MOCK_FALLBACK, diagnostics.selectedRole)
        assertFalse(diagnostics.fallbackUsed)
        assertNotNull(diagnostics.runtimeStatus)
        assertTrue(diagnostics.runtimeStatus?.onlineTrace?.failed == true)
        assertEquals(BrainModelRole.MOCK_FALLBACK, diagnostics.runtimeStatus?.selectedBrainRole)
        assertEquals("local_model_unavailable", diagnostics.finalBrainAction.intent)
        assertTrue(diagnostics.finalSafetyDecision.allowed)
    }
}
