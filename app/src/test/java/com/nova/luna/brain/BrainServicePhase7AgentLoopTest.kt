package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase7AgentLoopTest {
    private val brainService = BrainService()

    @Test
    fun `multi step screen tasks are loop candidates`() {
        val rawText = "read the screen and then open settings"
        val commandIntent = CommandIntent(
            rawText = rawText,
            intentType = IntentType.SENSITIVE,
            actionType = ActionType.OPEN_SETTINGS
        )

        val taskPlan = brainService.buildTaskPlan(
            rawText = rawText,
            commandIntent = commandIntent
        )
        val diagnostics = brainService.diagnose(rawText)

        assertTrue(taskPlan.loopCapable)
        assertTrue(taskPlan.requiresScreenContext)
        assertTrue(taskPlan.maxSteps >= 4)
        assertTrue(diagnostics.agentLoopCandidate)
        assertTrue(diagnostics.runtimeStatus?.agentLoopCandidate == true)
    }

    @Test
    fun `plain open settings stays one shot`() {
        val rawText = "open settings"
        val commandIntent = CommandIntent(
            rawText = rawText,
            intentType = IntentType.SENSITIVE,
            actionType = ActionType.OPEN_SETTINGS
        )

        val taskPlan = brainService.buildTaskPlan(
            rawText = rawText,
            commandIntent = commandIntent
        )

        assertFalse(taskPlan.loopCapable)
        assertEquals(1, taskPlan.maxSteps)
    }
}
