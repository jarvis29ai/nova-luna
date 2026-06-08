package com.nova.luna.executor

import android.content.Context
import com.nova.luna.model.ActionType
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.memory.BrainSessionType
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
import com.nova.luna.media.MediaOrchestrator
import com.nova.luna.shopping.ShoppingOrchestrator
import com.nova.luna.shopping.ShoppingStatus
import com.nova.luna.music.*
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateVerifier
import com.nova.luna.service.NovaAccessibilityService

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
    private val communicationOrchestrator by lazy { CommunicationOrchestrator(context.applicationContext) }
    private val contentCreationOrchestrator = ContentCreationOrchestrator(context.applicationContext)
    private val mediaOrchestrator = MediaOrchestrator(context.applicationContext)
    private val shoppingOrchestrator = ShoppingOrchestrator(context.applicationContext)
    private val cabProviderRegistry = CabProviderRegistry(context.applicationContext.packageManager)
    private val foodProviderRegistry = FoodProviderRegistry(context.applicationContext.packageManager)
    private val groceryProviderRegistry = GroceryProviderRegistry(context.applicationContext.packageManager)
    private val musicProviderRegistry = MusicProviderRegistry(context.applicationContext.packageManager)
    private val musicDeepLinkBuilder = MusicDeepLinkBuilder(context.applicationContext)
    private val musicAppLauncher = MusicAppLauncher(context.applicationContext, musicDeepLinkBuilder, musicProviderRegistry)
    private val screenStateVerifier = ScreenStateVerifier()
    private val musicOrchestrator by lazy {
        MusicOrchestrator(
            context = context.applicationContext,
            parser = MusicIntentParser(),
            registry = musicProviderRegistry,
            launcher = musicAppLauncher,
            searchEngine = MusicSearchEngine(),
            matcher = MusicResultMatcher(),
            safetyDetector = MusicSafetyDetector(),
            playbackController = MusicPlaybackController(context.applicationContext),
            responses = MusicVoiceResponses(),
            cardBuilder = MusicMiniCardBuilder()
        )
    }

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
        val service = NovaAccessibilityService.instance
        val requiresService = actionRequiresAccessibility(commandIntent.actionType)
        
        if (requiresService && service == null) {
            return CommandResult.failure(
                message = "Accessibility service is not enabled. Please enable it in Settings.",
                status = ActionResultStatus.PERMISSION_REQUIRED,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        val captureScreenState = shouldVerifyScreen(commandIntent.actionType)
        val beforeScreenState = if (captureScreenState && service != null) {
            service.captureScreenState()
        } else {
            null
        }

        val result = executeWithRetry(commandIntent, service)
        val memoryResult = result.withMemoryContext(sessionTypeForAction(commandIntent.actionType))

        val afterScreenState = if (captureScreenState && service != null) {
            service.captureScreenState()
        } else {
            null
        }

        return attachScreenVerification(commandIntent, memoryResult, beforeScreenState, afterScreenState)
    }

    private fun actionRequiresAccessibility(actionType: ActionType): Boolean {
        return actionType in setOf(
            ActionType.GO_HOME,
            ActionType.GO_BACK,
            ActionType.OPEN_RECENTS,
            ActionType.OPEN_NOTIFICATIONS,
            ActionType.CLICK_TEXT,
            ActionType.TAP_TEXT,
            ActionType.TAP_DESCRIPTION,
            ActionType.TAP_NODE,
            ActionType.SCROLL_FORWARD,
            ActionType.SCROLL_DOWN,
            ActionType.SCROLL_BACKWARD,
            ActionType.SCROLL_UP,
            ActionType.TYPE_TEXT,
            ActionType.READ_SCREEN,
            ActionType.WAIT_FOR_TEXT,
            ActionType.WAIT_FOR_APP,
            ActionType.TAKE_SCREENSHOT
        )
    }

    private fun executeWithRetry(commandIntent: CommandIntent, service: NovaAccessibilityService?): CommandResult {
        var retryCount = 0
        val maxRetries = 2
        var lastResult: CommandResult? = null

        while (retryCount <= maxRetries) {
            val result = performExecution(commandIntent, service, retryCount)
            if (result.success || result.status == ActionResultStatus.NEEDS_CONFIRMATION || result.status == ActionResultStatus.BLOCKED) {
                return result
            }
            lastResult = result
            retryCount++
            if (retryCount <= maxRetries && service != null) {
                // Try to recover: maybe scroll if it's a tap action
                if (commandIntent.actionType == ActionType.TAP_TEXT || commandIntent.actionType == ActionType.CLICK_TEXT) {
                    service.scrollForward()
                }
                Thread.sleep(500)
            }
        }
        return lastResult ?: CommandResult.failure(message = "Execution failed", status = ActionResultStatus.FAILED)
    }

    private fun performExecution(commandIntent: CommandIntent, service: NovaAccessibilityService?, retryCount: Int): CommandResult {
        return when (commandIntent.actionType) {
            ActionType.LAUNCH_APP, ActionType.OPEN_APP -> appLauncher.launchApp(commandIntent)
            ActionType.GO_HOME -> navExecutor.goHome(commandIntent)
            ActionType.GO_BACK -> navExecutor.goBack(commandIntent)
            ActionType.OPEN_RECENTS -> navExecutor.openRecents(commandIntent)
            ActionType.OPEN_NOTIFICATIONS -> navExecutor.openNotifications(commandIntent)
            ActionType.CLICK_TEXT, ActionType.TAP_TEXT -> tapExecutor.tap(commandIntent)
            ActionType.TAP_DESCRIPTION -> {
                val query = commandIntent.entities["text"] ?: commandIntent.entities["query"] ?: ""
                if (service?.clickByContentDescription(query) == true) {
                    CommandResult.success("Tapped $query by description", actionType = commandIntent.actionType)
                } else {
                    CommandResult.failure(message = "Could not find $query by description", status = ActionResultStatus.NOT_FOUND, actionType = commandIntent.actionType)
                }
            }
            ActionType.SCROLL_FORWARD, ActionType.SCROLL_DOWN -> scrollExecutor.scrollForward(commandIntent)
            ActionType.SCROLL_BACKWARD, ActionType.SCROLL_UP -> scrollExecutor.scrollBackward(commandIntent)
            ActionType.TYPE_TEXT -> typeExecutor.typeText(commandIntent)
            ActionType.READ_SCREEN -> {
                val state = service?.captureScreenState()
                if (state != null) {
                    CommandResult.success("Screen read successful", entities = commandIntent.entities + mapOf("screenSummary" to state.summarizedState))
                } else {
                    CommandResult.failure(message = "Could not read screen", status = ActionResultStatus.FAILED)
                }
            }
            ActionType.WAIT_FOR_TEXT -> {
                val text = commandIntent.entities["text"] ?: ""
                if (service?.waitForText(text) == true) {
                    CommandResult.success("Found text: $text")
                } else {
                    CommandResult.failure(message = "Timed out waiting for text: $text", status = ActionResultStatus.TIMEOUT)
                }
            }
            ActionType.WAIT_FOR_APP -> {
                val pkg = commandIntent.entities["packageName"] ?: ""
                if (service?.waitForApp(pkg) == true) {
                    CommandResult.success("App $pkg is now in foreground")
                } else {
                    CommandResult.failure(message = "Timed out waiting for app: $pkg", status = ActionResultStatus.TIMEOUT)
                }
            }
            ActionType.READ_NOTIFICATIONS -> notificationReader.readNotifications(commandIntent)
            ActionType.TAKE_SCREENSHOT -> CommandResult.failure(
                "Screenshot is scaffolded but not implemented in this starter.",
                status = ActionResultStatus.UNSUPPORTED,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            ).withMemoryContext(BrainSessionType.SCREEN)
            ActionType.OPEN_SETTINGS -> settingsExecutor.openSettings(commandIntent).withMemoryContext(BrainSessionType.BASIC_CONTROL)
            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> settingsExecutor.openAccessibilitySettings(commandIntent).withMemoryContext(BrainSessionType.BASIC_CONTROL)
            ActionType.OPEN_USAGE_ACCESS_SETTINGS -> settingsExecutor.openUsageAccessSettings(commandIntent).withMemoryContext(BrainSessionType.BASIC_CONTROL)
            ActionType.CALL_CONTACT -> {
                val phoneRequest = phoneParser.parse(commandIntent.rawText)
                phoneOrchestrator.start(phoneRequest).toPhoneCommandResult(commandIntent).withMemoryContext(BrainSessionType.PHONE)
            }
            ActionType.CAB_BOOKING -> cabOrchestrator.start(commandIntent.toCabBookingRequest()).toCabCommandResult().withMemoryContext(BrainSessionType.CAB)
            ActionType.FOOD_ORDER -> foodOrchestrator.start(commandIntent.toFoodBookingRequest()).toFoodCommandResult().withMemoryContext(BrainSessionType.FOOD)
            ActionType.GROCERY_BOOKING -> groceryOrchestrator.start(commandIntent.toGroceryBookingRequest()).toGroceryCommandResult().withMemoryContext(BrainSessionType.GROCERY)
            ActionType.CONTENT_CREATION -> handleContentCreationText(commandIntent.rawText, commandIntent).withMemoryContext(BrainSessionType.CONTENT)
            ActionType.MUSIC -> handleMusicText(commandIntent.rawText, commandIntent).withMemoryContext(BrainSessionType.MUSIC)
            ActionType.COMMUNICATION -> {
                val result = communicationOrchestrator.handleRequest(commandIntent.rawText)
                CommandResult(
                    success = result.status == com.nova.luna.communication.CommunicationStatus.SUCCESS ||
                              result.status == com.nova.luna.communication.CommunicationStatus.NEEDS_CONFIRMATION,
                    status = if (result.status == com.nova.luna.communication.CommunicationStatus.NEEDS_CONFIRMATION) ActionResultStatus.NEEDS_CONFIRMATION else ActionResultStatus.SUCCESS,
                    message = result.popupText,
                    intentType = commandIntent.intentType,
                    actionType = commandIntent.actionType,
                    entities = commandIntent.entities + mapOf("voiceText" to result.voiceText)
                ).withMemoryContext(BrainSessionType.COMMUNICATION)
            }
            ActionType.SHOPPING -> handleShoppingText(commandIntent.rawText, commandIntent).withMemoryContext(BrainSessionType.SHOPPING)
            ActionType.STOP_SERVICE -> CommandResult.success(
                message = "Stopping listening.",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities,
                shouldStopListening = true
            ).withMemoryContext(BrainSessionType.BASIC_CONTROL)
            ActionType.BLOCKED,
            ActionType.UNKNOWN -> CommandResult.failure(
                "I could not map that command to a safe action.",
                status = ActionResultStatus.BLOCKED,
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            ).withMemoryContext(BrainSessionType.BASIC_CONTROL)
            ActionType.MEDIA_CONTROL -> handleMediaText(commandIntent.rawText, commandIntent).withMemoryContext(BrainSessionType.MEDIA)
            else -> CommandResult.failure("Unsupported action type", status = ActionResultStatus.UNSUPPORTED)
        }
    }

    override fun hasActiveCabBookingSession(): Boolean = cabOrchestrator.isActive()
    override fun cancelCabBookingSession(): CommandResult = CommandResult.success("Cancelled").withMemoryContext(BrainSessionType.CAB)
    override fun handleCabBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = cabOrchestrator.handleUserInput(rawText).toCabCommandResult(commandIntent).withMemoryContext(BrainSessionType.CAB)

    override fun hasActiveFoodBookingSession(): Boolean = foodOrchestrator.isActive()
    override fun cancelFoodBookingSession(): CommandResult = CommandResult.success("Cancelled").withMemoryContext(BrainSessionType.FOOD)
    override fun handleFoodBookingText(rawText: String, commandIntent: CommandIntent): CommandResult = foodOrchestrator.handleUserInput(rawText).toFoodCommandResult(commandIntent).withMemoryContext(BrainSessionType.FOOD)

    override fun hasActiveGroceryBookingSession(): Boolean = groceryOrchestrator.isActive()
    override fun cancelGroceryBookingSession(): CommandResult = groceryOrchestrator.cancelSession().toGroceryCommandResult().withMemoryContext(BrainSessionType.GROCERY)
    override fun handleGroceryBookingText(rawText: String, commandIntent: CommandIntent, userConfirmed: Boolean): CommandResult = groceryOrchestrator.handleUserInput(rawText, userConfirmed).toGroceryCommandResult(commandIntent).withMemoryContext(BrainSessionType.GROCERY)

    override fun hasActivePhoneContactSession(): Boolean = phoneOrchestrator.isActive()
    override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = phoneOrchestrator.handleUserInput(rawText).toPhoneCommandResult(commandIntent).withMemoryContext(BrainSessionType.PHONE)

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
        ).withMemoryContext(BrainSessionType.COMMUNICATION)
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
        ).withMemoryContext(BrainSessionType.CONTENT)
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
        ).withMemoryContext(BrainSessionType.MEDIA)
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
        ).withMemoryContext(BrainSessionType.SHOPPING)
    }

    override fun hasActiveMusicSession(): Boolean = musicOrchestrator.isActive()
    override fun handleMusicText(rawText: String, commandIntent: CommandIntent): CommandResult {
        val result = musicOrchestrator.handleRequest(rawText)
        return result.toCommandResult(commandIntent).withMemoryContext(BrainSessionType.MUSIC)
    }

    private fun shouldVerifyScreen(actionType: ActionType): Boolean {
        return actionType in setOf(
            ActionType.LAUNCH_APP,
            ActionType.GO_HOME,
            ActionType.GO_BACK,
            ActionType.OPEN_RECENTS,
            ActionType.OPEN_NOTIFICATIONS,
            ActionType.CLICK_TEXT,
            ActionType.SCROLL_FORWARD,
            ActionType.SCROLL_BACKWARD,
            ActionType.TYPE_TEXT,
            ActionType.OPEN_SETTINGS,
            ActionType.OPEN_ACCESSIBILITY_SETTINGS,
            ActionType.OPEN_USAGE_ACCESS_SETTINGS
        )
    }

    private fun attachScreenVerification(
        commandIntent: CommandIntent,
        result: CommandResult,
        beforeScreenState: ScreenState?,
        afterScreenState: ScreenState?
    ): CommandResult {
        val verification = screenStateVerifier.verify(
            before = beforeScreenState,
            after = afterScreenState,
            commandIntent = commandIntent,
            commandResult = result
        )

        if (!verification.applicable) {
            return result
        }

        return result.copy(
            entities = result.entities + verification.toEntityMap(),
            memoryMetadata = result.memoryMetadata + buildMap {
                put("screenVerificationStatus", verification.status.name)
                put("screenVerificationApplicable", verification.applicable.toString())
                put("screenVerificationChanged", verification.changed.toString())
                put("screenVerificationVerified", verification.verified.toString())
                put("screenVerificationMessage", verification.message)
                beforeScreenState?.summarizedState?.takeIf { it.isNotBlank() }?.let { put("screenBeforeSummary", it) }
                afterScreenState?.summarizedState?.takeIf { it.isNotBlank() }?.let { put("screenAfterSummary", it) }
                put("screenVerificationRetryable", (!verification.verified).toString())
            }
        )
    }

    private fun CommandResult.withMemoryContext(
        sessionType: BrainSessionType,
        memoryMetadata: Map<String, String> = emptyMap()
    ): CommandResult {
        return copy(
            memorySessionType = memorySessionType ?: sessionType,
            memoryMetadata = entities + memoryMetadata
        )
    }

    private fun sessionTypeForAction(actionType: ActionType): BrainSessionType {
        return when (actionType) {
            ActionType.LAUNCH_APP,
            ActionType.OPEN_APP,
            ActionType.GO_HOME,
            ActionType.GO_BACK,
            ActionType.OPEN_RECENTS,
            ActionType.OPEN_NOTIFICATIONS,
            ActionType.CLICK_TEXT,
            ActionType.TAP_TEXT,
            ActionType.TAP_DESCRIPTION,
            ActionType.TAP_NODE,
            ActionType.SCROLL_FORWARD,
            ActionType.SCROLL_DOWN,
            ActionType.SCROLL_BACKWARD,
            ActionType.SCROLL_UP,
            ActionType.TYPE_TEXT,
            ActionType.READ_SCREEN,
            ActionType.WAIT_FOR_TEXT,
            ActionType.WAIT_FOR_APP,
            ActionType.READ_NOTIFICATIONS,
            ActionType.STOP_SERVICE,
            ActionType.OPEN_SETTINGS,
            ActionType.OPEN_ACCESSIBILITY_SETTINGS,
            ActionType.OPEN_USAGE_ACCESS_SETTINGS,
            ActionType.TAKE_SCREENSHOT,
            ActionType.BLOCKED,
            ActionType.CANCEL,
            ActionType.NO_OP,
            ActionType.UNKNOWN -> BrainSessionType.BASIC_CONTROL

            ActionType.CALL_CONTACT -> BrainSessionType.PHONE
            ActionType.FOOD_ORDER -> BrainSessionType.FOOD
            ActionType.CAB_BOOKING -> BrainSessionType.CAB
            ActionType.GROCERY_BOOKING -> BrainSessionType.GROCERY
            ActionType.COMMUNICATION -> BrainSessionType.COMMUNICATION
            ActionType.CONTENT_CREATION -> BrainSessionType.CONTENT
            ActionType.MEDIA_CONTROL -> BrainSessionType.MEDIA
            ActionType.SHOPPING -> BrainSessionType.SHOPPING
            ActionType.MUSIC -> BrainSessionType.MUSIC
        }
    }
}
