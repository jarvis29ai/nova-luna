package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmBrainProviderTest {
    private val codec = BrainActionJsonCodec()

    @Test
    fun `mock config selects deterministic fallback provider without calling ollama`() {
        val client = RecordingOllamaClient(responseBody = """{"response":"{}","done":true}""")

        val provider = BrainProviderFactory.create(
            config = BrainRuntimeConfig(
                brainProvider = "mock",
                ollamaBaseUrl = "http://127.0.0.1:11434",
                ollamaModel = "qwen2.5:3b",
                llmEnabled = false
            ),
            client = client
        )

        assertTrue(provider is LocalDeterministicBrainProvider)
        assertEquals(null, client.lastPrompt)
    }

    @Test
    fun `markdown wrapped json is cleaned safely`() {
        val expected = BrainAction(
            intent = "open_app",
            reply = "Opening WhatsApp.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "open whatsapp",
                "appName" to "whatsapp"
            )
        )
        val innerJson = codec.encode(expected)
        val client = RecordingOllamaClient(
            responseBody = """{"response":"```json\n${escapeJson(innerJson)}\n```","done":true}"""
        )

        val provider = LocalLlmBrainProvider(
            config = BrainRuntimeConfig(
                brainProvider = "ollama",
                ollamaBaseUrl = "http://127.0.0.1:11434",
                ollamaModel = "qwen2.5:3b",
                llmEnabled = true
            ),
            client = client
        )

        val result = provider.analyze(BrainRequest("open whatsapp"))

        assertEquals(innerJson, result)
    }

    @Test
    fun `ollama wrapper is unwrapped into brain action json`() {
        val expected = BrainAction(
            intent = "open_app",
            reply = "Opening WhatsApp.",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = true,
            params = mapOf(
                "rawText" to "open whatsapp",
                "appName" to "whatsapp"
            )
        )
        val innerJson = codec.encode(expected)
        val client = RecordingOllamaClient(
            responseBody = """{"response":"${escapeJson(innerJson)}","done":true}"""
        )

        val provider = LocalLlmBrainProvider(
            config = BrainRuntimeConfig(
                brainProvider = "ollama",
                ollamaBaseUrl = "http://127.0.0.1:11434",
                ollamaModel = "qwen2.5:3b",
                llmEnabled = true
            ),
            client = client
        )

        val result = provider.analyze(BrainRequest("open WhatsApp"))
        val prompt = client.lastPrompt ?: error("Expected prompt to be recorded.")

        assertEquals(innerJson, result)
        assertEquals("qwen2.5:3b", client.lastModel)
        assertTrue(prompt.contains("You are Nova/Luna's local phone brain."))
        assertTrue(prompt.contains("SafetyGate"))
        assertTrue(prompt.contains("send money"))
    }

    private class RecordingOllamaClient(
        private val responseBody: String
    ) : OllamaClient {
        var lastBaseUrl: String? = null
        var lastModel: String? = null
        var lastPrompt: String? = null

        override fun generate(baseUrl: String, model: String, prompt: String): String {
            lastBaseUrl = baseUrl
            lastModel = model
            lastPrompt = prompt
            return responseBody
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
