package com.nova.luna.voice

import android.util.Log
import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandSource
import com.nova.luna.ui.AssistantPersonality
import com.nova.luna.model.CommandResult

class VoiceCommandOrchestrator(
    private val voiceInput: VoiceInputController,
    private val voiceOutput: VoiceOutputController,
    private val assistantSession: AssistantSession,
    private val normalizer: VoiceCommandNormalizer = VoiceCommandNormalizer()
) : VoiceInputController.Listener, VoiceOutputController.Listener, AssistantSession.SessionListener {

    interface Listener {
        fun onVoiceStateChanged(state: VoiceState)
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onVoiceError(error: VoiceError, message: String)
    }

    private var listener: Listener? = null
    private var currentPersonality = AssistantPersonality.LUNA
    private var lastSource = CommandSource.TEXT

    init {
        voiceInput.setListener(this)
        voiceOutput.setListener(this)
        assistantSession.addSessionListener(this)
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun startListening(personality: AssistantPersonality) {
        currentPersonality = personality
        voiceInput.startListening(personality)
    }

    fun stopListening() {
        voiceInput.stopListening()
    }

    fun cancelListening() {
        voiceInput.cancelListening()
    }

    // VoiceInputController.Listener
    override fun onListeningStarted() {
        listener?.onVoiceStateChanged(VoiceState.LISTENING)
    }

    override fun onPartialText(text: String) {
        listener?.onPartialText(text)
    }

    override fun onFinalText(result: VoiceInputResult) {
        listener?.onFinalText(result.rawTranscript)
        
        if (result.shouldSendToBrain) {
            lastSource = CommandSource.VOICE
            listener?.onVoiceStateChanged(VoiceState.COMMAND_RUNNING)
            // Send cleaned command to assistant session
            assistantSession.executeCommand(result.cleanedCommand, CommandSource.VOICE)
        } else if (result.rawTranscript.isNotBlank()) {
            // It had some text but was not considered valid (e.g. only wake word)
            listener?.onVoiceStateChanged(VoiceState.IDLE)
        } else {
            listener?.onVoiceStateChanged(VoiceState.ERROR)
            listener?.onVoiceError(VoiceError.NO_SPEECH_MATCH, "No command detected")
        }
    }

    override fun onError(error: VoiceError, message: String) {
        listener?.onVoiceStateChanged(VoiceState.ERROR)
        listener?.onVoiceError(error, message)
    }

    override fun onListeningStopped() {
        // State is usually updated via onStateChanged or onFinalText
    }

    override fun onStateChanged(state: VoiceState) {
        listener?.onVoiceStateChanged(state)
    }

    // VoiceOutputController.Listener
    override fun onSpeechStarted() {
        listener?.onVoiceStateChanged(VoiceState.SPEAKING)
    }

    override fun onSpeechCompleted() {
        listener?.onVoiceStateChanged(VoiceState.IDLE)
    }

    override fun onSpeechError(error: VoiceError, message: String) {
        Log.e("VoiceOrchestrator", "TTS Error: $message")
        listener?.onVoiceStateChanged(VoiceState.IDLE)
    }

    // AssistantSession.SessionListener
    override fun onCommandResult(result: CommandResult, source: CommandSource) {
        if (source == CommandSource.VOICE) {
            val reply = result.message
            if (reply.isNotBlank()) {
                voiceOutput.speak(reply, currentPersonality)
            } else {
                listener?.onVoiceStateChanged(VoiceState.IDLE)
            }
        }
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {}
    override fun onVoiceResponseRequested(message: String) {}
    override fun onThinkingStarted() {}
    override fun onActionStarted(label: String) {}
    override fun onConfirmationRequired(message: String, actionSummary: String?) {}
    override fun onVoiceInputStateChanged(state: com.nova.luna.voice.VoiceInputState) {}
    override fun onPartialTranscriptReceived(transcript: String) {}
    override fun onDomainRouted(domain: com.nova.luna.brain.UnifiedDomain) {}
    override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) {}
}
