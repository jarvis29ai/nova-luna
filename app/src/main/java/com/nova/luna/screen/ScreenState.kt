package com.nova.luna.screen

data class ScreenState(
    val packageName: String,
    val appName: String? = null,
    val className: String? = null,
    val timestampMillis: Long,
    val isAccessibilityReady: Boolean,
    val visibleText: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val clickableElements: List<ScreenNode> = emptyList(),
    val editableFields: List<ScreenNode> = emptyList(),
    val scrollableElements: List<ScreenNode> = emptyList(),
    val selectedElements: List<ScreenNode> = emptyList(),
    val focusedElement: ScreenNode? = null,
    val enabledElements: List<ScreenNode> = emptyList(),
    val disabledElements: List<ScreenNode> = emptyList(),
    val possibleButtons: List<ScreenNode> = emptyList(),
    val possibleSearchFields: List<ScreenNode> = emptyList(),
    val possibleListsOrCards: List<ScreenNode> = emptyList(),
    val errorMessages: List<String> = emptyList(),
    val loadingSignals: List<String> = emptyList(),
    val permissionSignals: List<String> = emptyList(),
    val loginSignals: List<String> = emptyList(),
    val paymentSignals: List<String> = emptyList(),
    val otpSignals: List<String> = emptyList(),
    val passwordSignals: List<String> = emptyList(),
    val captchaSignals: List<String> = emptyList(),
    val biometricSignals: List<String> = emptyList(),
    val riskSignals: List<ScreenRiskSignal> = emptyList(),
    val summarizedState: String,
    val confidence: Float,
    val rawNodeCount: Int,
    val truncated: Boolean,
    val nodes: List<ScreenNode> = emptyList()
) {
    fun hasRisk(signal: ScreenRiskSignal): Boolean {
        return riskSignals.contains(signal)
    }

    fun isSensitiveScreen(): Boolean {
        return riskSignals.isNotEmpty()
    }

    fun signature(): String {
        return buildString {
            append(packageName)
            append('|')
            append(appName.orEmpty())
            append('|')
            append(className.orEmpty())
            append('|')
            append(visibleText.take(12).joinToString(separator = "~"))
            append('|')
            append(contentDescriptions.take(12).joinToString(separator = "~"))
            append('|')
            append(clickableElements.size)
            append('|')
            append(editableFields.size)
            append('|')
            append(scrollableElements.size)
            append('|')
            append(selectedElements.size)
            append('|')
            append(focusedElement?.fingerprint().orEmpty())
            append('|')
            append(riskSignals.joinToString(separator = ","))
            append('|')
            append(summarizedState)
        }
    }
}
