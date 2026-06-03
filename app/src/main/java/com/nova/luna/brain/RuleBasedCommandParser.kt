package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.cab.CabIntentParser
import com.nova.luna.cab.toEntities
import java.util.Locale

class RuleBasedCommandParser {
    private val cabIntentParser = CabIntentParser()
    private val blockedKeywords = listOf(
        "send money",
        "pay",
        "payment",
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

        cabIntentParser.parse(rawText)?.let { cabRequest ->
            return CommandIntent(
                rawText = rawText,
                intentType = IntentType.CAB_BOOKING,
                actionType = ActionType.CAB_BOOKING,
                entities = cabRequest.toEntities()
            )
        }

        if (containsBlockedKeyword(normalized)) {
            return CommandIntent(
                rawText = rawText,
                intentType = IntentType.BLOCKED,
                actionType = ActionType.BLOCKED,
                entities = mapOf("reason" to "blocked_keyword")
            )
        }

        return when {
            isHomeCommand(normalized) -> nav(rawText, ActionType.GO_HOME, "go_home")
            isBackCommand(normalized) -> nav(rawText, ActionType.GO_BACK, "go_back")
            isRecentsCommand(normalized) -> nav(rawText, ActionType.OPEN_RECENTS, "open_recents")

            isOpenNotificationsCommand(normalized) -> nav(rawText, ActionType.OPEN_NOTIFICATIONS, "open_notifications")
            isScrollDownCommand(normalized) -> nav(rawText, ActionType.SCROLL_FORWARD, "scroll_down")
            isScrollUpCommand(normalized) -> nav(rawText, ActionType.SCROLL_BACKWARD, "scroll_up")
            isTapCommand(normalized) -> interaction(rawText, ActionType.CLICK_TEXT, extractTapTarget(normalized))
            isTypeTextCommand(normalized) -> textEntry(rawText, extractTypeText(normalized))
            isReadNotificationsCommand(normalized) -> reading(rawText, ActionType.READ_NOTIFICATIONS, "read_notifications")
            normalized == "take screenshot" -> sensitive(rawText, ActionType.TAKE_SCREENSHOT, "take_screenshot")
            isOpenSettingsCommand(normalized) -> sensitive(rawText, ActionType.OPEN_SETTINGS, "open_settings")
            isOpenAccessibilitySettingsCommand(normalized) -> sensitive(rawText, ActionType.OPEN_ACCESSIBILITY_SETTINGS, "open_accessibility_settings")
            isOpenUsageAccessSettingsCommand(normalized) -> sensitive(rawText, ActionType.OPEN_USAGE_ACCESS_SETTINGS, "open_usage_access_settings")
            normalized.startsWith("call ") -> sensitive(rawText, ActionType.CALL_CONTACT, normalized.removePrefix("call ").trim())
            normalized.startsWith("open app ") -> openApp(rawText, normalized.removePrefix("open app ").trim())
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

    private fun isHomeCommand(normalized: String): Boolean {
        return normalized == "go home" ||
            normalized == "home" ||
            normalized == "back to home"
    }

    private fun isBackCommand(normalized: String): Boolean {
        return normalized == "go back" ||
            normalized == "back" ||
            normalized == "previous" ||
            normalized == "go previous"
    }

    private fun isRecentsCommand(normalized: String): Boolean {
        return normalized == "show recent apps" ||
            normalized == "show recents" ||
            normalized == "open recent apps" ||
            normalized == "open recents" ||
            normalized == "recent apps" ||
            normalized == "recents"
    }

    private fun isOpenNotificationsCommand(normalized: String): Boolean {
        return normalized == "open notifications" ||
            normalized == "show notifications"
    }

    private fun isOpenSettingsCommand(normalized: String): Boolean {
        return normalized == "open settings" ||
            normalized == "launch settings" ||
            normalized == "open phone settings"
    }

    private fun isOpenAccessibilitySettingsCommand(normalized: String): Boolean {
        return normalized == "open accessibility settings" ||
            normalized == "open nova accessibility settings"
    }

    private fun isOpenUsageAccessSettingsCommand(normalized: String): Boolean {
        return normalized == "open usage access settings" ||
            normalized == "open usage settings" ||
            normalized == "open app usage settings" ||
            normalized == "open usage permission" ||
            normalized == "open app usage permission"
    }

    private fun isScrollDownCommand(normalized: String): Boolean {
        return normalized == "scroll down" ||
            normalized == "swipe down" ||
            normalized == "move down"
    }

    private fun isScrollUpCommand(normalized: String): Boolean {
        return normalized == "scroll up" ||
            normalized == "swipe up" ||
            normalized == "move up"
    }

    private fun isReadNotificationsCommand(normalized: String): Boolean {
        return normalized == "read notifications" ||
            normalized == "check notifications"
    }

    private fun isTapCommand(normalized: String): Boolean {
        return normalized.startsWith("tap ") ||
            normalized.startsWith("tap on ") ||
            normalized.startsWith("click ") ||
            normalized.startsWith("click on ") ||
            normalized.startsWith("press ") ||
            normalized.startsWith("press on ")
    }

    private fun extractTapTarget(normalized: String): String {
        val prefixes = listOf(
            "tap on ",
            "click on ",
            "press on ",
            "tap ",
            "click ",
            "press "
        )

        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                return normalized.removePrefix(prefix).trim()
            }
        }

        return normalized
    }

    private fun isTypeTextCommand(normalized: String): Boolean {
        return normalized.startsWith("type ") ||
            normalized.startsWith("write ") ||
            normalized.startsWith("enter ") ||
            normalized.startsWith("input ")
    }

    private fun extractTypeText(normalized: String): String {
        val prefixes = listOf(
            "type message ",
            "write message ",
            "enter message ",
            "type ",
            "write ",
            "enter ",
            "input message ",
            "input "
        )

        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                return normalized.removePrefix(prefix).trim()
            }
        }

        return normalized
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
            normalized == "cancel voice" ||
            normalized == "stop speaking" ||
            normalized == "quiet" ||
            normalized == "be quiet"
    }

    private fun containsBlockedKeyword(normalized: String): Boolean {
        return blockedKeywords.any { keyword ->
            Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(normalized)
        }
    }
}
