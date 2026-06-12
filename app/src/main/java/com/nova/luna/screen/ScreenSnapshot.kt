package com.nova.luna.screen

data class ScreenSnapshot(
    val packageName: String,
    val appName: String?,
    val className: String?,
    val timestamp: Long,
    val screenText: List<String>,
    val elements: List<ScreenElement>,
    val focusedElement: ScreenElement?,
    val scrollableContainers: List<ScreenElement>,
    val editableFields: List<ScreenElement>,
    val clickableElements: List<ScreenElement>,
    val detectedScreenType: ScreenType,
    val riskSignals: List<String>
)
