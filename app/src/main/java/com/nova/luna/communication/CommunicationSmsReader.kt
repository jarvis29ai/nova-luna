package com.nova.luna.communication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CommunicationSmsReader(private val context: Context) {
    fun readTodayMessages(): List<CommunicationMessage> {
        return readSmsFromInbox(null)
    }

    fun searchMessages(query: String): List<CommunicationMessage> {
        return readSmsFromInbox(query)
    }

    private fun readSmsFromInbox(searchQuery: String?): List<CommunicationMessage> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val messages = mutableListOf<CommunicationMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY
        )

        var selection: String? = null
        var selectionArgs: Array<String>? = null

        if (!searchQuery.isNullOrBlank()) {
            selection = "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
            val arg = "%${searchQuery}%"
            selectionArgs = arrayOf(arg, arg)
        }

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT 25"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx)
                    val address = cursor.getString(addressIdx) ?: "Unknown"
                    val dateMillis = cursor.getLong(dateIdx)
                    val body = cursor.getString(bodyIdx) ?: ""

                    val timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateMillis), ZoneId.systemDefault())

                    messages.add(
                        CommunicationMessage(
                            id = id,
                            platform = CommunicationPlatform.SMS,
                            sender = CommunicationSender(address),
                            timestamp = timestamp,
                            content = body
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Log or ignore safely
        }

        return messages
    }
}
