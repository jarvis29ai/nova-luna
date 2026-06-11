package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.util.AssistantTextNormalizer

class CommandUnderstandingService(
    private val ruleParser: RuleBasedCommandUnderstandingParser = RuleBasedCommandUnderstandingParser(),
    private val modelParser: BrainActionParser = BrainActionParser()
) {
    fun understand(
        rawCommand: String,
        modelOutput: String? = null
    ): BrainAction {
        val normalized = AssistantTextNormalizer.normalize(rawCommand)
        
        if (modelOutput != null) {
            val modelAction = modelParser.parse(rawCommand, normalized, modelOutput)
            if (modelAction.source != BrainActionSource.ERROR) {
                return modelAction
            }
        }

        // Rule-based fallback if model fails or isn't provided
        return ruleParser.parse(rawCommand)
    }

    fun generateModelPrompt(): String {
        return """
            You are Nova/Luna command understanding brain.
            Convert the user command into strict JSON matching this schema:
            {
              "intent": "...",
              "actionType": "...",
              "riskLevel": "...",
              "requiresConfirmation": true/false,
              "params": {},
              "confidence": 0.0,
              "language": "...",
              "assistantReply": "...",
              "reason": "..."
            }

            Allowed actionType:
            OPEN_APP, OPEN_CAMERA, OPEN_SETTINGS, TOGGLE_FLASHLIGHT, SET_DEVICE_SETTING, SEARCH_WEB, SEARCH_YOUTUBE, PLAY_MEDIA, MAKE_CALL_DRAFT, SEND_MESSAGE_DRAFT, CREATE_CONTENT, ASK_QUESTION, CAB_SEARCH, FOOD_SEARCH, GROCERY_SEARCH, BOOKING_REQUEST, PAYMENT_REQUEST, OTP_REQUEST, LOGIN_REQUEST, CAPTCHA_REQUEST, DESTRUCTIVE_REQUEST, PRIVACY_SENSITIVE_REQUEST, ASK_CLARIFICATION, UNSUPPORTED, UNKNOWN

            Allowed riskLevel:
            LOW, MEDIUM, HIGH, HUMAN_ONLY, UNKNOWN

            Rules:
            1. Do not execute commands.
            2. Do not claim completion.
            3. Classify risk honestly (e.g., PAYMENT is HUMAN_ONLY).
            4. If command is unsafe/secure, mark HUMAN_ONLY.
            5. If unclear, ask clarification.
            6. Return valid JSON only. No markdown. No prose.
        """.trimIndent()
    }
}
