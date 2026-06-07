package com.nova.luna.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.memory.BrainSessionManager
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.InMemoryBrainMemoryStore
import com.nova.luna.memory.PendingConfirmationType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainPhase6MemoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `confirmed pending grocery requests are replayed from the shared memory store`() {
        val store = InMemoryBrainMemoryStore()
        val brainAction = BrainAction(
            intent = "grocery_booking",
            reply = "Add milk to the cart.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to "buy milk",
                "selectedItem" to "milk"
            ),
            nextQuestion = "Add milk to the cart?"
        )
        val manager = BrainSessionManager(store)
        manager.queueConfirmation(
            sessionType = BrainSessionType.GROCERY,
            actionSummary = brainAction.reply,
            type = PendingConfirmationType.PLACE_ORDER,
            rawText = "buy milk",
            brainAction = brainAction
        )

        val brain = CommandBrain(context, brainMemoryStore = store)
        val result = brain.process("yes")

        assertTrue(result.success)
        assertEquals(BrainSessionType.GROCERY, result.memorySessionType)
        assertEquals(0, store.snapshot().activePendingConfirmationCount)
    }
}
