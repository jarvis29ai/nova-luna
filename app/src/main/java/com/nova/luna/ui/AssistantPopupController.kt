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
    }

    fun handleEvent(event: AssistantPopupEvent) {
        onEvent(event)
        when (event) {
            AssistantPopupEvent.MIC_TAPPED -> {
                assistantSession.onVoiceInputStarted()
            }
            AssistantPopupEvent.CANCEL_TAPPED -> {
                assistantSession.cancelPendingAction()
                updateUi(AssistantPopupUiModel(state = AssistantPopupState.IDLE))
            }
            AssistantPopupEvent.CONTINUE_TAPPED -> {
                assistantSession.confirmPendingAction()
            }
            else -> {}
        }
    }

    fun updateUi(model: AssistantPopupUiModel) {
        currentModel = model
        
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

        titleText.text = model.title ?: "Luna"
        subtitleText.text = model.subtitle
        subtitleText.visibility = if (model.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        
        loader.visibility = if (model.showLoader) View.VISIBLE else View.GONE
        
        transcriptText.text = model.transcript
        transcriptText.visibility = if (model.showTranscript && !model.transcript.isNullOrBlank()) View.VISIBLE else View.GONE
        
        if (model.state == AssistantPopupState.NEED_CONFIRMATION) {
            confirmationBox.visibility = View.VISIBLE
            confirmationTitle.text = model.confirmationTitle ?: "Confirm Action"
            confirmationMessage.text = model.confirmationMessage
            btnContinue.text = model.primaryButtonText ?: "Continue"
            btnCancel.text = model.secondaryButtonText ?: "Cancel"
        } else {
            confirmationBox.visibility = View.GONE
        }

        resultSummaryText.text = model.resultSummary
        resultSummaryText.visibility = if (model.showResultSummary && !model.resultSummary.isNullOrBlank()) View.VISIBLE else View.GONE
        
        errorText.text = model.errorMessage
        errorText.visibility = if (!model.errorMessage.isNullOrBlank()) View.VISIBLE else View.GONE
        
        micButton.visibility = if (model.showMicButton) View.VISIBLE else View.GONE
        
        // Safety: Clear error after 5 seconds if in completed/idle state
        if (model.state == AssistantPopupState.COMPLETED || model.state == AssistantPopupState.IDLE) {
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
