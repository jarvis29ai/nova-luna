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
