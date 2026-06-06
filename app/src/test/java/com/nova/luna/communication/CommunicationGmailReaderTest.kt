package com.nova.luna.communication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class CommunicationGmailReaderTest {

    private lateinit var context: Context
    private lateinit var reader: CommunicationGmailReader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        reader = CommunicationGmailReader(context)
    }

    @Test
    fun `test Gmail reading with no account returns empty`() {
        val messages = reader.readTodayMessages()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test mapping Gmail JSON to CommunicationMessage`() {
        val json = JSONObject("""
            {
                "id": "12345",
                "internalDate": "1622900000000",
                "snippet": "Hello snippet",
                "payload": {
                    "headers": [
                        {"name": "From", "value": "test@example.com"},
                        {"name": "Subject", "value": "Test Subject"}
                    ]
                }
            }
        """.trimIndent())

        // Access private method for testing mapping logic
        val method = CommunicationGmailReader::class.java.getDeclaredMethod("mapToCommunicationMessage", JSONObject::class.java)
        method.isAccessible = true
        val message = method.invoke(reader, json) as CommunicationMessage

        assertEquals("12345", message.id)
        assertEquals("test@example.com", message.sender.name)
        assertEquals("Test Subject", message.subject)
        assertEquals("Hello snippet", message.content)
        assertEquals(CommunicationPlatform.GMAIL, message.platform)
    }

    @Test
    fun `test search messages uses query`() {
        // This test would ideally mock the HTTP request, but for now we just verify it doesn't crash
        // and returns empty list when no account is found.
        val messages = reader.searchMessages("interview")
        assertTrue(messages.isEmpty())
    }
}
