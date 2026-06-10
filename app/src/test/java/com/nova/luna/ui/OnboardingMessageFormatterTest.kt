package com.nova.luna.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMessageFormatterTest {
    @Test
    fun appendsBrainStatusAfterTheCoreOnboardingSteps() {
        val message = buildOnboardingMessage(
            steps = listOf(
                OnboardingStep("Welcome", "Local first."),
                OnboardingStep("Safety", "You stay in control.")
            ),
            brainStatusLine = "AI Brain: Multilingual Backup (Full) - Model source not configured.",
            brainActionHint = "Open Diagnostics after setup to review the AI brain status."
        )

        assertTrue(message.contains("Welcome: Local first."))
        assertTrue(message.contains("Safety: You stay in control."))
        assertTrue(message.contains("AI Brain Setup"))
        assertTrue(message.contains("AI Brain: Multilingual Backup (Full) - Model source not configured."))
        assertTrue(message.contains("Open Diagnostics after setup to review the AI brain status."))
    }
}
