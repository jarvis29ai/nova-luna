package com.nova.luna.communication

import android.content.Context
import com.nova.luna.service.NovaAccessibilityService
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CommunicationTelegramReader(private val context: Context) {
    fun readTodayMessages(): List<CommunicationMessage> {
        return readFromNotifications(null)
    }

    fun searchMessages(query: String): List<CommunicationMessage> {
        return readFromNotifications(query)
    }

    private fun readFromNotifications(searchQuery: String?): List<CommunicationMessage> {
        val service = NovaAccessibilityService.instance ?: return emptyList()
        val snapshots = service.getCapturedNotifications("telegram")

        val messages = mutableListOf<CommunicationMessage>()
        for (snapshot in snapshots) {
            if (searchQuery != null && !snapshot.text.contains(searchQuery, ignoreCase = true)) {
                continue
            }
            
            val timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(snapshot.timestampMillis), ZoneId.systemDefault())

            messages.add(
                CommunicationMessage(
                    id = snapshot.timestampMillis.toString(),
                    platform = CommunicationPlatform.TELEGRAM,
                    sender = CommunicationSender("Telegram User"),
                    timestamp = timestamp,
                    content = snapshot.text
                )
            )
        }
        return messages
    }
}
