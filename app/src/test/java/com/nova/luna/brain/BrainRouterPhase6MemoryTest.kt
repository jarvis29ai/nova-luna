package com.nova.luna.brain

import com.nova.luna.memory.BrainMemorySnapshot
import com.nova.luna.memory.BrainSession
import com.nova.luna.memory.BrainSessionStatus
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRouterPhase6MemoryTest {
    @Test
    fun `active grocery memory keeps follow up prompts on the structured action route`() {
        val router = BrainRouter()
        val decision = router.route(
            BrainRequest(
                rawText = "cheapest",
                memorySnapshot = BrainMemorySnapshot(
                    sessions = mapOf(BrainSessionType.GROCERY to activeSession(BrainSessionType.GROCERY))
                )
            )
        )

        assertEquals(BrainModelRole.ACTION_JSON, decision.selectedRole)
        assertTrue(decision.reason.contains("active grocery", ignoreCase = true))
    }

    @Test
    fun `active music memory keeps simple playback follow ups on the lite command route`() {
        val router = BrainRouter()
        val decision = router.route(
            BrainRequest(
                rawText = "continue",
                activeSessionType = BrainSessionType.MUSIC,
                memorySnapshot = BrainMemorySnapshot(
                    sessions = mapOf(BrainSessionType.MUSIC to activeSession(BrainSessionType.MUSIC))
                )
            )
        )

        assertEquals(BrainModelRole.LITE_COMMAND, decision.selectedRole)
        assertTrue(decision.reason.contains("No ready local model was available", ignoreCase = true))
    }

    private fun activeSession(sessionType: BrainSessionType): BrainSession {
        return BrainSession(
            sessionId = "$sessionType-session",
            sessionType = sessionType,
            status = BrainSessionStatus.ACTIVE,
            startedAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            sourceCommand = "start $sessionType",
            normalizedGoal = "continue $sessionType",
            activeDomain = sessionType
        )
    }
}
