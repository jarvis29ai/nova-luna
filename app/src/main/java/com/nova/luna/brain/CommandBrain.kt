package com.nova.luna.brain

import android.content.Context
import com.nova.luna.executor.AppLauncher
import com.nova.luna.cab.CabIntentParser
import com.nova.luna.executor.ActionExecutor
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainAction
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate
import com.nova.luna.util.AssistantTextNormalizer

class CommandBrain(context: Context) {
    private val parser = RuleBasedCommandParser()
    private val appLauncher = AppLauncher(context.applicationContext)
    private val resolver = IntentResolver(appLauncher)
    private val safetyGate = SafetyGate()
    private val router = CommandRouter(ActionExecutor(context.applicationContext), safetyGate)
    private val brainService = BrainService()
    private val confirmationParser = CabIntentParser()
    private var pendingConfirmationAction: BrainAction? = null

    fun process(rawText: String): CommandResult {
        val normalized = AssistantTextNormalizer.stripWakeWords(rawText).trim()
        if (normalized.isBlank()) {
            return CommandResult.failure("I did not understand that command.")
        }

        if (isExplicitStopListeningCommand(normalized)) {
            pendingConfirmationAction = null
            if (router.hasActiveCabBookingSession()) {
                router.cancelCabBookingSession()
            }
            if (router.hasActiveFoodBookingSession()) {
                router.cancelFoodBookingSession()
            }
            if (router.hasActiveGroceryBookingSession()) {
                router.cancelGroceryBookingSession()
            }

            return CommandResult.success(
                message = "Stopping listening.",
                intentType = com.nova.luna.model.IntentType.CONTROL,
                actionType = com.nova.luna.model.ActionType.STOP_SERVICE,
                entities = mapOf("command" to "stop"),
                shouldStopListening = true
            )
        }

        pendingConfirmationAction?.let { pendingAction ->
            when (confirmationParser.parseFinalConfirmationReply(normalized)) {
                com.nova.luna.cab.CabFinalConfirmationReply.CONFIRM -> {
                    pendingConfirmationAction = null
                    return router.route(pendingAction, userConfirmed = true)
                }

                com.nova.luna.cab.CabFinalConfirmationReply.DECLINE -> {
                    pendingConfirmationAction = null
                    return CommandResult.blocked(
                        message = "Okay, I cancelled that request.",
                        entities = pendingAction.params
                    )
                }

                com.nova.luna.cab.CabFinalConfirmationReply.NONE -> {
                    pendingConfirmationAction = null
                }
            }
        }

        if (router.hasActiveCabBookingSession()) {
            return router.routeCabConversation(normalized)
        }

        if (router.hasActiveFoodBookingSession()) {
            return router.routeFoodConversation(normalized)
        }

        if (router.hasActiveGroceryBookingSession()) {
            return router.routeGroceryConversation(normalized, userConfirmed = false)
        }

        if (!router.hasActiveCabBookingSession() && !router.hasActiveGroceryBookingSession()) {
            val parsed = parser.parse(normalized)
            if (parsed.intentType == IntentType.FOOD_ORDER || parsed.actionType == ActionType.FOOD_ORDER) {
                return router.route(parsed)
            }
        }

        val action = brainService.process(
            rawText = normalized,
            activeCabSession = router.hasActiveCabBookingSession(),
            activeGrocerySession = router.hasActiveGroceryBookingSession()
        )
        val result = router.route(action)
        if (result.awaitingConfirmation) {
            pendingConfirmationAction = action
        }
        return result
    }

    private fun isExplicitStopListeningCommand(rawText: String): Boolean {
        val normalized = rawText.trim().lowercase()
        return normalized.contains("listening") ||
            normalized.contains("voice") ||
            normalized.contains("speaking") ||
            normalized == "quiet" ||
            normalized == "be quiet"
    }
}
