package com.nova.luna.ui

import com.nova.luna.model.*
import com.nova.luna.voice.VoiceInputState
import org.junit.Assert.*
import org.junit.Test

class AssistantPopupStateMapperTest {

    private val mapper = AssistantPopupStateMapper()

    @Test
    fun `mapFromVoiceInput LISTENING maps to popup LISTENING`() {
        val model = mapper.mapFromVoiceInput(VoiceInputState.LISTENING, "hello")
        assertEquals(AssistantPopupState.LISTENING, model.state)
        assertEquals("hello", model.transcript)
        assertTrue(model.showTranscript)
    }

    @Test
    fun `mapFromVoiceInput PROCESSING maps to popup THINKING`() {
        val model = mapper.mapFromVoiceInput(VoiceInputState.PROCESSING)
        assertEquals(AssistantPopupState.THINKING, model.state)
        assertTrue(model.showLoader)
    }

    @Test
    fun `mapFromCommandResult SUCCESS maps to popup COMPLETED`() {
        val result = CommandResult.success("Done", actionType = ActionType.OPEN_APP)
        val model = mapper.mapFromCommandResult(result)
        assertEquals(AssistantPopupState.COMPLETED, model.state)
        assertEquals("Done", model.resultSummary)
        assertTrue(model.showResultSummary)
    }

    @Test
    fun `mapFromCommandResult NEEDS_CONFIRMATION maps to popup NEED_CONFIRMATION`() {
        val result = CommandResult.confirmationRequired("This will order pizza", actionType = ActionType.FOOD_ORDER)
        val model = mapper.mapFromCommandResult(result)
        assertEquals(AssistantPopupState.NEED_CONFIRMATION, model.state)
        assertEquals("Confirm Action", model.confirmationTitle)
        assertEquals("This will order pizza", model.confirmationMessage)
        assertTrue(model.showContinueButton)
        assertTrue(model.showCancelButton)
    }

    @Test
    fun `mapFromCommandResult BLOCKED maps to popup BLOCKED`() {
        val result = CommandResult.blocked("I cannot do that safely")
        val model = mapper.mapFromCommandResult(result)
        assertEquals(AssistantPopupState.BLOCKED, model.state)
        assertEquals("I cannot do that safely", model.blockedReason)
        assertTrue(model.showSafetyWarning)
    }

    @Test
    fun `mapFromCommandResult NOT_FOUND maps to popup FAILED`() {
        val result = CommandResult.failure("Button not found", status = ActionResultStatus.NOT_FOUND)
        val model = mapper.mapFromCommandResult(result)
        assertEquals(AssistantPopupState.FAILED, model.state)
        assertEquals("Button not found", model.errorMessage)
    }
}
