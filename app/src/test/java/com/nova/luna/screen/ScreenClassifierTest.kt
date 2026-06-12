package com.nova.luna.screen

import org.junit.Assert.*
import org.junit.Test
import android.graphics.Rect

class ScreenClassifierTest {

    @Test
    fun testClassifyYouTubeSearch() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            className = null,
            timestamp = System.currentTimeMillis(),
            screenText = listOf("Search YouTube"),
            elements = listOf(
                ScreenElement(
                    id = "search_field",
                    text = null,
                    contentDescription = null,
                    className = "android.widget.EditText",
                    bounds = Rect(0,0,100,100),
                    isClickable = true,
                    isEditable = true,
                    isEnabled = true,
                    isFocused = false,
                    isScrollable = false,
                    isPassword = false,
                    type = ScreenElementType.SEARCH_FIELD,
                    confidence = 1.0f,
                    path = "root/0"
                )
            ),
            focusedElement = null,
            scrollableContainers = emptyList(),
            editableFields = emptyList(),
            clickableElements = emptyList(),
            detectedScreenType = ScreenType.UNKNOWN,
            riskSignals = emptyList()
        )

        val classifier = DefaultScreenClassifier()
        val result = classifier.classify(snapshot)

        assertEquals(ScreenType.YOUTUBE_SEARCH, result.detectedScreenType)
    }

    @Test
    fun testRiskSignalBlocking() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.bank",
            appName = "Bank",
            className = null,
            timestamp = System.currentTimeMillis(),
            screenText = listOf("Enter OTP", "Place order"),
            elements = emptyList(),
            focusedElement = null,
            scrollableContainers = emptyList(),
            editableFields = emptyList(),
            clickableElements = emptyList(),
            detectedScreenType = ScreenType.UNKNOWN,
            riskSignals = emptyList()
        )

        val classifier = DefaultScreenClassifier()
        val result = classifier.classify(snapshot)

        assertTrue(result.riskSignals.contains("otp"))
        assertTrue(result.riskSignals.contains("payment"))
        assertEquals(ScreenType.PAYMENT_OR_CHECKOUT, result.detectedScreenType)
    }

    @Test
    fun testOtpOnlyScreenClassification() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.bank",
            appName = "Bank",
            className = null,
            timestamp = System.currentTimeMillis(),
            screenText = listOf("Enter OTP"),
            elements = emptyList(),
            focusedElement = null,
            scrollableContainers = emptyList(),
            editableFields = emptyList(),
            clickableElements = emptyList(),
            detectedScreenType = ScreenType.UNKNOWN,
            riskSignals = emptyList()
        )

        val classifier = DefaultScreenClassifier()
        val result = classifier.classify(snapshot)

        assertTrue(result.riskSignals.contains("otp"))
        assertEquals(ScreenType.OTP_SCREEN, result.detectedScreenType)
    }
}
