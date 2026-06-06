package com.nova.luna.brain

import android.content.Context
import com.nova.luna.executor.ActionExecutor
import com.nova.luna.executor.AppLauncher
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.model.IntentType
import com.nova.luna.safety.SafetyGate

class CommandBrain(context: Context) {
    private val parser = RuleBasedCommandParser()
    private val appLauncher = AppLauncher(context.applicationContext)
    private val resolver = IntentResolver(appLauncher)
    private val safetyGate = SafetyGate()
    private val router = CommandRouter(ActionExecutor(context.applicationContext))

    fun process(rawText: String): CommandResult {
        val parsed = parser.parse(rawText)

        if (parsed.intentType == IntentType.BLOCKED || parsed.actionType == ActionType.BLOCKED) {
            return CommandResult.blocked(
                message = "Blocked command: payments, banking, checkout, passwords, OTPs, and CAPTCHA work must stay manual.",
                intentType = parsed.intentType,
                actionType = parsed.actionType,
                entities = parsed.entities
            )
        }

        if (
            parsed.intentType == IntentType.CAB_BOOKING ||
            parsed.actionType == ActionType.CAB_BOOKING ||
            parsed.intentType == IntentType.FOOD_ORDER ||
            parsed.actionType == ActionType.FOOD_ORDER ||
            parsed.intentType == IntentType.GROCERY_BOOKING ||
            parsed.actionType == ActionType.GROCERY_BOOKING ||
            parsed.intentType == IntentType.COMMUNICATION ||
            parsed.actionType == ActionType.COMMUNICATION ||
            parsed.intentType == IntentType.CONTENT_CREATION ||
            parsed.actionType == ActionType.CONTENT_CREATION
        ) {
            val resolved = resolver.resolve(parsed)
            val decision = safetyGate.evaluate(resolved)
            if (!decision.allowed) {
                return if (decision.requiresBiometric) {
                    CommandResult.biometricRequired(
                        message = decision.message,
                        intentType = resolved.intentType,
                        actionType = resolved.actionType,
                        entities = resolved.entities
                    )
                } else {
                    CommandResult.blocked(
                        message = decision.message,
                        intentType = resolved.intentType,
                        actionType = resolved.actionType,
                        entities = resolved.entities
                    )
                }
            }

            val result = router.route(resolved)
            return result.copy(safetyDecision = decision)
        }

        if (router.hasActiveCabBookingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeCabConversation(rawText)
        }

        if (router.hasActiveFoodBookingSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeFoodConversation(rawText)
        }

        if (router.hasActivePhoneContactSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routePhoneContactConversation(rawText)
        }

        if (router.hasActiveCommunicationSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeCommunicationConversation(rawText)
        }

        if (router.hasActiveContentCreationSession() && parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return router.routeContentCreationConversation(rawText)
        }

        if (parsed.intentType == IntentType.UNKNOWN && parsed.actionType == ActionType.UNKNOWN) {
            return CommandResult.failure(
                "I did not understand that command.",
                parsed.intentType,
                parsed.actionType,
                parsed.entities
            )
        }

        val resolved = resolver.resolve(parsed)
        val decision = safetyGate.evaluate(resolved)
        if (!decision.allowed) {
            return if (decision.requiresBiometric) {
                CommandResult.biometricRequired(
                    message = decision.message,
                    intentType = resolved.intentType,
                    actionType = resolved.actionType,
                    entities = resolved.entities
                )
            } else {
                CommandResult.blocked(
                    message = decision.message,
                    intentType = resolved.intentType,
                    actionType = resolved.actionType,
                    entities = resolved.entities
                )
            }
        }

        val result = router.route(resolved)
        return result.copy(safetyDecision = decision)
    }
}
