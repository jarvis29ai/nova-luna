package com.nova.luna.screen

import android.graphics.Rect

data class ScreenNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val viewIdResourceName: String? = null,
    val bounds: Rect? = null,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val depth: Int = 0,
    val childCount: Int = 0,
    val semanticRole: ScreenElementType = ScreenElementType.UNKNOWN,
    val riskLabels: List<ScreenRiskSignal> = emptyList()
) {
    fun primaryLabel(): String? {
        return text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: viewIdResourceName?.takeIf { it.isNotBlank() }
            ?: className?.takeIf { it.isNotBlank() }
    }

    fun fingerprint(): String {
        return buildString {
            append(text.orEmpty())
            append('|')
            append(contentDescription.orEmpty())
            append('|')
            append(className.orEmpty())
            append('|')
            append(packageName.orEmpty())
            append('|')
            append(viewIdResourceName.orEmpty())
            append('|')
            append(isClickable)
            append('|')
            append(isEditable)
            append('|')
            append(isScrollable)
            append('|')
            append(isEnabled)
            append('|')
            append(isFocused)
            append('|')
            append(isSelected)
            append('|')
            append(depth)
            append('|')
            append(childCount)
            append('|')
            append(semanticRole.name)
            append('|')
            append(riskLabels.joinToString(separator = ","))
        }
    }
}
