package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class SystemHandler : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.PHONE_CONTROL
    override val modelName: String = "SystemParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val n = command.lowercase().trim()
        val signals = mutableListOf<String>()
        
        val isNav = isHomeCommand(n) || isBackCommand(n) || isRecentsCommand(n) || isOpenNotificationsCommand(n)
        val isScroll = isScrollDownCommand(n) || isScrollUpCommand(n)
        val isInteraction = isTapCommand(n) || isTypeTextCommand(n)
        val isControl = isStopCommand(n)
        val isSensitive = n == "take screenshot" || n.contains("settings") || n.startsWith("call ")
        val isAppLaunch = n.startsWith("open app ") || n.startsWith("open ") || n.startsWith("launch ") || n.startsWith("start ")

        if (isNav) signals.add("navigation")
        if (isScroll) signals.add("scroll")
        if (isInteraction) signals.add("interaction")
        if (isControl) signals.add("control")
        if (isSensitive) signals.add("sensitive")
        if (isAppLaunch) signals.add("app_launch")

        val confidence = if (signals.isNotEmpty()) 0.95f else 0.0f
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = if (signals.isNotEmpty()) "Matched system interaction pattern" else "No system match"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        val n = command.lowercase().trim()
        
        return when {
            isStopCommand(n) -> CommandIntent(
                rawText = command,
                intentType = IntentType.CONTROL,
                actionType = ActionType.STOP_SERVICE,
                entities = mapOf("command" to "stop")
            )
            isHomeCommand(n) -> nav(command, ActionType.GO_HOME, "go_home")
            isBackCommand(n) -> nav(command, ActionType.GO_BACK, "go_back")
            isRecentsCommand(n) -> nav(command, ActionType.OPEN_RECENTS, "open_recents")
            isReadNotificationsCommand(n) -> CommandIntent(
                rawText = command,
                intentType = IntentType.READ_NOTIFICATIONS,
                actionType = ActionType.READ_NOTIFICATIONS,
                entities = mapOf("command" to "read_notifications")
            )
            isOpenNotificationsCommand(n) -> nav(command, ActionType.OPEN_NOTIFICATIONS, "open_notifications")
            isScrollDownCommand(n) -> nav(command, ActionType.SCROLL_FORWARD, "scroll_down")
            isScrollUpCommand(n) -> nav(command, ActionType.SCROLL_BACKWARD, "scroll_up")
            isTapCommand(n) -> interaction(command, ActionType.CLICK_TEXT, extractTapTarget(n))
            isTypeTextCommand(n) -> textEntry(command, extractTypeText(n))
            n == "take screenshot" -> sensitive(command, ActionType.TAKE_SCREENSHOT, "take_screenshot")
            n.contains("settings") -> {
                when {
                    n.contains("accessibility") -> sensitive(command, ActionType.OPEN_ACCESSIBILITY_SETTINGS, "accessibility")
                    n.contains("usage") -> sensitive(command, ActionType.OPEN_USAGE_ACCESS_SETTINGS, "usage")
                    else -> sensitive(command, ActionType.OPEN_SETTINGS, "settings")
                }
            }
            n.startsWith("open app ") || n.startsWith("open ") || n.startsWith("launch ") || n.startsWith("start ") -> 
                openApp(command, extractAppName(n))
            else -> CommandIntent(rawText = command)
        }
    }

    private fun nav(rawText: String, actionType: ActionType, command: String) = CommandIntent(
        rawText = rawText,
        intentType = IntentType.NAVIGATION,
        actionType = actionType,
        entities = mapOf("command" to command)
    )

    private fun interaction(rawText: String, actionType: ActionType, target: String) = CommandIntent(
        rawText = rawText,
        intentType = IntentType.INTERACTION,
        actionType = actionType,
        entities = mapOf("text" to target)
    )

    private fun textEntry(rawText: String, text: String) = CommandIntent(
        rawText = rawText,
        intentType = IntentType.TEXT_ENTRY,
        actionType = ActionType.TYPE_TEXT,
        entities = mapOf("text" to text)
    )

    private fun openApp(rawText: String, appName: String) = CommandIntent(
        rawText = rawText,
        intentType = IntentType.OPEN_APP,
        actionType = ActionType.LAUNCH_APP,
        entities = mapOf("appName" to appName, "query" to appName)
    )

    private fun sensitive(rawText: String, actionType: ActionType, value: String) = CommandIntent(
        rawText = rawText,
        intentType = IntentType.SENSITIVE,
        actionType = actionType,
        entities = mapOf("value" to value)
    )

    private fun isStopCommand(n: String) = n in setOf("stop listening", "stop service", "cancel listening", "stop voice", "stop speaking")
    private fun isHomeCommand(n: String) = n in setOf("go home", "home", "back to home", "show home", "open home")
    private fun isBackCommand(n: String) = n in setOf("go back", "back", "previous", "go previous", "move back")
    private fun isRecentsCommand(n: String) = n in setOf("show recent apps", "show recents", "open recents", "recent apps", "recents", "recent")
    private fun isReadNotificationsCommand(n: String) = n in setOf("read notifications", "check notifications", "what are my notifications")
    private fun isOpenNotificationsCommand(n: String) = n in setOf("open notifications", "show notifications", "notifications")
    private fun isScrollDownCommand(n: String) = n.contains("scroll down") || n.contains("swipe down") || n.contains("move down")
    private fun isScrollUpCommand(n: String) = n.contains("scroll up") || n.contains("swipe up") || n.contains("move up")
    private fun isTapCommand(n: String) = n.startsWith("tap ") || n.startsWith("click ") || n.startsWith("press ")
    private fun isTypeTextCommand(n: String) = n.startsWith("type ") || n.startsWith("write ") || n.startsWith("input ")

    private fun extractTapTarget(n: String) = n.removePrefix("tap on ").removePrefix("tap ").removePrefix("click on ").removePrefix("click ").removePrefix("press on ").removePrefix("press ").trim()
    private fun extractTypeText(n: String) = n.removePrefix("type ").removePrefix("write ").removePrefix("input ").trim()
    private fun extractAppName(n: String) = n.removePrefix("open app ").removePrefix("open ").removePrefix("launch ").removePrefix("start ").trim()
}
