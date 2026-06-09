package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeFailureTrackerTest {
    @Test
    fun `failure suppresses a role until a success clears it`() {
        var now = 1_000L
        val tracker = ModelRuntimeFailureTracker(
            cooldownMillis = 5_000L,
            clock = { now }
        )

        assertFalse(tracker.isSuppressed(BrainModelRole.CORE_BRAIN))

        tracker.recordFailure(BrainModelRole.CORE_BRAIN, "runtime unavailable")
        assertTrue(tracker.isSuppressed(BrainModelRole.CORE_BRAIN))

        tracker.recordSuccess(BrainModelRole.CORE_BRAIN)
        assertFalse(tracker.isSuppressed(BrainModelRole.CORE_BRAIN))

        tracker.recordFailure(BrainModelRole.CORE_BRAIN, "runtime unavailable")
        now += 6_000L
        assertFalse(tracker.isSuppressed(BrainModelRole.CORE_BRAIN))
    }
}
