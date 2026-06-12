package com.nova.luna.screen

enum class ScreenAction {
    CLICK,
    TYPE_TEXT,
    FOCUS_FIELD,
    SCROLL,
    GO_BACK,
    WAIT,
    OPEN_APP,
    HUMAN_REQUIRED,
    ASK_CONFIRMATION,
    NO_OP
}

data class ScreenStep(
    val action: ScreenAction,
    val targetElement: ScreenElement? = null,
    val inputText: String? = null,
    val reason: String,
    val riskLevel: Int = 0,
    val requiresConfirmation: Boolean = false,
    val timeoutMs: Long = 5000L,
    val retryPolicy: String = "DEFAULT"
)
