package com.nova.luna.communication

import android.content.Context
import com.nova.luna.service.NovaAccessibilityService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
class CommunicationWhatsAppTelegramReaderTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var accessibilityService: NovaAccessibilityService

    private lateinit var whatsappReader: CommunicationWhatsAppReader
    private lateinit var telegramReader: CommunicationTelegramReader

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Inject mock into companion object
        val instanceField: Field = com.nova.luna.service.NovaAccessibilityService::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, accessibilityService)

        whatsappReader = CommunicationWhatsAppReader(context)
        telegramReader = CommunicationTelegramReader(context)
    }

    @Test
    fun `test whatsapp reader maps correctly`() {
        val snapshot = com.nova.luna.service.NovaAccessibilityService.NotificationSnapshot("com.whatsapp", "Hello from Alice", System.currentTimeMillis())
        `when`(accessibilityService.getCapturedNotifications("whatsapp")).thenReturn(listOf(snapshot))

        val messages = whatsappReader.readTodayMessages()
        assertEquals(1, messages.size)
        assertEquals(CommunicationPlatform.WHATSAPP, messages[0].platform)
        assertEquals("Hello from Alice", messages[0].content)
    }

    @Test
    fun `test telegram reader maps correctly`() {
        val snapshot = com.nova.luna.service.NovaAccessibilityService.NotificationSnapshot("org.telegram.messenger", "Message from Bob", System.currentTimeMillis())
        `when`(accessibilityService.getCapturedNotifications("telegram")).thenReturn(listOf(snapshot))

        val messages = telegramReader.readTodayMessages()
        assertEquals(1, messages.size)
        assertEquals(CommunicationPlatform.TELEGRAM, messages[0].platform)
        assertEquals("Message from Bob", messages[0].content)
    }

    @Test
    fun `test empty notification list returns empty`() {
        `when`(accessibilityService.getCapturedNotifications("whatsapp")).thenReturn(emptyList())
        val messages = whatsappReader.readTodayMessages()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test notification access missing returns empty`() {
        // Set instance to null to simulate missing service
        val instanceField: Field = com.nova.luna.service.NovaAccessibilityService::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)

        val messages = whatsappReader.readTodayMessages()
        assertTrue(messages.isEmpty())
    }
}
