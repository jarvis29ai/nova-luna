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

    @Test
    fun `sequential failover from core to full to lite`() {
        val selector = MultiModelRoleSelector()

        // Use a long request (> 6 words) to ensure it's complex but NOT a lite fallback candidate.
        val requestText = "please explain how automatic failover between local models works on android"

        // Case 1: Core is NOT ready, but Full and Lite are. Should pick Full.
        val readiness1 = BrainRoleReadinessProvider { role ->
            role == BrainModelRole.MULTILINGUAL_BACKUP || role == BrainModelRole.LITE_FALLBACK
        }
        val selection1 = selector.select(
            request = BrainRequest(requestText),
            readinessProvider = readiness1
        )
        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, selection1?.selectedRole)
        assertTrue(selection1?.reason?.contains("next ready local fallback after Core Brain", ignoreCase = true) == true)

        // Case 2: Core and Full are NOT ready, only Lite is. Should pick Lite.
        val readiness2 = BrainRoleReadinessProvider { role ->
            role == BrainModelRole.LITE_FALLBACK
        }
        val selection2 = selector.select(
            request = BrainRequest(requestText),
            readinessProvider = readiness2
        )
        assertEquals(BrainModelRole.LITE_FALLBACK, selection2?.selectedRole)
        assertTrue(selection2?.reason?.contains("next ready local fallback after Core Brain", ignoreCase = true) == true)

        // Case 3: None are ready. Should return null.
        val readiness3 = BrainRoleReadinessProvider { false }
        val selection3 = selector.select(
            request = BrainRequest(requestText),
            readinessProvider = readiness3
        )
        assertEquals(null, selection3)
    }
}
