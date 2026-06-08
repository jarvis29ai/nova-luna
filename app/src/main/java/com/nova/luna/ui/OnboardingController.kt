package com.nova.luna.ui

import android.content.Context
import android.content.SharedPreferences

class OnboardingController(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("luna_onboarding", Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getBoolean("complete", false)

    fun markComplete() {
        prefs.edit().putBoolean("complete", true).apply()
    }

    fun reset() {
        prefs.edit().putBoolean("complete", false).apply()
    }

    fun getSteps(): List<OnboardingStep> {
        return listOf(
            OnboardingStep("Welcome to Nova/Luna", "Your personal phone-only AI assistant."),
            OnboardingStep("Privacy First", "All processing and memory stay on your phone. No cloud, no tracking."),
            OnboardingStep("Permissions", "We need Microphone and Accessibility permissions to hear you and help with tasks."),
            OnboardingStep("Safety Gate", "Risky actions like payments or bookings always require your final confirmation."),
            OnboardingStep("Ready to Go", "Tap the mic orb at the bottom of the screen to start talking to Luna.")
        )
    }
}

data class OnboardingStep(val title: String, val message: String)
