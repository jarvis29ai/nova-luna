package com.nova.luna.brain

import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandResult
import com.nova.luna.voice.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class AssistantSessionVoiceFlowTest {

    private lateinit var session: AssistantSession
    private lateinit var commandBrain: CommandBrain
    private lateinit var templates: VoiceResponseTemplates
    private lateinit var listener: CapturingListener

    @Before
    fun setUp() {
        commandBrain = mock(CommandBrain::class.java)
        templates = VoiceResponseTemplates()
        listener = CapturingListener()
        session = AssistantSession(commandBrain, null, templates)
        session.addSessionListener(listener)
    }

    @Test
    fun `executeCommand with VOICE source triggers thinking and result speech`() {
        val command = "open camera"
        val result = CommandResult.success("Opening camera")
        `when`(commandBrain.process(command)).thenReturn(result)

        session.executeCommand(command, CommandSource.VOICE)

        assertEquals(
            listOf(
                templates.getMessage(VoiceResponseType.THINKING),
                templates.fromActionResultStatus(ActionResultStatus.SUCCESS, "Opening camera")
            ),
            listener.voiceMessages
        )
    }

    @Test
    fun `executeCommand with VOICE source handles blocked command with speech`() {
        val command = "pay someone"
        val result = CommandResult.blocked("Blocked by safety")
        `when`(commandBrain.process(command)).thenReturn(result)

        session.executeCommand(command, CommandSource.VOICE)

        assertEquals(
            listOf(
                templates.getMessage(VoiceResponseType.THINKING),
                templates.fromActionResultStatus(ActionResultStatus.BLOCKED, "Blocked by safety")
            ),
            listener.voiceMessages
        )
    }

    @Test
    fun `confirmPendingAction for voice command executes yes`() {
        `when`(commandBrain.process("yes")).thenReturn(CommandResult.success("Confirmed"))
        
        session.confirmPendingAction()
        
        verify(commandBrain).process("yes")
    }

    private class CapturingListener : AssistantSession.SessionListener {
        val voiceMessages = mutableListOf<String>()

        override fun onCommandResult(result: CommandResult, source: CommandSource) = Unit
        override fun onSpeakingStateChanged(isSpeaking: Boolean) = Unit
        override fun onVoiceResponseRequested(message: String) {
            voiceMessages.add(message)
        }
        override fun onThinkingStarted() = Unit
        override fun onActionStarted(label: String) = Unit
        override fun onConfirmationRequired(message: String, actionSummary: String?) = Unit
        override fun onVoiceInputStateChanged(state: VoiceInputState) = Unit
        override fun onPartialTranscriptReceived(transcript: String) = Unit
        override fun onDomainRouted(domain: UnifiedDomain) = Unit
        override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) = Unit
    }
}
