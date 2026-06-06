package com.nova.luna.communication

import android.content.Context
import android.content.pm.PackageManager
import com.nova.luna.service.NovaAccessibilityService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.lang.reflect.Field
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunicationSendExecutorTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var accessibilityService: NovaAccessibilityService

    private lateinit var executor: CommunicationSendExecutor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.packageManager).thenReturn(packageManager)
        executor = CommunicationSendExecutor(context)
    }

    @Test
    fun `test sendReply returns blocked when accessibility is missing`() {
        val instanceField: Field = com.nova.luna.service.NovaAccessibilityService::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)

        val draft = ReplyDraft(
            originalMessage = CommunicationMessage("1", CommunicationPlatform.SMS, CommunicationSender("Alice"), java.time.LocalDateTime.now(), ""),
            content = "OK",
            tone = DraftTone.INFORMAL,
            language = "English"
        )
        val status = executor.sendReply(draft)
        assertEquals(CommunicationStatus.BLOCKED, status)
    }

    @Test
    fun `test sendReply returns failed when intent does not resolve`() {
        val instanceField: Field = com.nova.luna.service.NovaAccessibilityService::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, accessibilityService)

        `when`(packageManager.resolveActivity(any(), anyInt())).thenReturn(null)

        val draft = ReplyDraft(
            originalMessage = CommunicationMessage("1", CommunicationPlatform.WHATSAPP, CommunicationSender("Alice"), java.time.LocalDateTime.now(), ""),
            content = "OK",
            tone = DraftTone.INFORMAL,
            language = "English"
        )
        
        val status = executor.sendReply(draft)
        assertEquals(CommunicationStatus.FAILED, status)
    }

    @Test
    fun `test sendEmail returns failed when intent does not resolve`() {
        val instanceField: Field = com.nova.luna.service.NovaAccessibilityService::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, accessibilityService)

        `when`(packageManager.resolveActivity(any(), anyInt())).thenReturn(null)

        val draft = EmailDraft(recipient = "test@example.com", subject = "Test", body = "Body", tone = DraftTone.FORMAL)
        
        val status = executor.sendEmail(draft)
        assertEquals(CommunicationStatus.FAILED, status)
    }
}
