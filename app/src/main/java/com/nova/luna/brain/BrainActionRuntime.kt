package com.nova.luna.brain

import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.model.*
import com.nova.luna.phone.PhoneActionExecutor
import com.nova.luna.safety.SafetyGate
import java.util.Locale

class BrainActionRuntime(
    private val commandRouter: CommandRouter,
    private val safetyGate: SafetyGate,
    private val phoneActionExecutor: PhoneActionExecutor? = null,
    private val validator: BrainActionValidator = BrainActionValidator()
) {
    fun isAcceptable(brainAction: BrainAction): Boolean {
        return validator.isAcceptable(brainAction)
    }

    fun evaluateSafety(
        brainAction: BrainAction,
        rawText: String? = null,
        pendingConfirmation: PendingConfirmation? = null,
        userConfirmed: Boolean = false
    ): SafetyDecision {
        return safetyGate.evaluate(
            action = brainAction,
            originalUserText = rawText,
            pendingConfirmation = pendingConfirmation,
            userConfirmed = userConfirmed
        )
    }

    fun execute(
        brainAction: BrainAction,
        rawText: String,
        parsed: CommandIntent,
        pendingConfirmation: PendingConfirmation? = null,
        userConfirmed: Boolean = false
    ): CommandResult? {
        val safetyDecision = evaluateSafety(brainAction, rawText, pendingConfirmation, userConfirmed)
        
        if (!isAcceptable(brainAction)) {
            return CommandResult.blocked(
                message = "This action was flagged as potentially unsafe by the pre-validator.",
                intentType = resultIntentType(brainAction),
                actionType = resultActionType(brainAction),
                entities = brainAction.params,
                safetyDecision = safetyDecision
            )
        }

        val resultIntentType = resultIntentType(brainAction)
        val resultActionType = resultActionType(brainAction)
        val sessionType = sessionTypeForBrainAction(brainAction) ?: pendingConfirmation?.sessionType
        val confirmationType = confirmationTypeForBrainAction(brainAction)
        val memoryMetadata = buildMap {
            putAll(brainAction.params)
            put("rawText", rawText)
            put("parsedIntentType", parsed.intentType.name)
            put("parsedActionType", parsed.actionType.name)
            put("brainActionIntent", brainAction.intent)
            put("safetyStatus", safetyDecision.status.name)
            put("safetyCategory", safetyDecision.category.name)
            put("safetyReason", safetyDecision.reason)
            put("finalAuthority", "SafetyGate")
        }

        if (safetyDecision.status == SafetyStatus.BLOCKED) {
            return CommandResult.blocked(
                message = safetyDecision.reason,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params,
                memorySessionType = sessionType,
                pendingConfirmationType = confirmationType,
                memoryMetadata = memoryMetadata,
                safetyDecision = safetyDecision
            )
        }

        if (safetyDecision.status == SafetyStatus.CONFIRMATION_REQUIRED || safetyDecision.requiresUserConfirmation) {
            return CommandResult.confirmationRequired(
                message = safetyDecision.reason,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params,
                memorySessionType = sessionType,
                pendingConfirmationType = confirmationType,
                memoryMetadata = memoryMetadata,
                safetyDecision = safetyDecision
            )
        }

        // ONLY IF ALLOWED
        return when (brainAction.actionType) {
            BrainActionType.EXTERNAL_ACTION -> {
                val routedResult = commandRouter.route(brainAction)
                routedResult.copy(
                    safetyDecision = safetyDecision,
                    memorySessionType = routedResult.memorySessionType ?: sessionType,
                    pendingConfirmationType = routedResult.pendingConfirmationType ?: confirmationType,
                    memoryMetadata = routedResult.memoryMetadata + memoryMetadata
                )
            }

            BrainActionType.READ_ONLY,
            BrainActionType.NONE -> {
                // Check if it's a known phone action even if actionType is NONE (legacy/fallback)
                if (phoneActionExecutor != null && isPhoneActionIntent(brainAction.intent)) {
                    executePhoneAction(brainAction, safetyDecision, sessionType, memoryMetadata)
                } else {
                    CommandResult.success(
                        message = brainAction.reply,
                        intentType = resultIntentType(brainAction),
                        actionType = resultActionType(brainAction),
                        entities = brainAction.params,
                        memorySessionType = sessionType,
                        memoryMetadata = memoryMetadata,
                        safetyDecision = safetyDecision
                    )
                }
            }


            BrainActionType.PREPARE -> CommandResult.confirmationRequired(
                message = brainAction.nextQuestion?.takeIf { it.isNotBlank() }
                    ?: safetyDecision.reason,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params,
                memorySessionType = sessionType,
                pendingConfirmationType = confirmationType,
                memoryMetadata = memoryMetadata,
                safetyDecision = safetyDecision
            )

            BrainActionType.HUMAN_ONLY -> CommandResult.blocked(
                message = safetyDecision.reason,
                intentType = resultIntentType,
                actionType = resultActionType,
                entities = brainAction.params,
                memorySessionType = sessionType,
                memoryMetadata = memoryMetadata,
                safetyDecision = safetyDecision
            )

            else -> {
                if (phoneActionExecutor != null && isSupportedPhoneActionType(brainAction.actionType)) {
                    executePhoneAction(brainAction, safetyDecision, sessionType, memoryMetadata)
                } else {
                    CommandResult.success(
                        message = brainAction.reply,
                        intentType = resultIntentType(brainAction),
                        actionType = resultActionType(brainAction),
                        entities = brainAction.params,
                        memorySessionType = sessionType,
                        memoryMetadata = memoryMetadata,
                        safetyDecision = safetyDecision
                    )
                }
            }
        }
    }

    private fun isPhoneActionIntent(intent: String): Boolean {
        val lower = intent.lowercase(Locale.US)
        return lower.contains("camera") || lower.contains("settings") || 
               lower.contains("flashlight") || lower.contains("search") || 
               lower.contains("back") || lower.contains("home") || 
               lower.contains("recents") || lower.contains("notifications")
    }

    private fun isSupportedPhoneActionType(type: BrainActionType): Boolean {
        return type in setOf(
            BrainActionType.OPEN_APP,
            BrainActionType.OPEN_CAMERA,
            BrainActionType.OPEN_SETTINGS,
            BrainActionType.SEARCH_WEB,
            BrainActionType.TOGGLE_FLASHLIGHT
        )
    }

    private fun executePhoneAction(
        brainAction: BrainAction,
        safetyDecision: SafetyDecision,
        sessionType: BrainSessionType?,
        memoryMetadata: Map<String, String>
    ): CommandResult {
        val phoneResult = phoneActionExecutor?.execute(brainAction)
        val resolvedEntities = buildMap {
            putAll(brainAction.params)
            phoneResult?.packageName?.let { put("resolvedPackage", it) }
            // Legacy/Test compatibility for app label
            if (brainAction.actionType == BrainActionType.OPEN_APP) {
                val label = phoneResult?.label 
                    ?: brainAction.params["appName"] 
                    ?: brainAction.params["package"]
                label?.let { put("resolvedLabel", it) }
            }
        }
        
        return CommandResult(
            success = phoneResult?.success ?: false,
            status = if (phoneResult?.success == true) ActionResultStatus.SUCCESS else ActionResultStatus.FAILED,
            message = phoneResult?.reason ?: "Phone action execution failed.",
            intentType = resultIntentType(brainAction),
            actionType = resultActionType(brainAction),
            entities = resolvedEntities,
            memorySessionType = sessionType,
            memoryMetadata = memoryMetadata + mapOf(
                "phoneActionAttempted" to (phoneResult?.attempted?.toString() ?: "false"),
                "phoneActionErrorCode" to (phoneResult?.errorCode ?: "")
            ) + (phoneResult?.packageName?.let { mapOf("resolvedPackage" to it) } ?: emptyMap()),
            safetyDecision = safetyDecision,
            phoneActionResult = phoneResult
        )
    }

    private fun resultIntentType(brainAction: BrainAction): IntentType {
        val mapped = brainAction.toCommandIntent()
        if (mapped.intentType != IntentType.UNKNOWN) {
            return mapped.intentType
        }

        return when {
            brainAction.intent.startsWith("content", ignoreCase = true) -> IntentType.CONTENT_CREATION
            else -> IntentType.UNKNOWN
        }
    }

    private fun resultActionType(brainAction: BrainAction): ActionType {
        val mapped = brainAction.toCommandIntent()
        if (mapped.actionType != ActionType.UNKNOWN) {
            return mapped.actionType
        }

        return when {
            brainAction.intent.startsWith("content", ignoreCase = true) -> ActionType.CONTENT_CREATION
            else -> ActionType.UNKNOWN
        }
    }

    private fun sessionTypeForBrainAction(brainAction: BrainAction): BrainSessionType? {
        return when {
            brainAction.intent.startsWith("cab", ignoreCase = true) -> BrainSessionType.CAB
            brainAction.intent.startsWith("food", ignoreCase = true) -> BrainSessionType.FOOD
            brainAction.intent.startsWith("grocery", ignoreCase = true) -> BrainSessionType.GROCERY
            brainAction.intent.startsWith("shopping", ignoreCase = true) -> BrainSessionType.SHOPPING
            brainAction.intent.startsWith("music", ignoreCase = true) -> BrainSessionType.MUSIC
            brainAction.intent.startsWith("media", ignoreCase = true) -> BrainSessionType.MEDIA
            brainAction.intent.startsWith("content", ignoreCase = true) -> BrainSessionType.CONTENT
            brainAction.intent.startsWith("communication", ignoreCase = true) -> BrainSessionType.COMMUNICATION
            brainAction.intent.startsWith("phone", ignoreCase = true) -> BrainSessionType.PHONE
            brainAction.intent.startsWith("screen", ignoreCase = true) -> BrainSessionType.SCREEN
            brainAction.intent.equals("online_ai_permission", ignoreCase = true) -> BrainSessionType.ONLINE_HELPER
            brainAction.intent.equals("local_model_unavailable", ignoreCase = true) -> BrainSessionType.LOCAL_LLM
            else -> null
        }
    }

    private fun confirmationTypeForBrainAction(brainAction: BrainAction): com.nova.luna.memory.PendingConfirmationType {
        return when {
            brainAction.intent.equals("online_ai_permission", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.ONLINE_AI_USE
            brainAction.intent.startsWith("cab", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.BOOK_RIDE
            brainAction.intent.startsWith("food", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.PLACE_ORDER
            brainAction.intent.startsWith("grocery", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.PLACE_ORDER
            brainAction.intent.startsWith("shopping", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.PLACE_ORDER
            brainAction.intent.startsWith("communication", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.SEND_MESSAGE
            brainAction.intent.startsWith("content", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.EXPORT_CONTENT
            brainAction.intent.startsWith("phone", ignoreCase = true) -> com.nova.luna.memory.PendingConfirmationType.CALL_CONTACT
            else -> com.nova.luna.memory.PendingConfirmationType.GENERIC_SAFE_ACTION
        }
    }
}
