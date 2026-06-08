package com.nova.luna.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.brain.AssistantContext
import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalLlmPhase7Test {

    private lateinit var context: Context
    private lateinit var promptBuilder: LocalLlmPromptBuilder
    private lateinit var outputParser: LocalLlmOutputParser

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        promptBuilder = LocalLlmPromptBuilder()
        outputParser = LocalLlmOutputParser()
    }

    @Test
    fun `promptBuilder contains strict JSON and no-control rules`() {
        val request = LocalLlmRequest(commandText = "open youtube")
        val prompt = promptBuilder.build(request)
        
        assertTrue(prompt.contains("strict JSON", ignoreCase = true))
        assertTrue(prompt.contains("DO NOT control the phone directly", ignoreCase = true))
        assertTrue(prompt.contains("requiresConfirmation", ignoreCase = true))
    }

    @Test
    fun `outputParser accepts valid JSON and maps to CommandIntent`() {
        val rawOutput = """
            {
              "type": "candidate_action",
              "domain": "MEDIA",
              "intent": "open_youtube",
              "confidence": 0.95,
              "requiresConfirmation": false,
              "riskLevel": "SAFE",
              "action": {
                "actionType": "LAUNCH_APP",
                "targetApp": "com.google.android.youtube"
              },
              "userMessage": "Opening YouTube"
            }
        """.trimIndent()
        
        val request = LocalLlmRequest(commandText = "open youtube")
        val result = outputParser.parse(rawOutput, request)
        
        assertEquals(LocalLlmStatus.READY, result.status)
        assertEquals(UnifiedDomain.MEDIA, result.parsedDomain)
        assertNotNull(result.parsedCandidateAction)
        assertEquals(ActionType.LAUNCH_APP, result.parsedCandidateAction?.actionType)
        assertEquals("com.google.android.youtube", result.parsedCandidateAction?.entities?.get("targetApp"))
    }

    @Test
    fun `outputParser rejects malformed JSON`() {
        val rawOutput = "This is not JSON"
        val request = LocalLlmRequest(commandText = "hello")
        val result = outputParser.parse(rawOutput, request)
        
        assertEquals(LocalLlmStatus.FAILED, result.status)
    }

    @Test
    fun `outputParser rejects unknown action type`() {
        val rawOutput = """{"action": {"actionType": "INVALID_ACTION"}}"""
        val request = LocalLlmRequest(commandText = "test")
        val result = outputParser.parse(rawOutput, request)
        
        assertEquals(ActionType.UNKNOWN, result.parsedCandidateAction?.actionType)
    }
}
