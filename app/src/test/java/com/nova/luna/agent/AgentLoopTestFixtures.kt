package com.nova.luna.agent

import com.nova.luna.screen.ScreenRiskSignal
import com.nova.luna.screen.ScreenState

internal fun testScreenState(
    summary: String = "Test screen",
    riskSignals: List<ScreenRiskSignal> = emptyList(),
    isAccessibilityReady: Boolean = true
): ScreenState {
    return ScreenState(
        packageName = "com.example.app",
        appName = "Example App",
        className = "MainActivity",
        timestampMillis = 123L,
        isAccessibilityReady = isAccessibilityReady,
        visibleText = listOf(summary),
        contentDescriptions = listOf(summary),
        clickableElements = emptyList(),
        editableFields = emptyList(),
        scrollableElements = emptyList(),
        selectedElements = emptyList(),
        focusedElement = null,
        enabledElements = emptyList(),
        disabledElements = emptyList(),
        possibleButtons = emptyList(),
        possibleSearchFields = emptyList(),
        possibleListsOrCards = emptyList(),
        errorMessages = emptyList(),
        loadingSignals = if (riskSignals.contains(ScreenRiskSignal.LOADING)) listOf("loading") else emptyList(),
        permissionSignals = if (riskSignals.contains(ScreenRiskSignal.PERMISSION)) listOf("permission") else emptyList(),
        loginSignals = if (riskSignals.contains(ScreenRiskSignal.LOGIN)) listOf("login") else emptyList(),
        paymentSignals = if (riskSignals.contains(ScreenRiskSignal.PAYMENT)) listOf("payment") else emptyList(),
        otpSignals = if (riskSignals.contains(ScreenRiskSignal.OTP)) listOf("otp") else emptyList(),
        passwordSignals = if (riskSignals.contains(ScreenRiskSignal.PASSWORD)) listOf("password") else emptyList(),
        captchaSignals = if (riskSignals.contains(ScreenRiskSignal.CAPTCHA)) listOf("captcha") else emptyList(),
        biometricSignals = if (riskSignals.contains(ScreenRiskSignal.BIOMETRIC)) listOf("biometric") else emptyList(),
        riskSignals = riskSignals,
        summarizedState = summary,
        confidence = 0.9f,
        rawNodeCount = 1,
        truncated = false,
        nodes = emptyList()
    )
}
