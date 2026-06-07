package com.nova.luna.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class StuckDetectorTest {
    @Test
    fun `repeating the same screen signature is stuck`() {
        val detector = StuckDetector(maxRepeatedScreens = 2)
        val history = listOf(
            TaskStep(
                stepNumber = 1,
                step = AgentLoopStep.READ_SCREEN,
                status = TaskStepStatus.SUCCEEDED,
                screenSummary = "same screen"
            ),
            TaskStep(
                stepNumber = 2,
                step = AgentLoopStep.PLAN_NEXT_STEP,
                status = TaskStepStatus.FAILED,
                screenSummary = "same screen"
            ),
            TaskStep(
                stepNumber = 3,
                step = AgentLoopStep.EXECUTE_ACTION,
                status = TaskStepStatus.FAILED,
                screenSummary = "same screen"
            )
        )

        assertTrue(detector.isStuck(history, currentScreenSignature = "same screen"))
    }
}
