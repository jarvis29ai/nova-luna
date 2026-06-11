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

        return buildString {
            appendLine("You are Nova/Luna's local on-device brain.")
            appendLine("Role: ${context.selectedRole.wireValue}")
            appendLine("Role name: ${context.selectedModelDisplayName}")
            appendLine("Model families: ${context.selectedModelFamilies.joinToString(", ")}")
            appendLine("You must output candidate JSON only.")
            appendLine("Never output markdown, code fences, prose, or direct execution instructions.")
            appendLine("Never call ActionExecutor directly.")
            appendLine("SafetyGate and BrainActionValidator still decide whether any candidate may execute.")
            appendLine("Do not mention raw private model paths, secrets, or unrestricted filesystem access.")
            appendLine("If the request is unsafe or unclear, ask a clarifying question in the JSON candidate.")
            appendLine("Keep final actions manual whenever confirmation is required.")
            appendLine("Preserve the user's language, including Hindi, Hinglish, and regional language when applicable.")
            appendLine()
            appendLine("Candidate JSON schema:")
            appendLine("{")
            appendLine("""  "intent": "string",""")
            appendLine("""  "actionType": "OPEN_APP|OPEN_CAMERA|OPEN_SETTINGS|TOGGLE_FLASHLIGHT|SET_DEVICE_SETTING|SEARCH_WEB|SEARCH_YOUTUBE|PLAY_MEDIA|MAKE_CALL_DRAFT|SEND_MESSAGE_DRAFT|CREATE_CONTENT|ASK_QUESTION|CAB_SEARCH|FOOD_SEARCH|GROCERY_SEARCH|BOOKING_REQUEST|PAYMENT_REQUEST|OTP_REQUEST|LOGIN_REQUEST|CAPTCHA_REQUEST|DESTRUCTIVE_REQUEST|PRIVACY_SENSITIVE_REQUEST|ASK_CLARIFICATION|UNSUPPORTED|UNKNOWN",""")
            appendLine("""  "riskLevel": "LOW|MEDIUM|HIGH|HUMAN_ONLY|UNKNOWN",""")
            appendLine("""  "requiresConfirmation": true|false,""")
            appendLine("""  "params": { "any_key": "string" },""")
            appendLine("""  "confidence": 0.0 to 1.0,""")
            appendLine("""  "language": "en|hi|hinglish|unknown",""")
            appendLine("""  "assistantReply": "string",""")
            appendLine("""  "reason": "string"""")
            appendLine("}")
            appendLine()
            appendLine("Risk Rules:")
            appendLine("- LOW: app launch, camera, settings, flashlight, media, web search, simple questions.")
            appendLine("- MEDIUM: call/message drafts, cab/food/grocery search, navigation.")
            appendLine("- HIGH: finalize bookings, destructive actions, sensitive settings.")
            appendLine("- HUMAN_ONLY: payments, OTPs, logins, captchas, bank transfers.")
            appendLine()
            appendLine("Allowed capabilities:")
            context.allowedCapabilities.forEach { capability ->
                appendLine("- $capability")
            }
            appendLine()
            appendLine("Safe context:")
            appendLine("- userCommand: ${context.userCommand}")
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
        }.trimIndent()
    }
}
