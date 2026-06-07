package com.nova.luna.screen

import com.nova.luna.util.AccessibilityReadiness
import java.util.Locale

class ScreenRecoveryAdvisor {
    fun buildUnavailableMessage(isAccessibilityReady: Boolean): String {
        return if (isAccessibilityReady) {
            "I could not capture the active screen yet. Please keep the app open and try again."
        } else {
            AccessibilityReadiness.blockedMessage()
        }
    }

    fun buildRecoveryMessage(state: ScreenState): String {
        val signals = state.riskSignals.toSet()
        return when {
            !state.isAccessibilityReady -> buildUnavailableMessage(false)
            ScreenRiskSignal.PAYMENT in signals ->
                "This looks like a payment or checkout screen. I can read it, but I will not complete it automatically."
            ScreenRiskSignal.OTP in signals ||
                ScreenRiskSignal.PASSWORD in signals ||
                ScreenRiskSignal.PIN in signals ||
                ScreenRiskSignal.CVV in signals ->
                "This looks like a verification or password screen. I can help read it, but I will not enter secrets."
            ScreenRiskSignal.CAPTCHA in signals ->
                "This looks like a CAPTCHA screen. Please complete it manually."
            ScreenRiskSignal.BIOMETRIC in signals ->
                "This looks like a biometric step. Please complete it manually."
            ScreenRiskSignal.PERMISSION in signals ->
                "This looks like a permission prompt. Please review it manually."
            ScreenRiskSignal.ERROR in signals ->
                "I see an error on the screen. I can help read it and suggest a safe next step."
            ScreenRiskSignal.LOADING in signals ->
                "The app still looks like it is loading. Let's wait before tapping again."
            ScreenRiskSignal.LOGIN in signals ->
                "This looks like a login screen. I can read it, but I will not enter passwords or OTPs."
            else ->
                "I can safely summarize the current screen."
        }
    }

    fun buildSummary(state: ScreenState): String {
        val pieces = buildList {
            val title = listOfNotNull(
                state.appName?.takeIf { it.isNotBlank() },
                state.packageName.takeIf { it.isNotBlank() }
            ).joinToString(separator = " / ")
            if (title.isNotBlank()) add(title)

            if (state.visibleText.isNotEmpty()) {
                add("${state.visibleText.size} visible text items")
            }

            if (state.clickableElements.isNotEmpty()) {
                add("${state.clickableElements.size} tappable items")
            }

            if (state.editableFields.isNotEmpty()) {
                add("${state.editableFields.size} editable fields")
            }

            if (state.scrollableElements.isNotEmpty()) {
                add("${state.scrollableElements.size} scrollable areas")
            }

            if (state.riskSignals.isNotEmpty()) {
                add("risk: ${state.riskSignals.joinToString(separator = ",") { it.name.lowercase(Locale.US) }}")
            }

            if (state.truncated) {
                add("screen tree truncated")
            }
        }

        return if (pieces.isEmpty()) {
            "I can safely summarize the current screen."
        } else {
            pieces.joinToString(separator = ". ").trim().take(240)
        }
    }

    fun buildNextQuestion(state: ScreenState): String? {
        if (!state.isAccessibilityReady) {
            return "Please enable accessibility and try again."
        }

        if (state.errorMessages.isNotEmpty()) {
            return "Would you like me to read the error message?"
        }

        if (state.permissionSignals.isNotEmpty()) {
            return "Would you like me to explain the permission prompt?"
        }

        if (state.possibleSearchFields.isNotEmpty()) {
            return "Would you like me to focus the search field?"
        }

        if (state.possibleButtons.isNotEmpty()) {
            return "Would you like me to read the visible buttons?"
        }

        return null
    }
}
