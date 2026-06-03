package com.nova.luna.model

data class CommandResult(
    val success: Boolean,
    val message: String,
    val intentType: IntentType = IntentType.UNKNOWN,
    val actionType: ActionType = ActionType.UNKNOWN,
    val safetyDecision: SafetyDecision = SafetyDecision.allow(),
    val shouldStopListening: Boolean = false,
    val entities: Map<String, String> = emptyMap()
) {
    companion object {
        fun success(
            message: String,
            intentType: IntentType = IntentType.UNKNOWN,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap(),
            shouldStopListening: Boolean = false
        ): CommandResult {
            return CommandResult(
                success = true,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.allow(),
                shouldStopListening = shouldStopListening,
                entities = entities
            )
        }

        fun failure(
            message: String,
            intentType: IntentType = IntentType.UNKNOWN,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.allow(),
                entities = entities
            )
        }

        fun blocked(
            message: String,
            intentType: IntentType = IntentType.BLOCKED,
            actionType: ActionType = ActionType.BLOCKED,
            entities: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.block(message),
                entities = entities
            )
        }

        fun biometricRequired(
            message: String,
            intentType: IntentType = IntentType.SENSITIVE,
            actionType: ActionType = ActionType.UNKNOWN,
            entities: Map<String, String> = emptyMap()
        ): CommandResult {
            return CommandResult(
                success = false,
                message = message,
                intentType = intentType,
                actionType = actionType,
                safetyDecision = SafetyDecision.requireBiometric(message),
                entities = entities
            )
        }
    }
}

