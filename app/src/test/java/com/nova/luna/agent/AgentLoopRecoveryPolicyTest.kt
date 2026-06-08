package com.nova.luna.agent

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.screen.ScreenRiskSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopRecoveryPolicyTest {
    private val policy = AgentLoopRecoveryPolicy()

    @Test
    fun `sensitive screens are handed off manually`() {
        val decision = policy.evaluate(
            plan = TaskPlan(
                goal = "enter otp",
                loopCapable = true,
                reason = "Sensitive screen recovery",
                maxRetries = 1
            ),
            state = AgentLoopState(
                taskGoal = "enter otp",
                maxSteps = 4,
                maxRetries = 1
            ),
            screenState = testScreenState(
                summary = "OTP verification",
                riskSignals = listOf(ScreenRiskSignal.OTP)
            ),
            lastResult = null,
            verification = null
        )

        assertTrue(decision is AgentLoopDecision.ManualHandoff)
        assertEquals(AgentLoopStopReason.OTP_OR_SECRET_REQUIRED, decision.stopReason)
    }

    @Test
    fun `missing screen gets one safe recovery attempt`() {
        val decision = policy.evaluate(
            plan = TaskPlan(
                goal = "open settings",
                loopCapable = true,
                reason = "Screen recovery",
                maxRetries = 1
            ),
            state = AgentLoopState(
                taskGoal = "open settings",
                maxSteps = 4,
                maxRetries = 1
            ),
            screenState = null,
            lastResult = CommandResult.failure(
                message = "Could not read the screen yet.",
                intentType = IntentType.SENSITIVE,
                actionType = ActionType.OPEN_SETTINGS
            ),

            verification = null
        )

        assertTrue(decision is AgentLoopDecision.Recover)
        assertEquals(AgentLoopStopReason.NO_ACCESSIBILITY, decision.stopReason)
    }
}
