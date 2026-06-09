package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiModelRoleSelectorTest {
    @Test
    fun `multilingual requests prefer the multilingual backup role`() {
        val selector = MultiModelRoleSelector()
        val readiness = BrainRoleReadinessProvider { role ->
            role == BrainModelRole.MULTILINGUAL_BACKUP ||
                role == BrainModelRole.CORE_BRAIN ||
                role == BrainModelRole.LITE_FALLBACK
        }

        val selection = selector.select(
            request = BrainRequest("कृपया मुझे समझाओ"),
            readinessProvider = readiness
        )

        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, selection?.selectedRole)
        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, selection?.candidateRoles?.first())
        assertTrue(selection?.reason?.contains("Multilingual Backup", ignoreCase = true) == true)
    }

    @Test
    fun `suppressed core role falls back to lite when the core pack fails`() {
        val tracker = ModelRuntimeFailureTracker(
            cooldownMillis = 60_000L,
            clock = { 0L }
        )
        tracker.recordFailure(BrainModelRole.CORE_BRAIN, "runtime failed")

        val selector = MultiModelRoleSelector(
            failureTracker = tracker
        )
        val readiness = BrainRoleReadinessProvider { role ->
            role == BrainModelRole.CORE_BRAIN ||
                role == BrainModelRole.LITE_FALLBACK
        }

        val selection = selector.select(
            request = BrainRequest("please explain how offline model verification works"),
            readinessProvider = readiness
        )

        assertEquals(BrainModelRole.LITE_FALLBACK, selection?.selectedRole)
        assertEquals(
            listOf(
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.MULTILINGUAL_BACKUP,
                BrainModelRole.LITE_FALLBACK
            ),
            selection?.candidateRoles
        )
        assertTrue(selection?.reason?.contains("fallback", ignoreCase = true) == true)
    }
}
