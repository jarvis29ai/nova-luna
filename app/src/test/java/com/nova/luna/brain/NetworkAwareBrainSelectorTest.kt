package com.nova.luna.brain

import com.nova.luna.model.BrainCapabilityMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAwareBrainSelectorTest {
    @Test
    fun `no internet falls back safely to the mock provider`() {
        val selection = NetworkAwareBrainSelector().select(
            capabilityMode = BrainCapabilityMode.ONLINE_ASSISTED,
            internetAvailable = false,
            localModelAvailable = false,
            phoneBrainProvider = UnavailablePhoneBrainProvider(),
            localLlmProvider = null,
            fallbackProvider = LocalMockBrainProvider()
        )

        assertTrue(selection.provider is LocalMockBrainProvider)
        assertFalse(selection.runtimeStatus.internetAvailable)
        assertTrue(selection.runtimeStatus.fallbackActive)
        assertTrue(selection.runtimeStatus.reason.contains("LocalMockBrainProvider"))
    }

    @Test
    fun `offline only mode stays local and keeps safety chain active`() {
        val selection = NetworkAwareBrainSelector().select(
            capabilityMode = BrainCapabilityMode.OFFLINE_ONLY,
            internetAvailable = false,
            localModelAvailable = false,
            phoneBrainProvider = UnavailablePhoneBrainProvider(),
            localLlmProvider = null,
            fallbackProvider = LocalMockBrainProvider()
        )

        assertTrue(selection.provider is LocalMockBrainProvider)
        assertFalse(selection.runtimeStatus.internetAvailable)
        assertTrue(selection.runtimeStatus.safetyChainActive)
    }
}
