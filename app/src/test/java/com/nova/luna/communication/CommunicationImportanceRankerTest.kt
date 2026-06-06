package com.nova.luna.communication

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CommunicationImportanceRankerTest {
    private val ranker = CommunicationImportanceRanker()

    @Test
    fun `test rank critical otp`() {
        val message = CommunicationMessage("1", CommunicationPlatform.SMS, CommunicationSender("Bank"), LocalDateTime.now(), "Your OTP is 1234")
        assertEquals(MessageImportance.CRITICAL, ranker.rank(message))
    }

    @Test
    fun `test rank important interview`() {
        val message = CommunicationMessage("1", CommunicationPlatform.GMAIL, CommunicationSender("HR"), LocalDateTime.now(), "Interview scheduled for tomorrow")
        assertEquals(MessageImportance.IMPORTANT, ranker.rank(message))
    }

    @Test
    fun `test rank normal message`() {
        val message = CommunicationMessage("1", CommunicationPlatform.WHATSAPP, CommunicationSender("Friend"), LocalDateTime.now(), "What's up?")
        assertEquals(MessageImportance.NORMAL, ranker.rank(message))
    }

    @Test
    fun `test rank promotional sale`() {
        val message = CommunicationMessage("1", CommunicationPlatform.SMS, CommunicationSender("Shop"), LocalDateTime.now(), "Big summer sale!")
        assertEquals(MessageImportance.PROMOTIONAL, ranker.rank(message))
    }

    @Test
    fun `test detect sensitivity otp`() {
        val message = CommunicationMessage("1", CommunicationPlatform.SMS, CommunicationSender("Bank"), LocalDateTime.now(), "Your OTP is 1234")
        assertTrue(ranker.isSensitive(message))
    }

    @Test
    fun `test detect sensitivity password`() {
        val message = CommunicationMessage("1", CommunicationPlatform.SMS, CommunicationSender("Admin"), LocalDateTime.now(), "Your password has been reset")
        assertTrue(ranker.isSensitive(message))
    }
}
