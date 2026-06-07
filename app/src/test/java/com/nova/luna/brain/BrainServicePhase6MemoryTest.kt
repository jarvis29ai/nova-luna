package com.nova.luna.brain

import com.nova.luna.memory.BrainSessionManager
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.InMemoryBrainMemoryStore
import com.nova.luna.memory.LocalUserPreferences
import com.nova.luna.memory.PendingConfirmationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase6MemoryTest {
    @Test
    fun `diagnostics surface memory sessions confirmations and preferences`() {
        val store = InMemoryBrainMemoryStore()
        val manager = BrainSessionManager(store)
        manager.setPreferences(
            LocalUserPreferences(
                preferredLanguage = "hi",
                preferredVoiceResponseStyle = "warm",
                privateMode = false
            )
        )
        manager.startSession(
            sessionType = BrainSessionType.GROCERY,
            sourceCommand = "buy milk",
            normalizedGoal = "buy milk"
        )
        manager.queueConfirmation(
            sessionType = BrainSessionType.GROCERY,
            actionSummary = "Add milk to the cart",
            type = PendingConfirmationType.PLACE_ORDER,
            rawText = "buy milk"
        )

        val diagnostics = BrainService(brainMemoryStore = store).diagnose("cheapest")

        assertEquals(BrainSessionType.GROCERY, diagnostics.activeSessionType)
        assertEquals(1, diagnostics.memorySessionCount)
        assertEquals(1, diagnostics.memoryPendingConfirmationCount)
        assertEquals(BrainSessionType.GROCERY, diagnostics.runtimeStatus?.activeSessionType)
        assertEquals(1, diagnostics.runtimeStatus?.memorySessionCount)
        assertTrue(diagnostics.runtimeStatus?.memoryLoaded == true)
        assertEquals("hi", diagnostics.preferences?.preferredLanguage)
    }
}
