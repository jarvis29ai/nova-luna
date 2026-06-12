package com.nova.luna.screen

data class ElementQuery(
    val targetText: String? = null,
    val targetContentDescription: String? = null,
    val targetType: ScreenElementType? = null,
    val resourceIdContains: String? = null,
    val classNameContains: String? = null,
    val packageName: String? = null,
    val preferEditable: Boolean = false,
    val preferClickable: Boolean = false,
    val allowPartialMatch: Boolean = false,
    val synonyms: List<String> = emptyList(),
    val riskAllowed: Boolean = false
)
