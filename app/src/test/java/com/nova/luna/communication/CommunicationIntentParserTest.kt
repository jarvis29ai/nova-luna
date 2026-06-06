package com.nova.luna.communication

import org.junit.Assert.*
import org.junit.Test

class CommunicationIntentParserTest {
    private val parser = CommunicationIntentParser()

    @Test
    fun `test parse summarize all today`() {
        val request = parser.parse("summarize all today's messages")
        assertEquals(CommunicationCommandType.SUMMARIZE_ALL_TODAY, request.commandType)
    }

    @Test
    fun `test parse summarize my messages`() {
        val request = parser.parse("luna summarize my messages")
        assertEquals(CommunicationCommandType.SUMMARIZE_ALL_TODAY, request.commandType)
    }

    @Test
    fun `test parse summarize whatsapp`() {
        val request = parser.parse("nova summarize whatsapp")
        assertEquals(CommunicationCommandType.SUMMARIZE_ONE_PLATFORM, request.commandType)
        assertEquals(CommunicationPlatform.WHATSAPP, request.targetPlatform)
    }

    @Test
    fun `test parse search about meeting`() {
        val request = parser.parse("search messages from rahul about meeting")
        assertEquals(CommunicationCommandType.FIND_MESSAGE, request.commandType)
        assertTrue(request.rawText.contains("meeting"))
    }

    @Test
    fun `test parse draft formal reply`() {
        val request = parser.parse("draft a formal reply to this email")
        assertEquals(CommunicationCommandType.DRAFT_EMAIL, request.commandType)
        assertEquals(DraftTone.FORMAL, request.tone)
    }

    @Test
    fun `test parse send it`() {
        val request = parser.parse("send it")
        assertEquals(CommunicationCommandType.SEND_REPLY, request.commandType)
    }

    @Test
    fun `test parser does not steal unrelated commands`() {
        val unrelated = listOf(
            "book a cab to db mall",
            "order pizza",
            "go home",
            "open settings",
            "tap on login"
        )
        for (cmd in unrelated) {
            val request = parser.parse(cmd)
            assertEquals("Failed on: $cmd", CommunicationCommandType.UNKNOWN, request.commandType)
        }
    }
}
