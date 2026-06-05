package com.nova.luna.phone

import android.content.Context
import android.provider.CallLog

class PhoneCallLogRepository(private val context: Context) {

    fun searchRecentCalls(name: String? = null): List<PhoneNumberCandidate> {
        val resolver = context.contentResolver
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE
        )
        
        val selection = if (name != null) {
            "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?"
        } else null
        
        val selectionArgs = if (name != null) {
            arrayOf("%$name%", "%$name%")
        } else null

        val candidates = mutableListOf<PhoneNumberCandidate>()
        
        resolver.query(uri, projection, selection, selectionArgs, "${CallLog.Calls.DATE} DESC LIMIT 10")?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)
                candidates.add(PhoneNumberCandidate(number, "Recent call", PhoneContactSearchSource.RECENT_CALL_LOGS))
            }
        }
        
        return candidates.distinctBy { it.number }
    }
}
