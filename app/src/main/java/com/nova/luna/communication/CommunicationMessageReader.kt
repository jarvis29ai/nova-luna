package com.nova.luna.communication

import android.content.Context

class CommunicationMessageReader(private val context: Context) {
    private val smsReader = CommunicationSmsReader(context)
    private val gmailReader = CommunicationGmailReader(context)
    private val whatsappReader = CommunicationWhatsAppReader(context)
    private val telegramReader = CommunicationTelegramReader(context)

    fun readMessages(platform: CommunicationPlatform): List<CommunicationMessage> {
        return when (platform) {
            CommunicationPlatform.SMS -> smsReader.readTodayMessages()
            CommunicationPlatform.GMAIL -> gmailReader.readTodayMessages()
            CommunicationPlatform.WHATSAPP -> whatsappReader.readTodayMessages()
            CommunicationPlatform.TELEGRAM -> telegramReader.readTodayMessages()
            CommunicationPlatform.UNKNOWN -> emptyList()
        }
    }

    fun searchMessages(query: String, platform: CommunicationPlatform? = null): List<CommunicationMessage> {
        val platformsToSearch = if (platform != null && platform != CommunicationPlatform.UNKNOWN) {
            listOf(platform)
        } else {
            listOf(CommunicationPlatform.SMS, CommunicationPlatform.GMAIL, CommunicationPlatform.WHATSAPP, CommunicationPlatform.TELEGRAM)
        }

        val results = mutableListOf<CommunicationMessage>()
        for (p in platformsToSearch) {
            results.addAll(when (p) {
                CommunicationPlatform.SMS -> smsReader.searchMessages(query)
                CommunicationPlatform.GMAIL -> gmailReader.searchMessages(query)
                CommunicationPlatform.WHATSAPP -> whatsappReader.searchMessages(query)
                CommunicationPlatform.TELEGRAM -> telegramReader.searchMessages(query)
                else -> emptyList()
            })
        }
        return results
    }
}
