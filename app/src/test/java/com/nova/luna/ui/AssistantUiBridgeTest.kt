package com.nova.luna.ui

import android.content.Context
import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandBrain
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.CommandResult
import com.nova.luna.brain.CommandSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class AssistantUiBridgeTest {

    private lateinit var bridge: AssistantUiBridge
    private lateinit var assistantSession: AssistantSession
    private lateinit var commandBrain: CommandBrain
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        commandBrain = mock(CommandBrain::class.java)
        `when`(commandBrain.process(anyString())).thenReturn(CommandResult.success("Mock Success"))
        
        assistantSession = AssistantSession(commandBrain)
        bridge = AssistantUiBridge(context, assistantSession)
    }

    @Test
    fun `submitTextCommand updates state and completes`() {
        bridge.submitTextCommand("test command", "LUNA")
        val state = bridge.getAssistantState()
        // It should be COMPLETED because the mock execution is synchronous
        assertEquals(AssistantUiStatus.COMPLETED, state.status)
        assertEquals("test command", state.lastCommand)
    }

    @Test
    fun `onCommandResult updates state to COMPLETED on success`() {
        val result = CommandResult.success("Success message")
        // We don't need to call submitTextCommand here, we can just trigger the callback
        bridge.onCommandResult(result, CommandSource.TEXT)
        
        val state = bridge.getAssistantState()
        assertEquals(AssistantUiStatus.COMPLETED, state.status)
        assertNotNull(state.lastResult)
        assertEquals("Success message", state.lastResult?.resultMessage)
    }

    @Test
    fun `onCommandResult updates state to BLOCKED on safety block`() {
        val result = CommandResult.blocked("Blocked by safety")
        bridge.onCommandResult(result, CommandSource.TEXT)
        
        val state = bridge.getAssistantState()
        assertEquals(AssistantUiStatus.BLOCKED, state.status)
        assertEquals("Blocked by safety", state.lastResult?.resultMessage)
    }

    @Test
    fun `setPersonality updates state`() {
        bridge.setPersonality(AssistantPersonality.NOVA)
        assertEquals(AssistantPersonality.NOVA, bridge.getAssistantState().personality)
    }

    @Test
    fun `empty command returns error code`() {
        val result = bridge.submitTextCommand("", "LUNA")
        assertEquals("ERROR_EMPTY_COMMAND", result)
    }
}
