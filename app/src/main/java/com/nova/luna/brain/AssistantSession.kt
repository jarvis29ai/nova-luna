package com.nova.luna.brain

import com.nova.luna.model.CommandResult
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.ActionType
import com.nova.luna.voice.*

enum class CommandSource {
    TEXT,
    VOICE
}

class AssistantSession(
    private val commandBrain: CommandBrain,
    private val responseManager: VoiceResponseManager? = null,
    private val templates: VoiceResponseTemplates = VoiceResponseTemplates(),
    private val memoryManager: com.nova.luna.memory.PersonalMemoryManager? = null
) {
    interface SessionListener {
        fun onCommandResult(result: CommandResult, source: CommandSource)
        fun onSpeakingStateChanged(isSpeaking: Boolean)
        fun onVoiceResponseRequested(message: String)
        fun onThinkingStarted()
        fun onActionStarted(label: String)
        fun onConfirmationRequired(message: String, actionSummary: String? = null)
        fun onVoiceInputStateChanged(state: VoiceInputState)
        fun onPartialTranscriptReceived(transcript: String)
        fun onDomainRouted(domain: UnifiedDomain)
        fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult)
    }

    private val listeners = mutableListOf<SessionListener>()
    private var isWaitingForMemoryConfirmation = false

    fun addSessionListener(listener: SessionListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeSessionListener(listener: SessionListener) {
        listeners.remove(listener)
    }

    fun setSessionListener(listener: SessionListener) {
        listeners.clear()
        listeners.add(listener)
    }

    fun executeCommand(text: String, source: CommandSource) {
        if (source == CommandSource.VOICE) {
            val message = templates.getMessage(VoiceResponseType.THINKING)
            listeners.forEach { it.onVoiceResponseRequested(message) }
            responseManager?.speak(VoiceResponseRequest(
                type = VoiceResponseType.THINKING,
                message = message
            ))
        }
        
        listeners.forEach { it.onThinkingStarted() }

        // Phase 8: Check for Memory Intent
        val memoryResult = memoryManager?.handleMemoryCommand(text)
        if (memoryResult != null && memoryResult.status != com.nova.luna.memory.MemoryPermissionStatus.NOT_FOUND) {
            handleMemoryResult(memoryResult, source)
            return
        }

        val result = commandBrain.process(text)
        
        if (result.domain != UnifiedDomain.UNKNOWN) {
            listeners.forEach { it.onDomainRouted(result.domain) }
        }

        if (result.status == ActionResultStatus.NEEDS_CONFIRMATION) {
            listeners.forEach { it.onConfirmationRequired(result.message) }
        } else if (result.success && result.actionType != ActionType.UNKNOWN) {
            listeners.forEach { it.onActionStarted(result.message) }
        }
        
        if (source == CommandSource.VOICE) {
            val voiceMessage = templates.fromActionResultStatus(result.status, result.message)
            listeners.forEach { it.onVoiceResponseRequested(voiceMessage) }
            responseManager?.speak(VoiceResponseRequest(
                type = when(result.status) {
                    ActionResultStatus.SUCCESS -> VoiceResponseType.SUCCESS
                    ActionResultStatus.FAILED -> VoiceResponseType.FAILURE
                    ActionResultStatus.BLOCKED -> VoiceResponseType.BLOCKED
                    ActionResultStatus.NEEDS_CONFIRMATION -> VoiceResponseType.CONFIRMATION
                    ActionResultStatus.PERMISSION_REQUIRED -> VoiceResponseType.PERMISSION_REQUIRED
                    else -> VoiceResponseType.SUMMARY
                },
                message = voiceMessage,
                relatedResultStatus = result.status,
                relatedActionType = result.actionType,
                priority = if (result.status == ActionResultStatus.NEEDS_CONFIRMATION || result.status == ActionResultStatus.BLOCKED) VoiceResponsePriority.HIGH else VoiceResponsePriority.NORMAL
            )) {
                listeners.forEach { it.onSpeakingStateChanged(false) }
            }
            listeners.forEach { it.onSpeakingStateChanged(true) }
        }

        listeners.forEach { it.onCommandResult(result, source) }
    }

    private fun handleMemoryResult(result: com.nova.luna.memory.MemoryOperationResult, source: CommandSource) {
        if (result.status == com.nova.luna.memory.MemoryPermissionStatus.NEEDS_CONFIRMATION) {
            isWaitingForMemoryConfirmation = true
            listeners.forEach { it.onConfirmationRequired(result.userMessage ?: "Confirm memory operation?") }
        } else {
            val message = result.userMessage ?: "Operation complete."
            if (source == CommandSource.VOICE) {
                listeners.forEach { it.onVoiceResponseRequested(message) }
                responseManager?.speak(VoiceResponseRequest(
                    type = if (result.status == com.nova.luna.memory.MemoryPermissionStatus.ALLOWED) VoiceResponseType.SUCCESS else VoiceResponseType.BLOCKED,
                    message = message
                ))
            }
        }
        listeners.forEach { it.onMemoryOperationComplete(result) }
    }

    fun onVoiceInputStarted() {
        val message = templates.getMessage(VoiceResponseType.LISTENING)
        listeners.forEach { it.onVoiceResponseRequested(message) }
        responseManager?.speak(VoiceResponseRequest(
            type = VoiceResponseType.LISTENING,
            message = message
        ))
    }

    fun notifyVoiceInputStateChanged(state: VoiceInputState) {
        listeners.forEach { it.onVoiceInputStateChanged(state) }
    }

    fun notifyPartialTranscriptReceived(transcript: String) {
        listeners.forEach { it.onPartialTranscriptReceived(transcript) }
    }

    fun confirmPendingAction() {
        if (isWaitingForMemoryConfirmation) {
            val result = memoryManager?.confirmPendingMemorySave()
            isWaitingForMemoryConfirmation = false
            if (result != null) {
                handleMemoryResult(result, CommandSource.VOICE)
            }
        } else {
            // Re-execute last command with confirmation context
            executeCommand("yes", CommandSource.VOICE)
        }
    }

    fun cancelPendingAction() {
        if (isWaitingForMemoryConfirmation) {
            memoryManager?.cancelPendingMemorySave()
            isWaitingForMemoryConfirmation = false
            val message = "Okay, I cancelled it."
            listeners.forEach { it.onVoiceResponseRequested(message) }
            responseManager?.speak(VoiceResponseRequest(
                type = VoiceResponseType.SUCCESS,
                message = message
            ))
        } else {
            executeCommand("cancel", CommandSource.VOICE)
        }
    }
}
