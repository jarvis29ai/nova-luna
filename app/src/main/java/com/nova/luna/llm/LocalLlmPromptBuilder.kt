package com.nova.luna.llm

import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType

class LocalLlmPromptBuilder {
    fun build(request: LocalLlmRequest): String {
        return buildString {
            appendLine("You are Luna/Nova's local assistant brain.")
            appendLine("You are a helpful on-device AI.")
            appendLine("Your goal is to understand the user's command and suggest a candidate action in strict JSON format.")
            appendLine("You DO NOT control the phone directly. Your output will be validated and safety-gated before execution.")
            appendLine("Language: Preserve the user's language (English, Hindi, or Hinglish).")
            appendLine()
            appendLine("CRITICAL RULES:")
            appendLine("1. Output ONLY strict JSON. No prose, no markdown, no explanation.")
            appendLine("2. Use ONLY the following action types: ${ActionType.entries.joinToString { it.name }}")
            appendLine("3. Risky actions (payment, order, book, send, delete) MUST have 'requiresConfirmation': true.")
            appendLine("4. Never output direct Android code or system intents.")
            appendLine("5. If the request is unsafe or restricted (OTP, PIN, CVV), return 'domain': 'UNKNOWN' and 'userMessage': 'I cannot do that for safety reasons.'")
            appendLine()
            appendLine("JSON SCHEMA:")
            appendLine("{")
            appendLine("  \"type\": \"candidate_action\",")
            appendLine("  \"domain\": \"MEDIA|MUSIC|FOOD|GROCERY|CAB|SHOPPING|COMMUNICATION|CONTENT|PHONE_CONTROL|SYSTEM_NAVIGATION|UNKNOWN\",")
            appendLine("  \"intent\": \"short_intent_name\",")
            appendLine("  \"confidence\": 0.0 to 1.0,")
            appendLine("  \"requiresConfirmation\": true|false,")
            appendLine("  \"riskLevel\": \"SAFE|LOW|MEDIUM|HIGH|CRITICAL\",")
            appendLine("  \"action\": {")
            appendLine("    \"actionType\": \"OPEN_APP|TAP_TEXT|TYPE_TEXT|SCROLL_DOWN|SCROLL_UP|GO_BACK|GO_HOME|READ_SCREEN|WAIT_FOR_TEXT|WAIT_FOR_APP|NO_OP|ASK_CLARIFICATION\",")
            appendLine("    \"targetApp\": \"string or null\",")
            appendLine("    \"targetText\": \"string or null\",")
            appendLine("    \"inputText\": \"string or null\",")
            appendLine("    \"direction\": \"UP|DOWN or null\"")
            appendLine("  },")
            appendLine("  \"clarificationQuestion\": \"string or null\",")
            appendLine("  \"userMessage\": \"message for user\",")
            appendLine("  \"safetyNotes\": \"internal safety notes\"")
            appendLine("}")
            appendLine()
            appendLine("CONTEXT:")
            appendLine("- User command: ${request.commandText}")
            appendLine("- Normalized: ${request.normalizedCommand}")
            appendLine("- Current Domain: ${request.currentDomainGuess}")
            request.screenSnapshotSummary?.let { appendLine("- Screen Snapshot: $it") }
            request.assistantContext?.let { ctx ->
                appendLine("- Active Domain: ${ctx.activeDomain}")
                appendLine("- Last Command: ${ctx.lastCommand}")
                if (!ctx.memoryContext.isNullOrBlank()) {
                    appendLine("- User Preferences: ${ctx.memoryContext}")
                }
            }
            appendLine()
            appendLine("Suggest the best candidate action now.")
        }.trimIndent()
    }
}
