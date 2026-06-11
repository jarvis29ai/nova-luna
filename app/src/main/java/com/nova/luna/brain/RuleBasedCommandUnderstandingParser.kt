package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.util.AssistantTextNormalizer
import java.util.Locale

class RuleBasedCommandUnderstandingParser {
    fun parse(rawText: String): BrainAction {
        val normalized = AssistantTextNormalizer.normalize(rawText)
        val language = detectLanguage(rawText)
        
        if (normalized.isBlank()) {
            return clarifyAction(rawText, normalized, language)
        }

        return when {
            isStopCommand(normalized) -> controlAction(rawText, normalized, "stop", language)
            
            // 1. Specific Domain keywords (check before general search)
            normalized.contains("cab") || normalized.contains("ola") || normalized.contains("uber") ->
                cabAction(rawText, normalized, language)

            normalized.contains("food") || normalized.contains("order") || normalized.contains("pizza") || normalized.contains("biryani") ->
                foodAction(rawText, normalized, language)

            normalized.contains("grocery") || normalized.contains("milk") ->
                groceryAction(rawText, normalized, language)

            // 2. Multimedia & Search
            normalized.contains("youtube") || normalized.contains(" par search karo") ->
                youtubeSearchAction(rawText, normalized, language)

            normalized.startsWith("play ") ->
                playMediaAction(rawText, normalized, language)

            normalized.startsWith("search ") || normalized.startsWith("google ") || normalized.contains("search for") ->
                webSearchAction(rawText, normalized, language)

            // 3. Communication
            isCallCommand(normalized) ->
                callAction(rawText, normalized, language)

            isMessageCommand(normalized) ->
                messageAction(rawText, normalized, language)

            // 4. Device Controls (including Devanagari)
            normalized == "open camera" || normalized == "camera kholo" || normalized == "कैमरा खोलो" -> 
                simpleAction(rawText, normalized, "open_camera", BrainActionType.OPEN_CAMERA, BrainRiskLevel.LOW, language, "I understood: open the camera.")
            
            normalized == "open settings" || normalized == "setting kholo" || normalized == "settings" || normalized == "सेटिंग खोलो" ->
                simpleAction(rawText, normalized, "open_settings", BrainActionType.OPEN_SETTINGS, BrainRiskLevel.LOW, language, "I understood: open the settings.")

            isFlashlightCommand(normalized) ->
                simpleAction(rawText, normalized, "toggle_flashlight", BrainActionType.TOGGLE_FLASHLIGHT, BrainRiskLevel.LOW, language, "I understood: toggle the flashlight.")

            // 5. Apps
            normalized.startsWith("open ") || normalized.startsWith("launch ") ->
                openAppAction(rawText, normalized, language)

            // 6. Sensitive
            isPaymentCommand(normalized) ->
                paymentAction(rawText, normalized, language)

            isOtpCommand(normalized) ->
                otpAction(rawText, normalized, language)

            isLoginCommand(normalized) ->
                loginAction(rawText, normalized, language)

            isCaptchaCommand(normalized) ->
                captchaAction(rawText, normalized, language)

            isDestructiveCommand(normalized) ->
                destructiveAction(rawText, normalized, language)

            else -> clarifyAction(rawText, normalized, language)
        }
    }

    private fun simpleAction(
        raw: String, 
        normalized: String, 
        intent: String, 
        type: BrainActionType, 
        risk: BrainRiskLevel, 
        lang: String,
        reply: String,
        params: Map<String, String> = emptyMap()
    ): BrainAction {
        return BrainAction(
            intent = intent,
            reply = reply,
            actionType = type,
            riskLevel = risk,
            requiresConfirmation = risk != BrainRiskLevel.LOW,
            params = params
        ).withPhase23Metadata(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = raw,
            normalizedCommand = normalized,
            confidence = 0.9,
            language = lang,
            assistantReply = reply,
            reason = "Deterministic rule-based match for ${type.name}."
        )
    }

    private fun controlAction(raw: String, normalized: String, command: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "control_$command", BrainActionType.SET_DEVICE_SETTING, BrainRiskLevel.LOW, lang, "I understood: $command assistant.", mapOf("command" to command))
    }

    private fun openAppAction(raw: String, normalized: String, lang: String): BrainAction {
        val appName = normalized.removePrefix("open ").removePrefix("launch ").removePrefix("app ").trim()
        return simpleAction(raw, normalized, "open_app", BrainActionType.OPEN_APP, BrainRiskLevel.LOW, lang, "I understood: open $appName.", mapOf("appName" to appName))
    }

    private fun youtubeSearchAction(raw: String, normalized: String, lang: String): BrainAction {
        val query = when {
            normalized.contains(" par search karo") -> normalized.substringBefore(" par search karo").trim()
            normalized.startsWith("search youtube for ") -> normalized.removePrefix("search youtube for ").trim()
            else -> normalized.replace("search", "").replace("youtube", "").replace("for", "").trim()
        }
        return simpleAction(raw, normalized, "search_youtube", BrainActionType.SEARCH_YOUTUBE, BrainRiskLevel.LOW, lang, "I understood: search YouTube for $query.", mapOf("query" to query, "provider" to "youtube"))
    }

    private fun webSearchAction(raw: String, normalized: String, lang: String): BrainAction {
        val query = normalized.removePrefix("search ").removePrefix("google ").removePrefix("web ").removePrefix("for ").trim()
        return simpleAction(raw, normalized, "search_web", BrainActionType.SEARCH_WEB, BrainRiskLevel.LOW, lang, "I understood: search for $query.", mapOf("query" to query))
    }

    private fun playMediaAction(raw: String, normalized: String, lang: String): BrainAction {
        val media = normalized.removePrefix("play ").trim()
        return simpleAction(raw, normalized, "play_music", BrainActionType.PLAY_MEDIA, BrainRiskLevel.LOW, lang, "I understood: play $media.", mapOf("query" to media))
    }

    private fun callAction(raw: String, normalized: String, lang: String): BrainAction {
        val contact = normalized.removePrefix("call ").trim()
        return simpleAction(raw, normalized, "call_contact", BrainActionType.MAKE_CALL_DRAFT, BrainRiskLevel.MEDIUM, lang, "I can prepare a call to $contact, but confirmation is required.", mapOf("contact" to contact))
    }

    private fun messageAction(raw: String, normalized: String, lang: String): BrainAction {
        val contact: String
        val message: String
        
        when {
            normalized.contains(" saying ") -> {
                val parts = normalized.split(" saying ")
                contact = parts[0].removePrefix("message ").removePrefix("send ").trim()
                message = parts[1].trim()
            }
            normalized.contains(" ko message bhejo ki ") -> {
                val parts = normalized.split(" ko message bhejo ki ")
                contact = parts[0].trim()
                message = parts[1].trim()
            }
            normalized.startsWith("send message to ") -> {
                val rest = normalized.removePrefix("send message to ").trim()
                val parts = rest.split(" ")
                contact = parts.getOrNull(0) ?: "someone"
                message = if (parts.size > 1) parts.drop(1).joinToString(" ") else rest
            }
            else -> {
                contact = "someone"
                message = normalized
            }
        }
        
        return simpleAction(raw, normalized, "draft_message", BrainActionType.SEND_MESSAGE_DRAFT, BrainRiskLevel.MEDIUM, lang, "I can prepare this message for $contact, but confirmation is required.", mapOf("contact" to contact, "message" to message))
    }

    private fun cabAction(raw: String, normalized: String, lang: String): BrainAction {
        val destination = when {
            normalized.contains(" to ") -> normalized.substringAfterLast(" to ").trim()
            else -> "destination"
        }
        return simpleAction(raw, normalized, "cab_fare_check", BrainActionType.CAB_SEARCH, BrainRiskLevel.MEDIUM, lang, "I understood: check cab fare to $destination.", mapOf("destination" to destination))
    }

    private fun foodAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "food_search", BrainActionType.FOOD_SEARCH, BrainRiskLevel.MEDIUM, lang, "I can help search for food locally.")
    }

    private fun groceryAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "grocery_search", BrainActionType.GROCERY_SEARCH, BrainRiskLevel.MEDIUM, lang, "I can help search for groceries locally.")
    }

    private fun paymentAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "payment", BrainActionType.PAYMENT_REQUEST, BrainRiskLevel.HUMAN_ONLY, lang, "This is a payment request and must be handled by the user manually.")
    }

    private fun otpAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "otp", BrainActionType.OTP_REQUEST, BrainRiskLevel.HUMAN_ONLY, lang, "This is an OTP request and must be handled by the user manually for security.")
    }

    private fun loginAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "login", BrainActionType.LOGIN_REQUEST, BrainRiskLevel.HUMAN_ONLY, lang, "This is a login request and must be handled by the user manually.")
    }

    private fun captchaAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "solve_captcha", BrainActionType.CAPTCHA_REQUEST, BrainRiskLevel.HUMAN_ONLY, lang, "This is a CAPTCHA and must be solved by the user manually.")
    }

    private fun destructiveAction(raw: String, normalized: String, lang: String): BrainAction {
        return simpleAction(raw, normalized, "destructive", BrainActionType.DESTRUCTIVE_REQUEST, BrainRiskLevel.HIGH, lang, "I cannot perform destructive actions automatically. Please confirm or handle manually.")
    }

    private fun clarifyAction(raw: String, normalized: String, lang: String): BrainAction {
        val reply = "What would you like me to do?"
        return BrainAction(
            intent = "clarify",
            reply = reply,
            actionType = BrainActionType.ASK_CLARIFICATION,
            riskLevel = BrainRiskLevel.UNKNOWN,
            requiresConfirmation = false
        ).withPhase23Metadata(
            source = BrainActionSource.RULE_FALLBACK,
            rawCommand = raw,
            normalizedCommand = normalized,
            confidence = 0.5,
            language = lang,
            assistantReply = reply,
            reason = "Command is ambiguous or empty."
        )
    }

    private fun isStopCommand(normalized: String): Boolean = normalized in listOf("stop", "cancel", "quiet", "be quiet")
    
    private fun isFlashlightCommand(normalized: String): Boolean = normalized.contains("flashlight") || normalized.contains("torch")
    
    private fun isCallCommand(normalized: String): Boolean = normalized.startsWith("call ") || normalized.contains("call ")

    private fun isMessageCommand(normalized: String): Boolean = normalized.contains("message") || normalized.contains(" bhejo") || normalized.contains(" kahon")

    private fun isPaymentCommand(normalized: String): Boolean = listOf("pay", "send money", "upi", "transfer", " rupay").any { normalized.contains(it) }
    
    private fun isOtpCommand(normalized: String): Boolean = normalized.contains("otp") || normalized.contains("one time password")
    
    private fun isLoginCommand(normalized: String): Boolean = normalized.contains("login") || normalized.contains("password")
    
    private fun isCaptchaCommand(normalized: String): Boolean = normalized.contains("captcha")
    
    private fun isDestructiveCommand(normalized: String): Boolean = normalized.contains("delete") || normalized.contains("erase") || normalized.contains("remove all")

    private fun detectLanguage(raw: String): String {
        return if (raw.any { it.code in 0x0900..0x097F }) "hi"
        else if (listOf("karo", "kholo", "bhejo", "hai", "hoon").any { raw.lowercase().contains(it) }) "hinglish"
        else "en"
    }
}
