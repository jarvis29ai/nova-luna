package com.nova.luna.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import com.nova.luna.R
import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandSource
import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.model.CommandResult

class AssistantPopupController(
    private val container: ViewGroup,
    private val assistantSession: AssistantSession,
    private val stateMapper: AssistantPopupStateMapper = AssistantPopupStateMapper(),
    private val onEvent: (AssistantPopupEvent) -> Unit
) : AssistantSession.SessionListener {

    private val root: View = LayoutInflater.from(container.context).inflate(R.layout.assistant_popup, container, false)
    private val orb: View = root.findViewById(R.id.assistantOrb)
    private val panel: CardView = root.findViewById(R.id.assistantPanel)
    private val titleText: TextView = root.findViewById(R.id.popupTitle)
    private val subtitleText: TextView = root.findViewById(R.id.popupSubtitle)
    private val loader: ProgressBar = root.findViewById(R.id.popupLoader)
    private val transcriptText: TextView = root.findViewById(R.id.popupTranscript)
    private val confirmationBox: LinearLayout = root.findViewById(R.id.confirmationBox)
    private val confirmationTitle: TextView = root.findViewById(R.id.confirmationTitle)
    private val confirmationMessage: TextView = root.findViewById(R.id.confirmationMessage)
    private val btnCancel: Button = root.findViewById(R.id.btnCancel)
    private val btnContinue: Button = root.findViewById(R.id.btnContinue)
    private val resultSummaryText: TextView = root.findViewById(R.id.resultSummary)
    private val errorText: TextView = root.findViewById(R.id.errorText)
    private val micButton: ImageButton = root.findViewById(R.id.popupMicButton)
    
    // Phase 26 New UI Elements
    private val btnLuna: Button = root.findViewById(R.id.btnLuna)
    private val btnNova: Button = root.findViewById(R.id.btnNova)
    private val commandInput: EditText = root.findViewById(R.id.commandInput)
    private val btnSendCommand: ImageButton = root.findViewById(R.id.btnSendCommand)

    private var currentModel = AssistantPopupUiModel()

    init {
        container.addView(root)
        setupListeners()
        updateUi(AssistantPopupUiModel(state = AssistantPopupState.IDLE))
    }

    private fun setupListeners() {
        orb.setOnClickListener {
            handleEvent(AssistantPopupEvent.MIC_TAPPED)
        }
        micButton.setOnClickListener {
            handleEvent(AssistantPopupEvent.MIC_TAPPED)
        }
        btnCancel.setOnClickListener {
            handleEvent(AssistantPopupEvent.CANCEL_TAPPED)
        }
        btnContinue.setOnClickListener {
            handleEvent(AssistantPopupEvent.CONTINUE_TAPPED)
        }
        btnLuna.setOnClickListener {
            handlePersonalityChange(AssistantPersonality.LUNA)
        }
        btnNova.setOnClickListener {
            handlePersonalityChange(AssistantPersonality.NOVA)
        }
        btnSendCommand.setOnClickListener {
            val text = commandInput.text.toString()
            if (text.isNotBlank()) {
                commandInput.setText("")
                handleTextCommand(text)
            }
        }
    }

    private fun handlePersonalityChange(personality: AssistantPersonality) {
        currentModel = currentModel.copy(personality = personality)
        updateUi(currentModel)
        onEvent(AssistantPopupEvent.PERSONALITY_CHANGED)
    }

    private fun handleTextCommand(text: String) {
        // Mock Response Logic for Phase 26
        val isSensitive = text.contains("payment", ignoreCase = true) || 
                          text.contains("OTP", ignoreCase = true) || 
                          text.contains("bank", ignoreCase = true) ||
                          text.contains("private", ignoreCase = true)

        val thinkingText = if (currentModel.personality == AssistantPersonality.LUNA) "Processing softly..." else "Analyzing request..."

        updateUi(currentModel.copy(
            state = AssistantPopupState.THINKING,
            showLoader = true,
            title = if (currentModel.personality == AssistantPersonality.LUNA) "Luna" else "Nova",
            subtitle = thinkingText,
            transcript = text,
            showTranscript = true,
            showResultSummary = false,
            errorMessage = null
        ))

        // Simulate thinking and action
        root.postDelayed({
            if (isSensitive) {
                updateUi(currentModel.copy(
                    state = AssistantPopupState.LOCK_REQUIRED,
                    showLoader = false,
                    subtitle = "Action Blocked",
                    errorMessage = "This action needs unlock. Privacy/Security required.",
                    showResultSummary = false
                ))
            } else {
                val successMessage = if (currentModel.personality == AssistantPersonality.LUNA) 
                    "I've prepared that for you. Real executor will connect later."
                else 
                    "Task ready. Connection to real executor pending in future phase."

                updateUi(currentModel.copy(
                    state = AssistantPopupState.ACTION_READY,
                    showLoader = false,
                    subtitle = "Ready to assist",
                    resultSummary = successMessage,
                    showResultSummary = true
                ))
            }
        }, 1500)
    }

    fun handleEvent(event: AssistantPopupEvent) {
        onEvent(event)
        when (event) {
            AssistantPopupEvent.MIC_TAPPED -> {
                val listeningSubtitle = if (currentModel.personality == AssistantPersonality.LUNA) "Listening softly..." else "Listening..."
                updateUi(currentModel.copy(
                    state = AssistantPopupState.LISTENING, 
                    subtitle = listeningSubtitle,
                    showLoader = false,
                    showTranscript = false,
                    showResultSummary = false,
                    errorMessage = null
                ))
                
                // Mock voice detection after 2 seconds
                root.postDelayed({
                    if (currentModel.state == AssistantPopupState.LISTENING) {
                        handleTextCommand("Play some music")
                    }
                }, 2000)
            }
            AssistantPopupEvent.CANCEL_TAPPED -> {
                updateUi(AssistantPopupUiModel(state = AssistantPopupState.IDLE, personality = currentModel.personality))
            }
            AssistantPopupEvent.CONTINUE_TAPPED -> {
                val successText = if (currentModel.personality == AssistantPersonality.LUNA) 
                    "Action confirmed. I'll handle that gracefully."
                else 
                    "Action confirmed. Proceeding with confidence."

                updateUi(currentModel.copy(
                    state = AssistantPopupState.SUCCESS, 
                    resultSummary = successText,
                    showResultSummary = true
                ))
                
                // Close after success
                root.postDelayed({
                    if (currentModel.state == AssistantPopupState.SUCCESS) {
                        updateUi(AssistantPopupUiModel(state = AssistantPopupState.IDLE, personality = currentModel.personality))
                    }
                }, 3000)
            }
            AssistantPopupEvent.WAKE_LUNA_MOCK -> {
                handlePersonalityChange(AssistantPersonality.LUNA)
                triggerWake()
            }
            AssistantPopupEvent.WAKE_NOVA_MOCK -> {
                handlePersonalityChange(AssistantPersonality.NOVA)
                triggerWake()
            }
            else -> {}
        }
    }

    private fun triggerWake() {
        val wakeSubtitle = if (currentModel.personality == AssistantPersonality.LUNA) "I'm here. How can I help?" else "Nova active. Speak your command."
        updateUi(currentModel.copy(
            state = AssistantPopupState.WAKE_DETECTED,
            subtitle = wakeSubtitle,
            showLoader = false,
            showResultSummary = false
        ))
    }

    fun updateUi(model: AssistantPopupUiModel) {
        currentModel = model
        
        // Update Personality UI
        if (model.personality == AssistantPersonality.LUNA) {
            btnLuna.setTextColor(root.context.getColor(R.color.nova_pink))
            btnNova.setTextColor(root.context.getColor(R.color.nova_muted_text))
            titleText.setTextColor(root.context.getColor(R.color.nova_pink))
            titleText.text = "Luna"
            panel.setCardBackgroundColor(root.context.getColor(R.color.nova_deep_purple))
        } else {
            btnNova.setTextColor(root.context.getColor(R.color.nova_purple))
            btnLuna.setTextColor(root.context.getColor(R.color.nova_muted_text))
            titleText.setTextColor(root.context.getColor(R.color.nova_lavender))
            titleText.text = "Nova"
            panel.setCardBackgroundColor(root.context.getColor(R.color.nova_card_dark))
        }

        when (model.state) {
            AssistantPopupState.IDLE -> {
                orb.visibility = View.VISIBLE
                panel.visibility = View.GONE
                orb.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
            AssistantPopupState.HIDDEN -> {
                orb.visibility = View.GONE
                panel.visibility = View.GONE
            }
            else -> {
                orb.visibility = View.GONE
                panel.visibility = View.VISIBLE
                if (panel.alpha == 0f) {
                    panel.alpha = 0f
                    panel.animate().alpha(1.0f).setDuration(300).start()
                }
            }
        }

        titleText.text = model.title ?: (if (model.personality == AssistantPersonality.LUNA) "Luna" else "Nova")
        
        // Mock Voice Profile Label
        val voiceLabel = if (model.personality == AssistantPersonality.LUNA) " (Female Voice)" else " (Male Voice)"
        titleText.append(voiceLabel)

        subtitleText.text = model.subtitle
        subtitleText.visibility = if (model.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        
        loader.visibility = if (model.showLoader) View.VISIBLE else View.GONE
        
        transcriptText.text = model.transcript
        transcriptText.visibility = if (model.showTranscript && !model.transcript.isNullOrBlank()) View.VISIBLE else View.GONE
        
        if (model.state == AssistantPopupState.NEED_CONFIRMATION || 
            model.state == AssistantPopupState.CONFIRMATION_REQUIRED ||
            model.state == AssistantPopupState.LOCK_REQUIRED) {
            
            confirmationBox.visibility = View.VISIBLE
            confirmationTitle.text = if (model.state == AssistantPopupState.LOCK_REQUIRED) "Unlock Required" else (model.confirmationTitle ?: "Confirm Action")
            confirmationMessage.text = if (model.state == AssistantPopupState.LOCK_REQUIRED) "Please unlock your phone to proceed with this sensitive action." else model.confirmationMessage
            btnContinue.text = if (model.state == AssistantPopupState.LOCK_REQUIRED) "Unlock (Mock)" else (model.primaryButtonText ?: "Continue")
            btnCancel.text = model.secondaryButtonText ?: "Cancel"
        } else {
            confirmationBox.visibility = View.GONE
        }

        resultSummaryText.text = model.resultSummary
        resultSummaryText.visibility = if (model.showResultSummary && !model.resultSummary.isNullOrBlank()) View.VISIBLE else View.GONE
        
        errorText.text = model.errorMessage
        errorText.visibility = if (!model.errorMessage.isNullOrBlank()) View.VISIBLE else View.GONE
        
        micButton.visibility = if (model.showMicButton) View.VISIBLE else View.GONE
        
        // Add listening animation mock
        if (model.state == AssistantPopupState.LISTENING) {
            micButton.animate().scaleX(1.2f).scaleY(1.2f).setDuration(500).withEndAction {
                micButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(500).start()
            }.start()
        }
        
        // Safety: Clear error after 5 seconds if in completed/idle state
        if (model.state == AssistantPopupState.COMPLETED || model.state == AssistantPopupState.SUCCESS || model.state == AssistantPopupState.IDLE) {
            if (!model.errorMessage.isNullOrBlank()) {
                root.postDelayed({
                    if (currentModel.state == model.state) {
                         errorText.visibility = View.GONE
                    }
                }, 5000)
            }
        }
    }

    // SessionListener Implementation
    override fun onCommandResult(result: CommandResult, source: CommandSource) {
        updateUi(stateMapper.mapFromCommandResult(result))
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {
        // Optional: Update UI to show speaking state
    }

    override fun onVoiceResponseRequested(message: String) {
        // Optional: Show spoken text in popup
    }

    override fun onThinkingStarted() {
        updateUi(AssistantPopupUiModel(state = AssistantPopupState.THINKING, showLoader = true, title = "Thinking"))
    }

    override fun onActionStarted(label: String) {
        updateUi(AssistantPopupUiModel(state = AssistantPopupState.DOING_ACTION, title = "Hands On", subtitle = label, showLoader = true))
    }

    override fun onConfirmationRequired(message: String, actionSummary: String?) {
        updateUi(AssistantPopupUiModel(
            state = AssistantPopupState.NEED_CONFIRMATION,
            confirmationTitle = "Confirm Action",
            confirmationMessage = message,
            confirmationActionSummary = actionSummary,
            showContinueButton = true,
            showCancelButton = true
        ))
    }

    override fun onVoiceInputStateChanged(state: com.nova.luna.voice.VoiceInputState) {
        updateUi(stateMapper.mapFromVoiceInput(state))
    }

    override fun onPartialTranscriptReceived(transcript: String) {
        updateUi(stateMapper.mapFromVoiceInput(com.nova.luna.voice.VoiceInputState.LISTENING, transcript))
    }

    override fun onDomainRouted(domain: UnifiedDomain) {
        updateUi(currentModel.copy(subtitle = "Domain: ${domain.name}"))
    }

    override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) {
        if (result.status == com.nova.luna.memory.MemoryPermissionStatus.NEEDS_CONFIRMATION) {
            updateUi(AssistantPopupUiModel(
                state = AssistantPopupState.NEED_CONFIRMATION,
                confirmationTitle = "Memory Preference",
                confirmationMessage = result.userMessage ?: "Should I remember this?",
                showContinueButton = true,
                showCancelButton = true
            ))
        } else {
            updateUi(AssistantPopupUiModel(
                state = AssistantPopupState.COMPLETED,
                resultSummary = result.userMessage,
                showResultSummary = true
            ))
        }
    }
}
