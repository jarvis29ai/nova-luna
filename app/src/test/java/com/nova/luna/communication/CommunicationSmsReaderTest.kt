package com.nova.luna.communication

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.Telephony
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunicationSmsReaderTest {

    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var contentResolver: ContentResolver
    
    @Mock
    private lateinit var cursor: Cursor

    private lateinit var reader: CommunicationSmsReader

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.contentResolver).thenReturn(contentResolver)
        reader = CommunicationSmsReader(context)
    }

    @Test
    fun `test read sms when permission missing returns empty`() {
        `when`(context.checkPermission(eq(Manifest.permission.READ_SMS), anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED)
        
        val messages = reader.readTodayMessages()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test read sms with empty inbox returns empty`() {
        `when`(context.checkPermission(eq(Manifest.permission.READ_SMS), anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED)
        `when`(contentResolver.query(any(), any(), isNull(), isNull(), any())).thenReturn(cursor)
        `when`(cursor.moveToNext()).thenReturn(false)

        val messages = reader.readTodayMessages()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test read sms with normal messages returns list`() {
        `when`(context.checkPermission(eq(Manifest.permission.READ_SMS), anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED)
        `when`(contentResolver.query(any(), any(), isNull(), isNull(), any())).thenReturn(cursor)
        
        `when`(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)).thenReturn(0)
        `when`(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).thenReturn(1)
        `when`(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)).thenReturn(2)
        `when`(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).thenReturn(3)

        `when`(cursor.moveToNext()).thenReturn(true, true, false) // 2 messages
        `when`(cursor.getString(0)).thenReturn("1", "2")
        `when`(cursor.getString(1)).thenReturn("Alice", "Bob")
        `when`(cursor.getLong(2)).thenReturn(System.currentTimeMillis(), System.currentTimeMillis() - 1000)
        `when`(cursor.getString(3)).thenReturn("Hello Alice", "Hi Bob")

        val messages = reader.readTodayMessages()
        assertEquals(2, messages.size)
        assertEquals("Alice", messages[0].sender.name)
        assertEquals("Hello Alice", messages[0].content)
        assertEquals("Bob", messages[1].sender.name)
    }
}
