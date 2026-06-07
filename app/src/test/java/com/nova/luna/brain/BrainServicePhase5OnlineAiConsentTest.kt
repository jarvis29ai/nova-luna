package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase5OnlineAiConsentTest {
    @Test
    fun `consented online requests use the helper and produce a safe draft`() {
        val service = onlineBrainService()

        val diagnostics = service.diagnose(
            rawText = "latest phone under 30000",
            onlineConsentGiven = true
        )

        assertEquals(BrainModelRole.ONLINE_AI_HELPER, diagnostics.selectedRole)
        assertNotNull(diagnostics.finalBrainAction)
        assertTrue(diagnostics.validatorResult)
        assertTrue(diagnostics.runtimeStatus?.onlineTrace?.used == true)
        assertEquals("Here is a safe draft.", diagnostics.finalBrainAction.reply)
    }
}
