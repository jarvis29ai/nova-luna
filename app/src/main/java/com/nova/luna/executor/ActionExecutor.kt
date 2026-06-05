package com.nova.luna.executor

import android.content.Context
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.cab.AndroidCabPickupLocationResolver
import com.nova.luna.cab.CabAccessibilityService
import com.nova.luna.cab.CabBookingOrchestrator
import com.nova.luna.cab.CabDeepLinkBuilder
import com.nova.luna.cab.CabProviderRegistry
import com.nova.luna.cab.toCabBookingRequest
import com.nova.luna.cab.toCommandResult
import com.nova.luna.food.FoodAccessibilityService
import com.nova.luna.food.FoodBookingOrchestrator
import com.nova.luna.food.FoodDeepLinkBuilder
import com.nova.luna.food.FoodProviderLauncher
import com.nova.luna.food.FoodProviderRegistry
import com.nova.luna.food.toFoodBookingRequest
import com.nova.luna.food.toCommandResult as toFoodCommandResult
import com.nova.luna.phone.PhoneContactOrchestrator
import com.nova.luna.phone.PhoneContactIntentParser
import com.nova.luna.phone.toCommandResult as toPhoneCommandResult
import com.nova.luna.communication.CommunicationOrchestrator

class ActionExecutor(context: Context) {
    private val appLauncher = AppLauncher(context.applicationContext)
    private val navExecutor = NavExecutor()
    private val tapExecutor = TapExecutor()
    private val scrollExecutor = ScrollExecutor()
    private val typeExecutor = TypeExecutor()
    private val settingsExecutor = SettingsExecutor(context.applicationContext)
    private val notificationReader = NotificationReader(context.applicationContext)
    private val phoneParser = PhoneContactIntentParser()
    private val phoneOrchestrator = PhoneContactOrchestrator(context.applicationContext, phoneParser)
    private val communicationOrchestrator = CommunicationOrchestrator(context.applicationContext)
    private val cabProviderRegistry = CabProviderRegistry(context.applicationContext.packageManager)
    private val foodProviderRegistry = FoodProviderRegistry(context.applicationContext.packageManager)
    private val cabOrchestrator = CabBookingOrchestrator(
        providerRegistry = cabProviderRegistry,
        deepLinkBuilder = CabDeepLinkBuilder(context.applicationContext, cabProviderRegistry),
        accessibilityService = CabAccessibilityService(),
        pickupLocationResolver = AndroidCabPickupLocationResolver(context.applicationContext),
        providerLauncher = { intent ->
            runCatching {
                context.applicationContext.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    )
    private val foodOrchestrator = FoodBookingOrchestrator(
        providerRegistry = foodProviderRegistry,
        deepLinkBuilder = FoodDeepLinkBuilder(context.applicationContext, foodProviderRegistry),
        accessibilityService = FoodAccessibilityService(),
        providerLauncher = FoodProviderLauncher { intent ->
            runCatching {
                context.applicationContext.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    )

    fun execute(commandIntent: CommandIntent): CommandResult {
        return when (commandIntent.actionType) {
            ActionType.LAUNCH_APP -> appLauncher.launchApp(commandIntent)
            ActionType.GO_HOME -> navExecutor.goHome(commandIntent)
            ActionType.GO_BACK -> navExecutor.goBack(commandIntent)
            ActionType.OPEN_RECENTS -> navExecutor.openRecents(commandIntent)
            ActionType.OPEN_NOTIFICATIONS -> navExecutor.openNotifications(commandIntent)
            ActionType.CLICK_TEXT -> tapExecutor.tap(commandIntent)
            ActionType.SCROLL_FORWARD -> scrollExecutor.scrollForward(commandIntent)
            ActionType.SCROLL_BACKWARD -> scrollExecutor.scrollBackward(commandIntent)
            ActionType.TYPE_TEXT -> typeExecutor.typeText(commandIntent)
            ActionType.READ_NOTIFICATIONS -> notificationReader.readNotifications(commandIntent)
            ActionType.TAKE_SCREENSHOT -> CommandResult.failure(
                "Screenshot is scaffolded but not implemented in this starter.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
            ActionType.OPEN_SETTINGS -> settingsExecutor.openSettings(commandIntent)
            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> settingsExecutor.openAccessibilitySettings(commandIntent)
            ActionType.OPEN_USAGE_ACCESS_SETTINGS -> settingsExecutor.openUsageAccessSettings(commandIntent)
            ActionType.CALL_CONTACT -> {
                val phoneRequest = phoneParser.parse(commandIntent.rawText)
                phoneOrchestrator.start(phoneRequest).toPhoneCommandResult(commandIntent)
            }
            ActionType.CAB_BOOKING -> cabOrchestrator.start(commandIntent.toCabBookingRequest()).toCommandResult()
            ActionType.FOOD_ORDER -> foodOrchestrator.start(commandIntent.toFoodBookingRequest()).toFoodCommandResult()
            ActionType.COMMUNICATION -> {
                val result = communicationOrchestrator.handleRequest(commandIntent.rawText)
                CommandResult(
                    success = result.status == com.nova.luna.communication.CommunicationStatus.SUCCESS ||
                              result.status == com.nova.luna.communication.CommunicationStatus.NEEDS_CONFIRMATION,
                    message = result.popupText,
                    intentType = commandIntent.intentType,
                    actionType = commandIntent.actionType,
                    entities = commandIntent.entities + mapOf("voiceText" to result.voiceText)
                )
            }
            ActionType.STOP_SERVICE -> CommandResult.success(
                message = "Stopping listening.",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities,
                shouldStopListening = true
            )
            ActionType.BLOCKED,
            ActionType.UNKNOWN -> CommandResult.failure(
                "I could not map that command to a safe action.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }
    }

    fun hasActiveCabBookingSession(): Boolean {
        return cabOrchestrator.isActive()
    }

    fun hasActiveFoodBookingSession(): Boolean {
        return foodOrchestrator.isActive()
    }

    fun hasActivePhoneContactSession(): Boolean {
        return phoneOrchestrator.isActive()
    }

    fun hasActiveCommunicationSession(): Boolean {
        return communicationOrchestrator.isActive()
    }

    fun handleCabBookingText(rawText: String): CommandResult {
        return cabOrchestrator.handleUserInput(rawText).toCommandResult()
    }

    fun handleFoodBookingText(rawText: String): CommandResult {
        return foodOrchestrator.handleUserInput(rawText).toFoodCommandResult()
    }

    fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult {
        return phoneOrchestrator.handleUserInput(rawText).toPhoneCommandResult(commandIntent)
    }

    fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult {
        val result = communicationOrchestrator.handleRequest(rawText)
        return CommandResult(
            success = result.status == com.nova.luna.communication.CommunicationStatus.SUCCESS ||
                      result.status == com.nova.luna.communication.CommunicationStatus.NEEDS_CONFIRMATION,
            message = result.popupText,
            intentType = commandIntent.intentType,
            actionType = commandIntent.actionType,
            entities = commandIntent.entities + mapOf("voiceText" to result.voiceText)
        )
    }
}
