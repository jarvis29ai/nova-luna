package com.nova.luna.communication

class CommunicationSummarizer {

    fun summarizeAll(messages: List<CommunicationMessage>): CommunicationSummary {
        val groupedByPlatform = messages.groupBy { it.platform }
        val platformSummaries = groupedByPlatform.map { (platform, platformMessages) ->
            summarizePlatform(platform, platformMessages)
        }
        
        val importantMessages = messages.filter { 
            it.importance == MessageImportance.CRITICAL || it.importance == MessageImportance.IMPORTANT 
        }

        val overallSummary = if (messages.isEmpty()) {
            "No messages found for today."
        } else {
            "You have ${messages.size} messages today. ${importantMessages.size} look important."
        }

        return CommunicationSummary(platformSummaries, importantMessages, overallSummary)
    }

    fun summarizePlatform(platform: CommunicationPlatform, messages: List<CommunicationMessage>): PlatformSummary {
        val groupedBySender = messages.groupBy { it.sender }
        val senderSummaries = groupedBySender.map { (sender, senderMessages) ->
            SenderSummary(sender, senderMessages.size, summarizeMessagesFromSender(senderMessages))
        }

        return PlatformSummary(
            platform = platform,
            messageCount = messages.size,
            senderSummaries = senderSummaries,
            summaryText = "Found ${messages.size} messages from ${groupedBySender.size} senders on ${platform.name}."
        )
    }

    fun summarizeLongMessage(message: CommunicationMessage): String {
        // In a real implementation, this would use an LLM or summarization algorithm
        return "Summary of long message from ${message.sender.name}: ${message.content.take(100)}..."
    }

    private fun summarizeMessagesFromSender(messages: List<CommunicationMessage>): String {
        return if (messages.size == 1) {
            val msg = messages.first()
            if (msg.isSensitive) "[Sensitive content hidden]" else msg.content.take(50)
        } else {
            val latest = messages.first()
            val content = if (latest.isSensitive) "[Sensitive content hidden]" else latest.content.take(30)
            "${messages.size} messages, latest: $content..."
        }
    }
}
