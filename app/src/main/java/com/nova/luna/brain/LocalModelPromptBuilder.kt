package com.nova.luna.brain

import com.nova.luna.model.BrainModelCatalogEntry
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision

class LocalModelPromptBuilder(
    private val contextBuilder: LocalBrainRequestContextBuilder = LocalBrainRequestContextBuilder()
) {
    fun build(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        catalogEntry: BrainModelCatalogEntry
    ): String {
        val context = contextBuilder.build(
            request = request,
            routeDecision = routeDecision,
            catalogEntry = catalogEntry
        )

        val systemPrompt = buildString {
            appendLine("You are Nova/Luna's local on-device brain.")
            appendLine("Return exactly one JSON object and nothing else.")
            appendLine("Return candidate JSON only.")
            appendLine("Never output markdown, code fences, prose, or direct execution instructions.")
            appendLine("Never call ActionExecutor directly.")
            appendLine("SafetyGate and BrainActionValidator still decide whether any candidate may execute.")
            appendLine("Preserve the user's language, including Hindi, Hinglish, and regional language when applicable.")
            appendLine("Required fields: schemaVersion or schema_version, intent, actionType or action_type, riskLevel or risk_level, params, confirmationRequired or requires_confirmation.")
            appendLine("Recommended fields: reply, assistantReply or assistant_reply, reason, confidence, finalActionAllowed or final_action_allowed, source, nextQuestion or next_question.")
            appendLine()
            appendLine("Allowed capabilities:")
            context.allowedCapabilities.forEach { capability ->
                appendLine("- $capability")
            }
            appendLine()
            appendLine("Safe context:")
            appendLine("- userCommand: ${context.userCommand}")
            appendLine("- userText: ${context.userCommand}")
            appendLine("- normalizedCommand: ${context.normalizedCommand}")
            context.preferredLanguage?.let {
                appendLine("- preferredLanguage: $it")
            }
            context.activeSessionSummary?.let {
                appendLine("- activeSession: $it")
            }
            context.pendingConfirmationSummary?.let {
                appendLine("- pendingConfirmation: $it")
            }
            context.screenSummary?.let {
                appendLine("- screenSummary: $it")
            }
            context.appSummary?.let {
                appendLine("- appSummary: $it")
            }
            if (context.safetyNotes.isNotEmpty()) {
                appendLine("- safetyNotes: ${context.safetyNotes.joinToString(" | ")}")
            }

            when (routeDecision.selectedRole) {
                BrainModelRole.CORE_BRAIN -> {
                    appendLine()
                    appendLine("Core Brain guidance:")
                    appendLine("- Handle complex commands, planning, and app-control understanding.")
                }

                BrainModelRole.MULTILINGUAL_BACKUP -> {
                    appendLine()
                    appendLine("Multilingual Backup guidance:")
                    appendLine("- Prioritize Hindi, Hinglish, and translation-heavy understanding.")
                }

                BrainModelRole.LITE_FALLBACK -> {
                    appendLine()
                    appendLine("Lite Fallback guidance:")
                    appendLine("- Keep the response lightweight, concise, and failure-safe.")
                }

                else -> Unit
            }
        }.trimEnd()

        return buildString {
            appendLine("<|im_start|>system")
            appendLine(systemPrompt)
            appendLine("<|im_end|>")
            appendLine("<|im_start|>user")
            appendLine(context.userCommand.trim())
            appendLine("<|im_end|>")
            appendLine("<|im_start|>assistant")
        }.trimEnd()
    }
}
