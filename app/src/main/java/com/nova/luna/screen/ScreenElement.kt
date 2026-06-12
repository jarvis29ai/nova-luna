package com.nova.luna.screen

import android.graphics.Rect

data class ScreenElement(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isEnabled: Boolean,
    val isFocused: Boolean,
    val isScrollable: Boolean,
    val isPassword: Boolean,
    val type: ScreenElementType,
    val confidence: Float,
    val path: String,
    val parentHint: String? = null
)
