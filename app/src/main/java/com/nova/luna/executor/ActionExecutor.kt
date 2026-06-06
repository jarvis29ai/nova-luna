package com.nova.luna.executor

import android.content.Context
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.cab.CabBookingOrchestrator
import com.nova.luna.cab.CabDeepLinkBuilder
import com.nova.luna.cab.CabProviderRegistry
import com.nova.luna.cab.toCabBookingRequest
import com.nova.luna.cab.toCabCommandResult
import com.nova.luna.grocery.GroceryAccessibilityService
import com.nova.luna.grocery.GroceryBookingOrchestrator
import com.nova.luna.grocery.GroceryDeepLinkBuilder
import com.nova.luna.grocery.GroceryProviderLauncher
import com.nova.luna.grocery.GroceryProviderRegistry
import com.nova.luna.grocery.toGroceryBookingRequest
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
import com.nova.luna.content.ContentCreationOrchestrator
import com.nova.luna.shopping.ShoppingOrchestrator
import com.nova.luna.shopping.ShoppingStatus
import com.nova.luna.util.AccessibilityReadiness

class ActionExecutor(context: Context) : ActionExecutorGateway {
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
    private val contentCreationOrchestrator = ContentCreationOrchestrator(context.applicationContext)
    private val mediaOrchestrator = MediaOrchestrator(context.applicationContext)
    private val shoppingOrchestrator = ShoppingOrchestrator(context.applicationContext)
    private val cabProviderRegistry = CabProviderRegistry(context.applicationContext.packageManager)
    private val foodProviderRegistry = FoodProviderRegistry(context.applicationContext.packageManager)
    private val groceryProviderRegistry = GroceryProviderRegistry(context.applicationContext.packageManager)

    private val cabOrchestrator = CabBookingOrchestrator(
        providerRegistry = cabProviderRegistry,
        deepLinkBuilder = CabDeepLinkBuilder(context.applicationContext, cabProviderRegistry),
        pickupLocationResolver = com.nova.luna.cab.AndroidCabLocationResolver(context.applicationContext),
        providerLauncher = { intent ->
            runCatching {
                context.applicationContext.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    )

    private val groceryOrchestrator = GroceryBookingOrchestrator(
        providerRegistry = groceryProviderRegistry,
        deepLinkBuilder = GroceryDeepLinkBuilder(context.applicationContext, groceryProviderRegistry),
        accessibilityService = GroceryAccessibilityService(),
        providerLauncher = GroceryProviderLauncher { intent ->
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

    override fun execute(commandIntent: CommandIntent): CommandResult {
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
            ActionType.CAB_BOOKING -> cabOrchestrator.start(commandIntent.toCabBookingRequest()).toCabCommandResult()
            ActionType.FOOD_ORDER -> foodOrchestrator.start(commandIntent.toFoodBookingRequest()).toFoodCommandResult()
            ActionType.GROCERY_BOOKING -> groceryOrchestrator.start(commandIntent.toGroceryBookingRequest()).toGroceryCommandResult()
            ActionType.CONTENT_CREATION -> handleContentCreationText(commandIntent.rawText, commandIntent)
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
            ActionType.SHOPPING -> handleShoppingText(commandIntent.rawText, commandIntent)
            ActionType.STOP_SERVICE -> CommandResult.success(
                message = "Stopping listening.",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities,
                shouldStopListening = true
            )
            ActionType.BLOCKED,
            ActionType.UNKNOWN,
            ActionType.MEDIA_CONTROL -> CommandResult.failure(
                "I could not map that command to a safe action.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
        }
    }

    override fun hasActiveCabBookingSession(): Boolean = cabOrchestrator.isActive()
    override fun cancelCabBookingSession(): CommandResult = CommandResult.success("Cancelled")
    override fun handleCabBookingText(rawText: String): CommandResult = cabOrchestrator.handleUserInput(rawText).toCabCommandResult()

    override fun hasActiveFoodBookingSession(): Boolean = foodOrchestrator.isActive()
    override fun cancelFoodBookingSession(): CommandResult = CommandResult.success("Cancelled")
    override fun handleFoodBookingText(rawText: String): CommandResult = foodOrchestrator.handleUserInput(rawText).toFoodCommandResult()

    override fun hasActiveGroceryBookingSession(): Boolean = groceryOrchestrator.isActive()
    override fun cancelGroceryBookingSession(): CommandResult = groceryOrchestrator.cancelSession().toGroceryCommandResult()
    override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult = groceryOrchestrator.handleUserInput(rawText, userConfirmed).toGroceryCommandResult()

    override fun hasActivePhoneContactSession(): Boolean = phoneOrchestrator.isActive()
    override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = phoneOrchestrator.handleUserInput(rawText).toPhoneCommandResult(commandIntent)

    override fun hasActiveCommunicationSession(): Boolean = communicationOrchestrator.isActive()
    override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult {
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

    override fun hasActiveContentCreationSession(): Boolean = contentCreationOrchestrator.isActive()
    override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult {
        val result = contentCreationOrchestrator.handleRequest(rawText)
        return CommandResult(
            success = result.status == com.nova.luna.content.ContentCreationStatus.SUCCESS ||
                      result.status == com.nova.luna.content.ContentCreationStatus.NEEDS_USER_INPUT ||
                      result.status == com.nova.luna.content.ContentCreationStatus.NEEDS_CONFIRMATION ||
                      result.status == com.nova.luna.content.ContentCreationStatus.MANUAL_ACTION_REQUIRED,
            message = result.popupText,
            intentType = commandIntent.intentType,
            actionType = commandIntent.actionType,
            entities = commandIntent.entities + mapOf("voiceText" to result.voiceText)
        )
    }

    override fun hasActiveMediaSession(): Boolean = mediaOrchestrator.isActive()
    override fun handleMediaText(rawText: String, commandIntent: CommandIntent): CommandResult {
        val result = mediaOrchestrator.handleRequest(rawText)
        return CommandResult(
            success = result.status == com.nova.luna.media.MediaStatusType.SUCCESS ||
                      result.status == com.nova.luna.media.MediaStatusType.NEEDS_USER_INPUT ||
                      result.status == com.nova.luna.media.MediaStatusType.NEEDS_CONFIRMATION ||
                      result.status == com.nova.luna.media.MediaStatusType.MANUAL_ACTION_REQUIRED,
            message = result.popupText,
            intentType = commandIntent.intentType,
            actionType = commandIntent.actionType,
            entities = commandIntent.entities + mapOf("voiceText" to result.voiceText)
        )
    }

    override fun hasActiveShoppingSession(): Boolean = shoppingOrchestrator.isActive()
    override fun handleShoppingText(rawText: String, commandIntent: CommandIntent): CommandResult {
        val result = shoppingOrchestrator.handleRequest(rawText)
        return CommandResult(
            success = result.status != ShoppingStatus.FAILED && result.status != ShoppingStatus.CANCELLED,
            message = result.popupText,
            intentType = commandIntent.intentType,
            actionType = ActionType.SHOPPING,
            entities = commandIntent.entities + mapOf("voiceText" to result.voiceText)
        )
    }
}
