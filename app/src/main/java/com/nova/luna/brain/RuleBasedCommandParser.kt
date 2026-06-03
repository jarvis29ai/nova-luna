package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import java.util.Locale

class RuleBasedCommandParser {
    private val blockedKeywords = listOf(
        "send money",
        "pay",
        "order",
        "buy",
        "checkout",
        "bank",
        "upi",
        "password",
        "otp",
        "captcha"
    )

    fun parse(rawText: String): CommandIntent {
        val raw = rawText.trim()
        val normalized = raw.lowercase(Locale.US)
        if (raw.isBlank()) {
            return CommandIntent(rawText = rawText)
        }

        if (isStopCommand(normalized)) {
            return CommandIntent(
                rawText = rawText,
                intentType = IntentType.CONTROL,
                actionType = ActionType.STOP_SERVICE,
                entities = mapOf("command" to "stop")
            )
        }

        if (blockedKeywords.any { normalized.contains(it) }) {
            return CommandIntent(
                rawText = rawText,
                intentType = IntentType.BLOCKED,
                actionType = ActionType.BLOCKED,
                entities = mapOf("reason" to "blocked_keyword")
            )
        }

        return when {
            normalized == "go home" -> nav(rawText, ActionType.GO_HOME, "go_home")
            normalized == "go back" -> nav(rawText, ActionType.GO_BACK, "go_back")
            normalized == "show recent apps" ||
                normalized == "show recents" ||
                normalized == "open recent apps" ||
                normalized == "recent apps" -> nav(rawText, ActionType.OPEN_RECENTS, "open_recents")

            normalized == "open notifications" -> nav(rawText, ActionType.OPEN_NOTIFICATIONS, "open_notifications")
            normalized == "scroll down" -> nav(rawText, ActionType.SCROLL_FORWARD, "scroll_down")
            normalized == "scroll up" -> nav(rawText, ActionType.SCROLL_BACKWARD, "scroll_up")
            normalized.startsWith("tap ") -> interaction(rawText, ActionType.CLICK_TEXT, normalized.removePrefix("tap ").trim())
            normalized.startsWith("click ") -> interaction(rawText, ActionType.CLICK_TEXT, normalized.removePrefix("click ").trim())
            normalized.startsWith("type ") -> textEntry(rawText, normalized.removePrefix("type ").trim())
            normalized == "read notifications" -> reading(rawText, ActionType.READ_NOTIFICATIONS, "read_notifications")
            normalized == "take screenshot" -> sensitive(rawText, ActionType.TAKE_SCREENSHOT, "take_screenshot")
            normalized == "open settings" -> sensitive(rawText, ActionType.OPEN_SETTINGS, "open_settings")
            normalized == "open accessibility settings" -> sensitive(rawText, ActionType.OPEN_ACCESSIBILITY_SETTINGS, "open_accessibility_settings")
            normalized == "open usage access settings" -> sensitive(rawText, ActionType.OPEN_USAGE_ACCESS_SETTINGS, "open_usage_access_settings")
            normalized.startsWith("call ") -> sensitive(rawText, ActionType.CALL_CONTACT, normalized.removePrefix("call ").trim())
            normalized.startsWith("open ") -> openApp(rawText, normalized.removePrefix("open ").trim())
            normalized.startsWith("launch ") -> openApp(rawText, normalized.removePrefix("launch ").trim())
            normalized.startsWith("start ") -> openApp(rawText, normalized.removePrefix("start ").trim())
            else -> CommandIntent(
                rawText = rawText,
                intentType = IntentType.UNKNOWN,
                actionType = ActionType.UNKNOWN
            )
        }
    }

    private fun nav(rawText: String, actionType: ActionType, command: String): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = IntentType.NAVIGATION,
            actionType = actionType,
            entities = mapOf("command" to command)
        )
    }

    private fun interaction(rawText: String, actionType: ActionType, target: String): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = IntentType.INTERACTION,
            actionType = actionType,
            entities = mapOf("text" to target)
        )
    }

    private fun textEntry(rawText: String, text: String): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = IntentType.TEXT_ENTRY,
            actionType = ActionType.TYPE_TEXT,
            entities = mapOf("text" to text)
        )
    }

    private fun reading(rawText: String, actionType: ActionType, command: String): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = IntentType.READ_NOTIFICATIONS,
            actionType = actionType,
            entities = mapOf("command" to command)
        )
    }

    private fun openApp(rawText: String, appName: String): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = IntentType.OPEN_APP,
            actionType = ActionType.LAUNCH_APP,
            entities = mapOf("appName" to appName, "query" to appName)
        )
    }

    private fun sensitive(rawText: String, actionType: ActionType, value: String): CommandIntent {
        return CommandIntent(
            rawText = rawText,
            intentType = IntentType.SENSITIVE,
            actionType = actionType,
            entities = mapOf("value" to value)
        )
    }

    private fun isStopCommand(normalized: String): Boolean {
        return normalized == "stop" ||
            normalized == "cancel" ||
            normalized == "stop listening" ||
            normalized == "cancel listening" ||
            normalized == "stop voice" ||
            normalized == "cancel voice"
    }
}

