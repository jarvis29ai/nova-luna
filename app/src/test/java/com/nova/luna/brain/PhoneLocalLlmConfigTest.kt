package com.nova.luna.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLocalLlmConfigTest {
    @Test
    fun `default model stack keeps gemma first and backups disabled`() {
        val stack = PhoneLocalLlmConfig.defaultModelStack()

        assertEquals(4, stack.size)
        assertEquals(PhoneLocalLlmModelId.GEMMA_3N, stack[0].id)
        assertTrue(stack[0].enabled)
        assertEquals(PhoneLocalLlmModelId.QWEN_3_SMALL, stack[1].id)
        assertFalse(stack[1].enabled)
        assertEquals(PhoneLocalLlmModelId.GEMMA_3_270M, stack[2].id)
        assertFalse(stack[2].enabled)
        assertEquals(PhoneLocalLlmModelId.PHI_4_MINI, stack[3].id)
        assertFalse(stack[3].enabled)
    }

    @Test
    fun `model ids resolve from wire values`() {
        assertEquals(PhoneLocalLlmModelId.GEMMA_3N, PhoneLocalLlmModelId.fromWireValue("gemma_3n"))
        assertEquals(PhoneLocalLlmModelId.QWEN_3_SMALL, PhoneLocalLlmModelId.fromWireValue("QWEN_3_SMALL"))
        assertEquals("Gemma 3n", PhoneLocalLlmModelId.GEMMA_3N.displayName)
        assertTrue(PhoneLocalLlmModelId.GEMMA_3N.priority < PhoneLocalLlmModelId.QWEN_3_SMALL.priority)
    }

    @Test
    fun `sanitized config trims paths and coerces limits`() {
        val config = PhoneLocalLlmConfig(
            enabled = true,
            maxInputTokens = 0,
            maxPromptChars = 0,
            timeoutMs = 0,
            models = listOf(
                PhoneLocalLlmModelConfig(
                    id = PhoneLocalLlmModelId.GEMMA_3N,
                    enabled = true,
                    assetPath = "  /tmp/model.gguf  ",
                    quantizedFileName = "  gemma.gguf  ",
                    minimumRamMb = 0,
                    maxInputTokens = 0,
                    maxPromptChars = 0,
                    timeoutMs = 0,
                    priority = 0
                )
            )
        )

        val sanitized = config.sanitized()

        assertTrue(sanitized.maxInputTokens >= 1)
        assertTrue(sanitized.maxPromptChars >= 1)
        assertTrue(sanitized.timeoutMs >= 1L)
        assertEquals("/tmp/model.gguf", sanitized.models.first().assetPath)
        assertEquals("gemma.gguf", sanitized.models.first().quantizedFileName)
    }
}
