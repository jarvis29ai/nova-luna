package com.nova.luna.executor

import android.content.Context
import android.content.Intent
import com.nova.luna.cab.AndroidCabLocationResolver
import com.nova.luna.cab.CabAccessibilityService
import com.nova.luna.cab.CabBookingOrchestrator
import com.nova.luna.cab.CabDeepLinkBuilder
import com.nova.luna.cab.CabProviderRegistry
import com.nova.luna.cab.toCabBookingRequest
import com.nova.luna.cab.toCabCommandResult
import com.nova.luna.food.FoodAccessibilityService
import com.nova.luna.food.FoodBookingOrchestrator
import com.nova.luna.food.FoodDeepLinkBuilder
import com.nova.luna.food.FoodProviderLauncher
import com.nova.luna.food.FoodProviderRegistry
import com.nova.luna.food.toCommandResult as toFoodCommandResult
import com.nova.luna.food.toFoodBookingRequest
import com.nova.luna.grocery.GroceryAccessibilityService
import com.nova.luna.grocery.GroceryBookingOrchestrator
import com.nova.luna.grocery.GroceryDeepLinkBuilder
import com.nova.luna.grocery.GroceryProviderRegistry
import com.nova.luna.grocery.toGroceryBookingRequest
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult

class ActionExecutor(context: Context) : ActionExecutorGateway {
    private val appLauncher = AppLauncher(context.applicationContext)
    private val navExecutor = NavExecutor()
    private val tapExecutor = TapExecutor()
    private val scrollExecutor = ScrollExecutor()
    private val typeExecutor = TypeExecutor()
    private val settingsExecutor = SettingsExecutor(context.applicationContext)
    private val notificationReader = NotificationReader(context.applicationContext)
    private val cabProviderRegistry = CabProviderRegistry(context.applicationContext.packageManager)
    private val foodProviderRegistry = FoodProviderRegistry(context.applicationContext.packageManager)
    private val cabOrchestrator = CabBookingOrchestrator(
        providerRegistry = cabProviderRegistry,
        deepLinkBuilder = CabDeepLinkBuilder(context.applicationContext, cabProviderRegistry),
        accessibilityService = CabAccessibilityService(),
        locationResolver = AndroidCabLocationResolver(context.applicationContext),
        providerLauncher = { intent: Intent ->
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
        providerLauncher = FoodProviderLauncher { intent: Intent ->
            runCatching {
                context.applicationContext.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    )
    private val groceryProviderRegistry = GroceryProviderRegistry(context.applicationContext.packageManager)
    private val groceryOrchestrator = GroceryBookingOrchestrator(
        providerRegistry = groceryProviderRegistry,
        deepLinkBuilder = GroceryDeepLinkBuilder(context.applicationContext, groceryProviderRegistry),
        accessibilityService = GroceryAccessibilityService(),
        providerLauncher = { intent: Intent ->
            runCatching {
                context.applicationContext.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    )

    override fun execute(commandIntent: CommandIntent): CommandResult {
        if (isDangerousFinalCommand(commandIntent)) {
            return CommandResult.blocked(
                message = "That final step must stay manual.",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

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
            ActionType.CALL_CONTACT -> CommandResult.failure(
                "Call automation is intentionally not implemented in this starter.",
                commandIntent.intentType,
                commandIntent.actionType,
                commandIntent.entities
            )
            ActionType.CAB_BOOKING -> cabOrchestrator.start(commandIntent.toCabBookingRequest()).toCabCommandResult()
            ActionType.FOOD_ORDER -> foodOrchestrator.start(commandIntent.toFoodBookingRequest()).toFoodCommandResult()
            ActionType.GROCERY_BOOKING -> groceryOrchestrator.start(commandIntent.toGroceryBookingRequest()).toGroceryCommandResult()
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

    override fun hasActiveCabBookingSession(): Boolean {
        return cabOrchestrator.isActive()
    }

    override fun cancelCabBookingSession(): CommandResult {
        return cabOrchestrator.cancelSession().toCabCommandResult()
    }

    override fun handleCabBookingText(rawText: String): CommandResult {
        return cabOrchestrator.handleUserInput(rawText).toCabCommandResult()
    }

    override fun hasActiveFoodBookingSession(): Boolean {
        return foodOrchestrator.isActive()
    }

    override fun cancelFoodBookingSession(): CommandResult {
        return foodOrchestrator.cancelSession().toFoodCommandResult()
    }

    override fun handleFoodBookingText(rawText: String): CommandResult {
        return foodOrchestrator.handleUserInput(rawText).toFoodCommandResult()
    }

    override fun hasActiveGroceryBookingSession(): Boolean {
        return groceryOrchestrator.isActive()
    }

    override fun cancelGroceryBookingSession(): CommandResult {
        return groceryOrchestrator.cancelSession().toGroceryCommandResult()
    }

    override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult {
        return groceryOrchestrator.handleUserInput(rawText, userConfirmed).toGroceryCommandResult()
    }

    private fun isDangerousFinalCommand(commandIntent: CommandIntent): Boolean {
        val normalized = buildString {
            append(commandIntent.normalizedText)
            append(' ')
            append(commandIntent.entities.values.joinToString(separator = " "))
        }.lowercase()

        if (commandIntent.actionType == ActionType.CAB_BOOKING ||
            commandIntent.actionType == ActionType.FOOD_ORDER ||
            commandIntent.actionType == ActionType.GROCERY_BOOKING
        ) {
            return false
        }

        val sensitivePatterns = listOf(
            "send money",
            "payment",
            "pay now",
            "pay with",
            "bank",
            "banking",
            "upi",
            "password",
            "otp",
            "captcha",
            "login",
            "sign in",
            "delete",
            "erase",
            "remove account",
            "confirm booking",
            "book now",
            "book ride",
            "submit",
            "request ride",
            "request now",
            "final booking",
            "complete payment"
        )

        return sensitivePatterns.any { pattern ->
            Regex("""\b${Regex.escape(pattern)}\b""").containsMatchIn(normalized)
        }
    }
}
