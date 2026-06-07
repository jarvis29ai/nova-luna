package com.nova.luna.agent

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.screen.ScreenVerificationResult
import com.nova.luna.screen.ScreenVerificationStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionDetectorTest {
    private val detector = TaskCompletionDetector()

    @Test
    fun `verified screen change completes the loop task`() {
        val plan = TaskPlan(
            goal = "open settings",
            loopCapable = true,
            reason = "Loop completion"
        )
        val state = AgentLoopState(
            taskGoal = plan.goal,
            maxSteps = 4,
            maxRetries = 1,
            currentStepNumber = 1
        )
        val result = CommandResult.success(
            message = "Opened settings",
            intentType = IntentType.SENSITIVE,
            actionType = ActionType.OPEN_SETTINGS
        )
        val verification = AgentLoopVerification(
            screenVerification = ScreenVerificationResult(
                status = ScreenVerificationStatus.CHANGED,
                applicable = true,
                changed = true,
                verified = true,
                message = "The visible screen changed."
            ),
            progressObserved = true,
            verified = true,
            message = "The visible screen changed."
        )

        assertTrue(detector.isComplete(plan, state, null, result, verification))
    }

    @Test
    fun `one shot success completes immediately`() {
        val plan = TaskPlan(
            goal = "go home",
            loopCapable = false,
            reason = "One-shot control"
        )
        val state = AgentLoopState(
            taskGoal = plan.goal,
            maxSteps = 1,
            maxRetries = 0
        )
        val result = CommandResult.success(
            message = "Done",
            intentType = IntentType.CONTROL,
            actionType = ActionType.GO_HOME
        )

        assertTrue(detector.isComplete(plan, state, null, result, null))
    }
}
