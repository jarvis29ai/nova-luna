package com.nova.luna.agent

import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.model.CommandResult
import com.nova.luna.screen.ScreenRecoveryAdvisor
import com.nova.luna.screen.ScreenRiskSignal
import com.nova.luna.screen.ScreenState

class AgentLoopRecoveryPolicy(
    private val screenRecoveryAdvisor: ScreenRecoveryAdvisor = ScreenRecoveryAdvisor(),
    private val retryPolicy: RetryPolicy = RetryPolicy()
) {
    fun evaluate(
        plan: TaskPlan,
        state: AgentLoopState,
        screenState: ScreenState?,
        lastResult: CommandResult?,
        verification: AgentLoopVerification?,
        pendingConfirmation: PendingConfirmation? = null
    ): AgentLoopDecision {
        val screenVerification = verification?.screenVerification

        if (pendingConfirmation?.isExpired() == true) {
            return AgentLoopDecision.AskUser(
                message = "That confirmation expired. Please ask again.",
                stopReason = AgentLoopStopReason.NEEDS_CONFIRMATION
            )
        }

        if (screenState == null) {
            return if (retryPolicy.canRetry(state, retryable = true)) {
                AgentLoopDecision.Recover(
                    message = "I could not read the screen yet. I will try once more.",
                    stopReason = AgentLoopStopReason.NO_ACCESSIBILITY,
                    retryable = true
                )
            } else {
                AgentLoopDecision.AskUser(
                    message = "I could not read the screen. Please keep the app open and try again.",
                    stopReason = AgentLoopStopReason.NO_ACCESSIBILITY
                )
            }
        }

        if (!screenState.isAccessibilityReady) {
            return AgentLoopDecision.AskUser(
                message = screenRecoveryAdvisor.buildUnavailableMessage(false),
                stopReason = AgentLoopStopReason.NO_ACCESSIBILITY
            )
        }

        if (screenState.isSensitiveScreen()) {
            return when {
                ScreenRiskSignal.PAYMENT in screenState.riskSignals ->
                    AgentLoopDecision.ManualHandoff(
                        message = "This looks like a payment screen. Please finish it manually.",
                        stopReason = AgentLoopStopReason.PAYMENT_REQUIRED
                    )

                ScreenRiskSignal.OTP in screenState.riskSignals ||
                    ScreenRiskSignal.PASSWORD in screenState.riskSignals ||
                    ScreenRiskSignal.PIN in screenState.riskSignals ||
                    ScreenRiskSignal.CVV in screenState.riskSignals ->
                    AgentLoopDecision.ManualHandoff(
                        message = "This screen asks for OTP, PIN, password, or card details, so it must stay manual.",
                        stopReason = AgentLoopStopReason.OTP_OR_SECRET_REQUIRED
                    )

                ScreenRiskSignal.CAPTCHA in screenState.riskSignals ->
                    AgentLoopDecision.ManualHandoff(
                        message = "This looks like a CAPTCHA screen. Please complete it manually.",
                        stopReason = AgentLoopStopReason.CAPTCHA_REQUIRED
                    )

                ScreenRiskSignal.BIOMETRIC in screenState.riskSignals ->
                    AgentLoopDecision.ManualHandoff(
                        message = "This looks like a biometric step. Please complete it manually.",
                        stopReason = AgentLoopStopReason.OTP_OR_SECRET_REQUIRED
                    )

                ScreenRiskSignal.PERMISSION in screenState.riskSignals ->
                    AgentLoopDecision.AskUser(
                        message = "This looks like a permission prompt. Please review it manually.",
                        stopReason = AgentLoopStopReason.PERMISSION_REQUIRED
                    )

                ScreenRiskSignal.LOGIN in screenState.riskSignals ->
                    AgentLoopDecision.ManualHandoff(
                        message = "This looks like a login screen. I will not enter passwords or OTPs.",
                        stopReason = AgentLoopStopReason.LOGIN_REQUIRED
                    )

                else ->
                    AgentLoopDecision.Stop(
                        message = screenRecoveryAdvisor.buildRecoveryMessage(screenState),
                        stopReason = AgentLoopStopReason.MANUAL_HANDOFF
                    )
            }
        }

        if (screenState.loadingSignals.isNotEmpty() && retryPolicy.isLoadingRetryAllowed(state)) {
            return AgentLoopDecision.Recover(
                message = "The app is still loading, so I will wait and try once more.",
                stopReason = AgentLoopStopReason.UNKNOWN_FAILURE,
                retryable = true
            )
        }

        if (screenState.errorMessages.isNotEmpty()) {
            return AgentLoopDecision.AskUser(
                message = "I see an error on the screen. Please check it manually and I can try again.",
                stopReason = AgentLoopStopReason.UNKNOWN_FAILURE
            )
        }

        if (screenVerification?.applicable == true &&
            screenVerification?.verified == false &&
            retryPolicy.canRetry(state, retryable = lastResult?.success == true)
        ) {
            return AgentLoopDecision.Recover(
                message = "I tried once but the screen did not change. I will retry safely.",
                stopReason = AgentLoopStopReason.UNKNOWN_FAILURE,
                retryable = true
            )
        }

        if (lastResult != null && !lastResult.success && retryPolicy.canRetry(state, retryable = true)) {
            return AgentLoopDecision.Recover(
                message = "That step did not succeed yet. I will try a safe recovery step.",
                stopReason = AgentLoopStopReason.UNKNOWN_FAILURE,
                retryable = true
            )
        }

        if (state.stuckCount >= plan.maxRetries + 1) {
            return AgentLoopDecision.Stop(
                message = "I got stuck on the same screen, so I stopped safely.",
                stopReason = AgentLoopStopReason.STUCK_DETECTED
            )
        }

        return AgentLoopDecision.Continue(
            message = "Continuing the task."
        )
    }
}
