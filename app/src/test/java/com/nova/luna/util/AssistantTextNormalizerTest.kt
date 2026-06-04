package com.nova.luna.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantTextNormalizerTest {
    @Test
    fun `strip wake words removes leading assistant prefixes and punctuation`() {
        assertEquals("open WhatsApp", AssistantTextNormalizer.stripWakeWords("Luna, open WhatsApp"))
        assertEquals("open WhatsApp", AssistantTextNormalizer.stripWakeWords("Hey Nova: open WhatsApp"))
    }

    @Test
    fun `normalize strips wake words while preserving the rest of the request`() {
        assertEquals("open whatsapp", AssistantTextNormalizer.normalize("Luna, open WhatsApp!"))
        assertEquals("book a cab from current location to db mall", AssistantTextNormalizer.normalize("Hey Luna, book a cab from current location to DB Mall"))
    }

    @Test
    fun `strip wake words only removes prefixes at the start of the utterance`() {
        assertEquals("open WhatsApp for Luna", AssistantTextNormalizer.stripWakeWords("open WhatsApp for Luna"))
        assertEquals("what do you think of Luna", AssistantTextNormalizer.stripWakeWords("what do you think of Luna"))
    }
}
