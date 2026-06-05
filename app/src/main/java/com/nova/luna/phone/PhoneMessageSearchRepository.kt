package com.nova.luna.phone

import android.content.Context
import android.provider.Telephony
import java.util.regex.Pattern

class PhoneMessageSearchRepository(private val context: Context) {

    private val phonePattern = Pattern.compile("(\\+91|0)?[6-9]\\d{9}|\\d{10}|\\d{3}[-\\s]\\d{3}[-\\s]\\d{4}")

    fun searchSms(sender: String? = null): List<MessageNumberCandidate> {
        val resolver = context.contentResolver
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        
        val selection = if (sender != null) {
            "${Telephony.Sms.ADDRESS} LIKE ?"
        } else null
        
        val selectionArgs = if (sender != null) {
            arrayOf("%$sender%")
        } else null

        val candidates = mutableListOf<MessageNumberCandidate>()
        
        resolver.query(uri, projection, selection, selectionArgs, "${Telephony.Sms.DATE} DESC LIMIT 20")?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressIndex)
                val body = cursor.getString(bodyIndex)
                val date = cursor.getLong(dateIndex)
                
                val matcher = phonePattern.matcher(body)
                while (matcher.find()) {
                    val number = matcher.group().filter { it.isDigit() || it == '+' }
                    candidates.add(MessageNumberCandidate(number, address, "SMS", date))
                }
            }
        }
        
        return candidates.distinctBy { it.number }
    }

    // Mock implementation for WhatsApp/Telegram if no direct API is available
    fun searchWhatsApp(sender: String? = null): List<MessageNumberCandidate> {
        // In a real implementation, this might read from notifications or accessibility service
        return emptyList()
    }

    fun searchTelegram(sender: String? = null): List<MessageNumberCandidate> {
        return emptyList()
    }
}
