package com.nova.luna.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateAnalyzerTest {
    private val analyzer = ScreenStateAnalyzer()

    @Test
    fun `analyzer detects sensitive screen signals from visible nodes`() {
        val nodes = listOf(
            ScreenNode(
                text = "Login",
                contentDescription = "Sign in",
                className = "android.widget.Button",
                isClickable = true,
                semanticRole = ScreenElementType.BUTTON
            ),
            ScreenNode(
                text = "Enter OTP",
                contentDescription = "Verification code",
                className = "android.widget.EditText",
                isEditable = true,
                semanticRole = ScreenElementType.OTP_FIELD,
                riskLabels = listOf(ScreenRiskSignal.OTP, ScreenRiskSignal.SENSITIVE_FIELD)
            ),
            ScreenNode(
                text = "Pay now",
                contentDescription = "Complete payment",
                className = "android.widget.Button",
                isClickable = true,
                semanticRole = ScreenElementType.BUTTON,
                riskLabels = listOf(ScreenRiskSignal.PAYMENT)
            ),
            ScreenNode(
                text = "Loading",
                contentDescription = "Please wait",
                className = "android.widget.TextView",
                semanticRole = ScreenElementType.PROGRESS,
                riskLabels = listOf(ScreenRiskSignal.LOADING)
            )
        )

        val state = analyzer.analyze(
            packageName = "com.example.app",
            appName = "Example App",
            className = "MainActivity",
            timestampMillis = 123L,
            isAccessibilityReady = true,
            nodes = nodes,
            rawNodeCount = nodes.size,
            truncated = false
        )

        assertTrue(state.isSensitiveScreen())
        assertTrue(state.hasRisk(ScreenRiskSignal.OTP))
        assertTrue(state.hasRisk(ScreenRiskSignal.PAYMENT))
        assertTrue(state.hasRisk(ScreenRiskSignal.LOADING))
        assertEquals(1, state.loginSignals.size)
        assertEquals(1, state.otpSignals.size)
        assertEquals(1, state.paymentSignals.size)
        assertEquals(1, state.loadingSignals.size)
        assertTrue(state.summarizedState.contains("Example App"))
        assertTrue(state.confidence > 0f)
    }
}
