package com.nova.luna.ui

fun buildOnboardingMessage(
    steps: List<OnboardingStep>,
    brainStatusLine: String? = null,
    brainActionHint: String? = null
): String {
    val sections = mutableListOf<String>()

    if (steps.isNotEmpty()) {
        sections += steps.joinToString("\n\n") { step ->
            "${step.title}: ${step.message}"
        }
    }

    if (!brainStatusLine.isNullOrBlank()) {
        sections += buildString {
            appendLine("AI Brain Setup")
            appendLine(brainStatusLine)
            if (!brainActionHint.isNullOrBlank()) {
                append(brainActionHint)
            }
        }.trim()
    }

    return sections.joinToString("\n\n")
}
