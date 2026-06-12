package com.nova.luna.ui

import android.content.Context
import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandSource
import com.nova.luna.model.CommandResult
import com.nova.luna.voice.VoiceInputState
import org.json.JSONObject
import java.util.UUID

class AssistantUiBridge(
    private val context: Context,
    private val assistantSession: AssistantSession
) : AssistantSession.SessionListener {

    private var currentPersonality = AssistantPersonality.LUNA
    private var currentState = AssistantUiState(personality = currentPersonality)
    private val commandHistory = mutableListOf<AssistantUiResult>()
    
    // Callback for Flutter (to be set by MethodChannel handler)
    var onStateChanged: ((AssistantUiState) -> Unit)? = null
    var onResultReceived: ((AssistantUiResult) -> Unit)? = null

    init {
        assistantSession.addSessionListener(this)
    }

    fun submitTextCommand(command: String, personalityStr: String): String {
        val personality = try {
            AssistantPersonality.valueOf(personalityStr.uppercase())
        } catch (e: Exception) {
            AssistantPersonality.LUNA
        }
        
        setPersonality(personality)
        
        if (command.isBlank()) {
            return "ERROR_EMPTY_COMMAND"
        }

        val requestId = UUID.randomUUID().toString()
        updateState(currentState.copy(
            status = AssistantUiStatus.RUNNING,
            progressMessage = "Thinking...",
            lastCommand = command
        ))
        
        assistantSession.executeCommand(command, CommandSource.TEXT)
        return requestId
    }

    fun setPersonality(personality: AssistantPersonality) {
        if (currentPersonality != personality) {
            currentPersonality = personality
            updateState(currentState.copy(personality = personality))
        }
    }

    fun getAssistantState(): AssistantUiState {
        return currentState
    }

    fun getCommandHistory(): List<AssistantUiResult> {
        return commandHistory.toList()
    }

    fun getPhase26Diagnostics(): Map<String, Any> {
        return mapOf(
            "phase" to 26,
            "ui_layer_available" to true,
            "personality_switch_available" to true,
            "text_input_available" to true,
            "voice_button_placeholder_available" to true,
            "result_summary_available" to true,
            "progress_status_available" to true,
            "command_history_available" to true,
            "kotlin_bridge_available" to true,
            "brain_runtime_connected" to true,
            "safety_result_display_supported" to true,
            "current_personality" to currentPersonality.name,
            "history_count" to commandHistory.size
        )
    }

    private fun updateState(newState: AssistantUiState) {
        currentState = newState
        onStateChanged?.invoke(newState)
    }

    override fun onCommandResult(result: CommandResult, source: CommandSource) {
        val uiResult = result.toAssistantUiResult(
            requestId = UUID.randomUUID().toString(), // Ideally tracked from submit
            personality = currentPersonality,
            commandText = currentState.lastCommand ?: ""
        )
        
        synchronized(commandHistory) {
            commandHistory.add(0, uiResult)
            if (commandHistory.size > 10) {
                commandHistory.removeAt(commandHistory.size - 1)
            }
        }

        updateState(currentState.copy(
            status = uiResult.status,
            progressMessage = null,
            lastResult = uiResult
        ))
        onResultReceived?.invoke(uiResult)
    }

    override fun onThinkingStarted() {
        updateState(currentState.copy(
            status = AssistantUiStatus.RUNNING,
            progressMessage = "Thinking..."
        ))
    }

    override fun onActionStarted(label: String) {
        updateState(currentState.copy(
            status = AssistantUiStatus.RUNNING,
            progressMessage = "Acting: $label"
        ))
    }

    override fun onConfirmationRequired(message: String, actionSummary: String?) {
        updateState(currentState.copy(
            status = AssistantUiStatus.NEEDS_CONFIRMATION,
            progressMessage = message
        ))
    }

    override fun onVoiceInputStateChanged(state: VoiceInputState) {
        // Not used for text commands, but could update UI status
    }

    override fun onPartialTranscriptReceived(transcript: String) {
        // Not used for text commands
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {}
    override fun onVoiceResponseRequested(message: String) {}
    override fun onDomainRouted(domain: com.nova.luna.brain.UnifiedDomain) {}
    override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) {}
}
