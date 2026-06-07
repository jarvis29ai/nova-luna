package com.nova.luna.brain

import com.nova.luna.model.BrainCapabilityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAwareBrainSelectorPhase4Test {
    @Test
    fun `local llm dev selects phone local provider when available`() {
        val localProvider = FakePhoneBrainProvider(available = true)

        val selection = NetworkAwareBrainSelector().select(
            capabilityMode = BrainCapabilityMode.LOCAL_LLM_DEV,
            internetAvailable = false,
            localModelAvailable = true,
            phoneBrainProvider = UnavailablePhoneBrainProvider(),
            localLlmProvider = localProvider,
            fallbackProvider = LocalMockBrainProvider()
        )

        assertTrue(selection.provider === localProvider)
        assertEquals(BrainCapabilityMode.LOCAL_LLM_DEV, selection.runtimeStatus.capabilityMode)
        assertTrue(selection.runtimeStatus.localModelAvailable)
        assertFalse(selection.runtimeStatus.fallbackActive)
        assertEquals("FakePhoneBrainProvider", selection.runtimeStatus.selectedProvider)
    }

    @Test
    fun `local llm dev falls back when the local model is unavailable`() {
        val localProvider = FakePhoneBrainProvider(available = false)

        val selection = NetworkAwareBrainSelector().select(
            capabilityMode = BrainCapabilityMode.LOCAL_LLM_DEV,
            internetAvailable = false,
            localModelAvailable = false,
            phoneBrainProvider = UnavailablePhoneBrainProvider(),
            localLlmProvider = localProvider,
            fallbackProvider = LocalMockBrainProvider()
        )

        assertTrue(selection.provider is LocalMockBrainProvider)
        assertTrue(selection.runtimeStatus.fallbackActive)
        assertEquals("LocalMockBrainProvider", selection.runtimeStatus.selectedProvider)
    }
}
