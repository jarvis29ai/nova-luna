package com.nova.luna.communication

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CommunicationSummarizerTest {
    private val summarizer = CommunicationSummarizer()

    @Test
    fun `test summarize all with no messages`() {
        val summary = summarizer.summarizeAll(emptyList())
        assertEquals("No messages found for today.", summary.overallSummary)
        assertTrue(summary.platformSummaries.isEmpty())
    }

    @Test
    fun `test summarize sensitive message redaction`() {
        val messages = listOf(
            CommunicationMessage("1", CommunicationPlatform.SMS, CommunicationSender("Bank"), LocalDateTime.now(), "Your OTP is 1234", isSensitive = true)
        )
        val platformSummary = summarizer.summarizePlatform(CommunicationPlatform.SMS, messages)
        val senderSummary = platformSummary.senderSummaries.first()
        assertEquals("[Sensitive content hidden]", senderSummary.latestSummary)
    }
}
