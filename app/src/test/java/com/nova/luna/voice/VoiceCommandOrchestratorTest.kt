package com.nova.luna.voice

import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandSource
import com.nova.luna.model.CommandResult
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.ActionType
import com.nova.luna.ui.AssistantPersonality
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.mock

class VoiceCommandOrchestratorTest {

    private lateinit var voiceInput: VoiceInputController
    private lateinit var voiceOutput: VoiceOutputController
    private lateinit var assistantSession: AssistantSession
    private lateinit var orchestrator: VoiceCommandOrchestrator

    @Before
    fun setup() {
        voiceInput = mock(VoiceInputController::class.java)
        voiceOutput = mock(VoiceOutputController::class.java)
        assistantSession = mock(AssistantSession::class.java)
        orchestrator = VoiceCommandOrchestrator(voiceInput, voiceOutput, assistantSession)
    }

    @Test
    fun `onFinalText sends valid command to brain`() {
        val result = VoiceInputResult(
            status = VoiceState.RECOGNIZED,
            rawTranscript = "Hey Luna open camera",
            cleanedCommand = "open camera",
            shouldSendToBrain = true
        )

        orchestrator.onFinalText(result)

        verify(assistantSession).executeCommand("open camera", CommandSource.VOICE)
    }

    @Test
    fun `onCommandResult with voice source triggers TTS`() {
        val commandResult = CommandResult(
            success = true,
            message = "I opened the camera.",
            status = ActionResultStatus.SUCCESS,
            actionType = ActionType.LAUNCH_APP,
            timestamp = System.currentTimeMillis()
        )

        orchestrator.onCommandResult(commandResult, CommandSource.VOICE)

        verify(voiceOutput).speak("I opened the camera.", AssistantPersonality.LUNA)
    }

    @Test
    fun `error in STT triggers error state`() {
        val listener = mock(VoiceCommandOrchestrator.Listener::class.java)
        orchestrator.setListener(listener)

        orchestrator.onError(VoiceError.NO_SPEECH_MATCH, "No speech")

        verify(listener).onVoiceStateChanged(VoiceState.ERROR)
        verify(listener).onVoiceError(VoiceError.NO_SPEECH_MATCH, "No speech")
    }
}
